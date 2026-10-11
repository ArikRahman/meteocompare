package com.meteocompare.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.UvDailyDto
import com.meteocompare.app.data.remote.dto.UvForecastDto
import com.meteocompare.app.data.remote.dto.UvHourlyDto
import com.meteocompare.app.domain.model.City
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UvIndexRepositoryTest {
    private val city = City("paris", "Paris", country = "France", latitude = 48.85,
        longitude = 2.35, timezone = "Europe/Paris")
    private val response = UvForecastDto(
        timezone = "Europe/Paris",
        hourly = UvHourlyDto(listOf("2026-10-11T13:00"), listOf(5.4)),
        daily = UvDailyDto(listOf("2026-10-11"), listOf(6.7))
    )
    private val api = mockk<OpenMeteoApi>()
    private var isOnline = true
    private val network = mockk<NetworkMonitor> {
        every { isOnline() } answers { this@UvIndexRepositoryTest.isOnline }
    }
    private var clockMs = Instant.parse("2026-10-11T08:00:00Z").toEpochMilli()
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(clockMs)
    }
    private val stored = mutableMapOf<String, String>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true) {
        every { putString(any(), any()) } answers {
            stored[firstArg()] = secondArg()
            mockk(relaxed = true)
        }
    }
    private val prefs = mockk<SharedPreferences> {
        every { getString(any(), any()) } answers { stored[firstArg()] }
        every { edit() } returns editor
    }
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns prefs
    }

    private fun repository() = UvIndexRepositoryImpl(
        api, Json { ignoreUnknownKeys = true }, clock, network, context,
        UnconfinedTestDispatcher()
    )

    @Test fun preferences_are_initialized_only_on_first_lookup() = runTest {
        val repository = repository()
        verify(exactly = 0) { context.getSharedPreferences(any(), any()) }

        // Offline lookup is enough to initialize the cache, without any HTTP request.
        isOnline = false
        assertNull(repository.get(city))
        verify(exactly = 1) {
            context.getSharedPreferences("uv_forecast_cache_v1", Context.MODE_PRIVATE)
        }
        assertNull(repository.get(city))
        verify(exactly = 1) {
            context.getSharedPreferences("uv_forecast_cache_v1", Context.MODE_PRIVATE)
        }
    }

    @Test fun concurrent_city_views_share_one_http_request_and_fresh_cache_is_reused() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { api.getUvForecast(any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            response
        }
        val repository = repository()
        val detail = async { repository.get(city) }
        val home = async { repository.get(city.copy(id = "same-coordinates")) }
        runCurrent()
        coVerify(exactly = 1) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
        gate.complete(Unit)
        assertEquals(5.4, detail.await()?.hourly?.values?.first())
        assertEquals(6.7, home.await()?.dailyMaximum?.values?.first())
        repository.get(city)
        coVerify(exactly = 1) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun uv_cache_has_its_own_ttl_and_is_readable_offline() = runTest {
        coEvery { api.getUvForecast(any(), any(), any(), any(), any(), any()) } returns response
        val repository = repository()
        assertEquals(5.4, repository.get(city)?.hourly?.values?.first())
        clockMs += UV_CACHE_TTL_MS - 1
        repository.get(city)
        coVerify(exactly = 1) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
        clockMs += 2
        repository.get(city)
        coVerify(exactly = 2) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
        isOnline = false
        clockMs += UV_CACHE_TTL_MS
        assertEquals(5.4, repository.get(city)?.hourly?.values?.first())
        coVerify(exactly = 2) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun error_is_temporarily_backed_off_instead_of_refetched_for_every_screen() = runTest {
        coEvery { api.getUvForecast(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("offline")
        val repository = repository()
        assertNull(repository.get(city))
        assertNull(repository.get(city))
        coVerify(exactly = 1) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
        clockMs += UV_FAILURE_BACKOFF_MS + 1
        assertNull(repository.get(city))
        coVerify(exactly = 2) { api.getUvForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun timestamp_alignment_and_invalid_values_are_safe() {
        val mapped = response.toDomainOrNull()!!
        assertEquals(5.4, mapped.at(Instant.parse("2026-10-11T11:37:00Z")))
        assertEquals(6.7, mapped.maxOn(java.time.LocalDate.parse("2026-10-11")))
        assertNull(mapped.at(Instant.parse("2026-10-11T10:00:00Z")))
        assertTrue(!isUvCacheFresh(0, UV_CACHE_TTL_MS))
        assertTrue(isUvCacheFresh(1000, 1001))
        assertNull(UvForecastDto("Europe/Paris", UvHourlyDto(listOf("2026-10-11T13:00"), listOf(-1.0))).toDomainOrNull())
    }
}
