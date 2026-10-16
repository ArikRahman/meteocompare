package com.meteocompare.app.ui.localitycompare

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalityMetricsMemoTest {
    private val date = LocalDate.of(2026, 6, 20)
    private val instant = Instant.parse("2026-06-20T14:00:00Z")
    private val city = City("memo", "Memo", null, "France", 48.0, 2.0, "UTC")
    private val daily = DailyForecast(
        dates = listOf(date), tempMax = listOf(26.0), tempMin = listOf(17.0),
        precipitationSum = listOf(1.0), windSpeedMax = listOf(14.0)
    )
    private val hourly = HourlyForecast(
        timestamps = listOf(instant), temperature2m = listOf(22.0),
        precipitation = listOf(1.0), windSpeed10m = listOf(14.0), cloudCover = listOf(10)
    )
    private fun forecast() = CityForecast(city,
        mapOf(WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily)))
    private fun memo(capacity: Int = 72) = LocalityMetricsMemo(
        ConfidenceCalculator(EqualWeighting()), capacity
    )

    @Test fun sameForecastAndSelectionReturnsIdenticalMetricsWithoutRecalculation() {
        val cached = memo()
        val source = forecast()
        val uv = UvForecast(mapOf(instant to 6.0), mapOf(date to 8.0))
        val originalDay = requireNotNull(cached.day(source, date, uv))
        val originalHour = requireNotNull(cached.hour(source, date, 14, uv))
        assertSame(originalDay, cached.day(source, date, uv))
        assertSame(originalHour, cached.hour(source, date, 14, uv))
        assertEquals(2, cached.size())
    }

    @Test fun newForecastOrUvInvalidatesTheOldValues() {
        val cached = memo()
        val source = forecast()
        val uv = UvForecast(mapOf(instant to 6.0), mapOf(date to 8.0))
        val first = requireNotNull(cached.day(source, date, uv))
        val nextForecast = forecast()
        val second = requireNotNull(cached.day(nextForecast, date, uv))
        assertNotSame(first, second)
        assertEquals(1, cached.size())
        val nextUv = UvForecast(mapOf(instant to 6.0), mapOf(date to 9.0))
        val third = requireNotNull(cached.day(nextForecast, date, nextUv))
        assertEquals(9.0, third.uvMax!!, 0.001)
        assertNotSame(second, third)
    }

    @Test fun aNewForecastRevisionReleasesOldCityEntriesInsteadOfRetainingLargeGraphs() {
        val cached = memo(capacity = 100)
        val previous = forecast()
        cached.day(previous, date, null)
        cached.hour(previous, date, 13, null)
        cached.hour(previous, date, 14, null)
        assertEquals(3, cached.size())
        cached.day(forecast(), date, null)
        assertEquals(1, cached.size())
    }

    @Test fun aUvOnlyChangeInvalidatesMetricsButKeepsTheForecastResultsCorrect() {
        val cached = memo()
        val source = forecast()
        val before = UvForecast(mapOf(instant to 6.0), mapOf(date to 7.0))
        val after = UvForecast(mapOf(instant to 8.0), mapOf(date to 9.0))
        cached.day(source, date, before)
        cached.hour(source, date, 14, before)
        val updated = cached.day(source, date, after)
        assertEquals(1, cached.size())
        assertNotNull(updated)
        assertEquals(9.0, updated!!.uvMax!!, 0.001)
    }

    @Test fun removingCitiesReleasesTheirMetricsAndSolarWindows() {
        val cached = memo()
        val old = forecast()
        cached.day(old, date, null)
        cached.hour(old, date, 14, null)
        cached.retainCities(emptySet())
        assertEquals(0, cached.size())
        val next = cached.day(old, date, null)
        assertNotNull(next)
        assertEquals(1, cached.size())
    }

    @Test fun changingOnlyTheSelectedDateOrHourUsesABoundedLru() {
        val cached = memo(capacity = 3)
        val source = forecast()
        cached.hour(source, date, 13, null)
        cached.hour(source, date, 14, null)
        cached.hour(source, date, 15, null)
        val retained = cached.hour(source, date, 14, null)
        cached.hour(source, date, 16, null)
        assertEquals(3, cached.size())
        assertSame(retained, cached.hour(source, date, 14, null))
        // Missing hours are also cached (as null) instead of repeatedly scanned.
        assertTrue(cached.hour(source, date, 13, null) == null)
        assertEquals(3, cached.size())
    }
}
