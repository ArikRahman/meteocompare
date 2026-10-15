package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalityComparisonTest {
    private val day = LocalDate.of(2026, 6, 20)
    private val rise = Instant.parse("2026-06-20T06:00:00Z")
    private val set = Instant.parse("2026-06-20T18:00:00Z")

    @Test fun cloudAdjustedSunshine_usesOnlyOverlappingDaylight() {
        val hours = (0L until 24).map { Instant.parse("2026-06-20T00:00:00Z").plusSeconds(it * 3600) }
        val clouds = hours.mapIndexed { i, _ -> if (i in 6..17) 25 else 100 }
        assertEquals(9.0, estimateCloudAdjustedDaylight(rise, set, hours, clouds), 0.001)
    }

    @Test fun cloudAdjustedSunshine_rejectsIncompleteCloudData() {
        val hours = (6L..8L).map { rise.plusSeconds((it - 6L) * 3600L) }
        assertNull(estimateCloudAdjustedDaylight(rise, set, hours, listOf(0, 0, 0)))
    }

    @Test fun cloudAdjustedSunshine_doesNotCountRepeatedTimestamps() {
        val hours = (0L until 12).map { rise.plusSeconds(it * 3600) }
        assertEquals(6.0, estimateCloudAdjustedDaylight(rise, set, hours + hours, List(24) { 50 }), 0.001)
    }

    @Test fun cloudAdjustedSunshine_invalidSolarWindowReturnsNull() {
        assertNull(estimateCloudAdjustedDaylight(set, rise, listOf(rise), listOf(0)))
    }

    @Test fun dailyMetrics_followExplicitDateAndIgnoreMissingDays() {
        val daily = DailyForecast(
            dates = listOf(day),
            tempMax = listOf(25.0), tempMin = listOf(15.0),
            precipitationSum = listOf(2.0), windSpeedMax = listOf(15.0),
            sunrise = listOf(rise), sunset = listOf(set)
        )
        val hourly = HourlyForecast(
            timestamps = (0L until 12).map { rise.plusSeconds(it * 3600) },
            temperature2m = List(12) { 20.0 }, precipitation = List(12) { 0.0 },
            windSpeed10m = List(12) { 15.0 }, cloudCover = List(12) { 0 }
        )
        val forecast = CityForecast(
            city = City("a", "Example", null, "Example", 48.0, 2.0, "UTC"),
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily),
                WeatherModel.ICON_GLOBAL to ForecastSeries(WeatherModel.ICON_GLOBAL, hourly, daily)
            )
        )
        val calculator = ConfidenceCalculator(EqualWeighting())
        assertNull(buildLocalityDayMetrics(forecast, day.plusDays(1), calculator))
        val result = requireNotNull(buildLocalityDayMetrics(forecast, day, calculator))
        assertEquals(25.0, result.maxC!!, 0.001)
        assertEquals(15.0, result.minC!!, 0.001)
        assertEquals(15.0, result.maxWindKmh!!, 0.001)
        assertEquals(12.0, result.estimatedSunHours!!, 0.001)
        assertEquals(2, result.modelCount)
        assertTrue(result.agreementPercent == null || result.agreementPercent in 0..100)
    }
}
