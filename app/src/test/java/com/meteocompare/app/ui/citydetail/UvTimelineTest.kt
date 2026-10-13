package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.UvForecast
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvTimelineTest {
    @Test fun hourly_and_daily_uv_are_aligned_without_interpolation() {
        val hour = Instant.parse("2026-10-11T11:00:00Z")
        val date = LocalDate.parse("2026-10-11")
        val uv = UvForecast(hourly = mapOf(hour to 4.6), dailyMaximum = mapOf(date to 7.2))
        val result = listOf(
            SimplifiedTimelinePoint(instant = hour),
            SimplifiedTimelinePoint(instant = hour.plusSeconds(3600)),
            SimplifiedTimelinePoint(date = date)
        ).withUvIndex(uv)
        assertEquals(4.6, result[0].uvIndex)
        assertNull(result[1].uvIndex)
        assertEquals(7.2, result[2].uvIndex)
        assertEquals(3, result.size)
    }

    @Test fun repeated_local_hour_at_fall_dst_keeps_distinct_utc_uv_measurements() {
        val first = Instant.parse("2026-10-25T00:00:00Z")
        val second = Instant.parse("2026-10-25T01:00:00Z")
        // Les deux heures valent 02h locales à Paris, mais les instants sont distincts.
        val forecast = UvForecast(mapOf(first to 1.2, second to 2.8), emptyMap())
        val result = listOf(
            SimplifiedTimelinePoint(instant = first),
            SimplifiedTimelinePoint(instant = second)
        ).withUvIndex(forecast)
        assertEquals(1.2, result[0].uvIndex)
        assertEquals(2.8, result[1].uvIndex)
    }
}
