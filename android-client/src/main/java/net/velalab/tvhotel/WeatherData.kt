package net.velalab.tvhotel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.ThreadLocalRandom

/** MET Norway Locationforecast is a forecast, not an on-site observation. */
internal data class WeatherReading(
    val temperatureC: Double,
    val symbol: String,
    val forecastAtUtc: Long,
    val fetchedAtUtc: Long,
    val expiresAtUtc: Long,
    val lastModified: String?
)

internal data class WeatherResponse(
    val status: Int,
    val body: String?,
    val expiresAtUtc: Long,
    val lastModified: String?
)

internal interface WeatherSource {
    fun fetch(ifModifiedSince: String?): WeatherResponse
}

internal interface WeatherStore {
    fun load(): WeatherReading?
    fun save(reading: WeatherReading)
}

internal object BangkokClock {
    private val zone = TimeZone.getTimeZone("Asia/Bangkok")
    fun format(epochMillis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = zone }.format(Date(epochMillis))
}

internal object WeatherSymbols {
    fun describe(code: String): Pair<String, String> {
        val base = code.substringBefore("_")
        return when {
            base == "clearsky" -> "☀" to "Clear sky"
            base == "fair" -> "☀" to "Fair"
            base == "partlycloudy" -> "⛅" to "Partly cloudy"
            base == "cloudy" -> "☁" to "Cloudy"
            base.contains("thunder") -> "⚡" to "Thunderstorms"
            base.contains("rain") || base.contains("showers") -> "☂" to "Rain"
            base.contains("fog") -> "≋" to "Fog"
            base.contains("snow") -> "❄" to "Snow"
            else -> "☁" to "Conditions unavailable"
        }
    }
}

internal class MetNorwayWeatherSource : WeatherSource {
    // Ao Nang beach reference point, rounded to 4 decimals for MET Norway caching.
    private val endpoint = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=8.0322&lon=98.8205")

    override fun fetch(ifModifiedSince: String?): WeatherResponse {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "VelaTVHotel/1.0.4 (https://github.com/swansook-cyber/Vela-TV-Hotel/issues)")
            if (!ifModifiedSince.isNullOrBlank()) setRequestProperty("If-Modified-Since", ifModifiedSince)
        }
        return try {
            val status = connection.responseCode
            val body = if (status in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else null
            WeatherResponse(status, body, connection.getHeaderFieldDate("Expires", 0L),
                connection.getHeaderField("Last-Modified"))
        } finally {
            connection.disconnect()
        }
    }
}

internal object WeatherParser {
    fun parse(body: String, now: Long, expires: Long, lastModified: String?): WeatherReading {
        val root = JSONObject(body)
        val units = root.getJSONObject("properties").getJSONObject("meta").getJSONObject("units")
        require(units.getString("air_temperature") == "celsius")
        val series = root.getJSONObject("properties").getJSONArray("timeseries")
        require(series.length() > 0)
        var selected = series.getJSONObject(0)
        for (index in 0 until series.length()) {
            val item = series.getJSONObject(index)
            if (Instant.parse(item.getString("time")).toEpochMilli() <= now) selected = item else break
        }
        val data = selected.getJSONObject("data")
        val degrees = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
        require(degrees.isFinite() && degrees in -90.0..70.0)
        val symbol = listOf("next_1_hours", "next_6_hours", "next_12_hours")
            .firstNotNullOfOrNull { data.optJSONObject(it)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf(String::isNotBlank) }
            ?: "unknown"
        return WeatherReading(degrees, symbol, Instant.parse(selected.getString("time")).toEpochMilli(),
            now, expires, lastModified)
    }
}

/** One owner per Activity. Redraws and lifecycle resumes cannot start duplicate requests. */
internal class WeatherRepository(
    private val source: WeatherSource,
    private val store: WeatherStore,
    private val worker: Executor,
    private val ui: Executor,
    private val now: () -> Long = System::currentTimeMillis,
    private val jitterMillis: Long = ThreadLocalRandom.current().nextLong(0, 5 * 60_000L)
) {
    private val minimumRefreshMillis = 30 * 60 * 1000L
    private val retryMillis = 5 * 60 * 1000L
    @Volatile private var inFlight = false
    @Volatile private var retryAt = 0L
    @Volatile private var reading: WeatherReading? = store.load()

    fun current(): WeatherReading? = reading

    @Synchronized fun refreshIfNeeded(onChange: () -> Unit) {
        val currentTime = now()
        val cached = reading
        if (inFlight || currentTime < retryAt) return
        if (cached != null && currentTime < maxOf(cached.fetchedAtUtc + minimumRefreshMillis + jitterMillis,
                cached.expiresAtUtc)) return
        inFlight = true
        worker.execute {
            try {
                val response = source.fetch(cached?.lastModified)
                val updated = when (response.status) {
                    200 -> WeatherParser.parse(requireNotNull(response.body), now(),
                        response.expiresAtUtc, response.lastModified)
                    304 -> cached?.copy(fetchedAtUtc = now(), expiresAtUtc = response.expiresAtUtc)
                    else -> null
                }
                if (updated != null) {
                    reading = updated
                    runCatching { store.save(updated) }
                    ui.execute(onChange)
                } else retryAt = now() + retryMillis
            } catch (_: Exception) {
                retryAt = now() + retryMillis
                ui.execute(onChange)
            } finally {
                inFlight = false
            }
        }
    }
}
