package net.velalab.tvhotel

import android.content.Context
import org.json.JSONObject

internal class AndroidWeatherStore(context: Context) : WeatherStore {
    private val preferences = context.getSharedPreferences("weather_v1", Context.MODE_PRIVATE)

    override fun load(): WeatherReading? = runCatching {
        val raw = preferences.getString("reading", null) ?: return null
        val value = JSONObject(raw)
        WeatherReading(
            value.getDouble("temperature_c"), value.getString("symbol"),
            value.getLong("forecast_at"), value.getLong("fetched_at"),
            value.getLong("expires_at"), value.optString("last_modified").ifBlank { null }
        )
    }.getOrNull()

    override fun save(reading: WeatherReading) {
        val value = JSONObject()
            .put("temperature_c", reading.temperatureC)
            .put("symbol", reading.symbol)
            .put("forecast_at", reading.forecastAtUtc)
            .put("fetched_at", reading.fetchedAtUtc)
            .put("expires_at", reading.expiresAtUtc)
            .put("last_modified", reading.lastModified ?: "")
        check(preferences.edit().putString("reading", value.toString()).commit())
    }
}
