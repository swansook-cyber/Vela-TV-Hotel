package net.velalab.tvhotel

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.Executor

class WeatherRepositoryTest {
    private val start = Instant.parse("2026-10-30T17:15:00Z").toEpochMilli()
    private val body = """{
      "properties":{"meta":{"units":{"air_temperature":"celsius"}},"timeseries":[
        {"time":"2026-10-30T17:00:00Z","data":{"instant":{"details":{"air_temperature":27.6}},"next_1_hours":{"summary":{"symbol_code":"partlycloudy_night"}}}},
        {"time":"2026-10-30T18:00:00Z","data":{"instant":{"details":{"air_temperature":26.0}},"next_1_hours":{"summary":{"symbol_code":"cloudy"}}}}
      ]}}
    }""".trimIndent()

    private class MemoryStore(var item: WeatherReading? = null) : WeatherStore {
        override fun load() = item
        override fun save(reading: WeatherReading) { item = reading }
    }

    @Test fun successParsesCurrentForecastAndPersistsCache() {
        val store = MemoryStore()
        var calls = 0
        val source = object : WeatherSource {
            override fun fetch(ifModifiedSince: String?): WeatherResponse {
                calls++
                assertNull(ifModifiedSince)
                return WeatherResponse(200, body, start + 35 * 60_000L, "Fri, 30 Oct 2026 17:00:00 GMT")
            }
        }
        val repository = WeatherRepository(source, store, Executor { it.run() }, Executor { it.run() }, { start }, 0)
        var changes = 0
        repository.refreshIfNeeded { changes++ }
        assertEquals(1, calls)
        assertEquals(1, changes)
        assertEquals(27.6, repository.current()!!.temperatureC, 0.01)
        assertEquals("partlycloudy_night", store.item!!.symbol)
        repository.refreshIfNeeded { changes++ }
        assertEquals(1, calls)
    }

    @Test fun duplicateRefreshWhilePendingStartsOnlyOneRequest() {
        val tasks = mutableListOf<Runnable>()
        var calls = 0
        val source = object : WeatherSource {
            override fun fetch(ifModifiedSince: String?): WeatherResponse {
                calls++
                return WeatherResponse(200, body, start + 30 * 60_000L, null)
            }
        }
        val repository = WeatherRepository(source, MemoryStore(), Executor { tasks.add(it) },
            Executor { it.run() }, { start }, 0)
        repository.refreshIfNeeded { }
        repository.refreshIfNeeded { }
        assertEquals(1, tasks.size)
        tasks.single().run()
        assertEquals(1, calls)
    }

    @Test fun timeoutRetainsLastSuccessAndThrottlesRetry() {
        var currentTime = start
        var calls = 0
        val store = MemoryStore()
        val source = object : WeatherSource {
            override fun fetch(ifModifiedSince: String?): WeatherResponse {
                calls++
                if (calls == 1) return WeatherResponse(200, body, start + 30 * 60_000L, "modified")
                throw SocketTimeoutException("simulated timeout")
            }
        }
        val repository = WeatherRepository(source, store, Executor { it.run() },
            Executor { it.run() }, { currentTime }, 0)
        repository.refreshIfNeeded { }
        currentTime += 31 * 60_000L
        repository.refreshIfNeeded { }
        repository.refreshIfNeeded { }
        assertEquals(2, calls)
        assertEquals(27.6, repository.current()!!.temperatureC, 0.01)
        assertEquals(start, store.item!!.fetchedAtUtc)
    }

    @Test fun httpErrorWithoutCacheLeavesWeatherUnavailable() {
        val repository = WeatherRepository(object : WeatherSource {
            override fun fetch(ifModifiedSince: String?) = WeatherResponse(503, null, 0, null)
        }, MemoryStore(), Executor { it.run() }, Executor { it.run() }, { start }, 0)
        repository.refreshIfNeeded { }
        assertNull(repository.current())
    }

    @Test fun notModifiedHonorsExpiresAndUpdatesLastCheckedTime() {
        var currentTime = start
        val cached = WeatherReading(28.0, "fair_day", start, start - 31 * 60_000L,
            start - 1L, "prior")
        val store = MemoryStore(cached)
        val repository = WeatherRepository(object : WeatherSource {
            override fun fetch(ifModifiedSince: String?): WeatherResponse {
                assertEquals("prior", ifModifiedSince)
                return WeatherResponse(304, null, start + 40 * 60_000L, null)
            }
        }, store, Executor { it.run() }, Executor { it.run() }, { currentTime }, 0)
        repository.refreshIfNeeded { }
        assertEquals(start, store.item!!.fetchedAtUtc)
        assertEquals(start + 40 * 60_000L, store.item!!.expiresAtUtc)
        assertEquals("prior", store.item!!.lastModified)
    }

    @Test fun bangkokDateAndSymbolAreStable() {
        assertEquals("Sat, 31 Oct 2026 00:15", BangkokClock.format(start, "EEE, d MMM yyyy HH:mm"))
        assertEquals("Partly cloudy", WeatherSymbols.describe("partlycloudy_night").second)
    }
}
