package com.meteocompare.app.ui.localitycompare

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.usecase.LocalityHourMetrics
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Chart logic cannot request forecasts: it only projects already-computed hourly metrics. */
class LocalityHourlyChartTest {
    private val hour = LocalityHourMetrics(
        instant = Instant.parse("2026-06-20T14:00:00Z"),
        temperatureC = 20.0,
        precipitationMm = 2.54,
        rainProbabilityPercent = 35,
        cloudCoverPercent = 60,
        windKmh = 16.09344,
        estimatedSunHours = 0.5,
        agreementPercent = 72,
        uvIndex = 5.6,
        modelCount = 3
    )

    @Test fun chartRangeIgnoresMissingAndInvalidValues() {
        assertEquals(ChartRange(0.0, 1.0, false), chartRange(listOf(listOf(null, Double.NaN))))
        val range = chartRange(listOf(listOf(10.0, null, 20.0), listOf(30.0)))
        assertEquals(7.6, range.lower, 0.0001)
        assertEquals(32.4, range.upper, 0.0001)
        assertEquals(true, range.hasData)
    }

    @Test fun chartTapMapsToLocalClockHoursAndClampsEdges() {
        assertEquals(0, chartHourFromX(-200f, 20f, 250f))
        assertEquals(0, chartHourFromX(20f, 20f, 250f))
        assertEquals(12, chartHourFromX(140f, 20f, 250f))
        assertEquals(23, chartHourFromX(250f, 20f, 250f))
        assertEquals(23, chartHourFromX(900f, 20f, 250f))
        assertEquals(0, chartHourFromX(50f, 20f, 20f))
    }

    @Test fun missingHoursRemainGapsInsteadOfInventedZeros() {
        val cities = listOf(HourChartCity("paris", "Paris", List(14) { null } + hour))
        val points = chartPoints(cities, CompareChartMetric.TEMPERATURE, WeatherUnits())
        assertEquals(24, points.single().size)
        assertNull(points.single()[13])
        assertEquals(20.0, points.single()[14]!!, 0.00001)
        assertNull(points.single()[15])
    }

    @Test fun chartUnitsMatchTheUserChoiceIncludingMixedUkProfile() {
        val cities = listOf(HourChartCity("uk", "London", listOf(hour)))
        val mixed = WeatherUnits(UnitSystem.UK_MIXED)
        val imperial = WeatherUnits(UnitSystem.IMPERIAL)
        val tempMixed = chartPoints(cities, CompareChartMetric.TEMPERATURE, mixed).single()[0]
        val tempImperial = chartPoints(cities, CompareChartMetric.TEMPERATURE, imperial).single()[0]
        val windMixed = chartPoints(cities, CompareChartMetric.WIND, mixed).single()[0]
        assertEquals(20.0, tempMixed!!, 0.00001)
        assertEquals(68.0, tempImperial!!, 0.00001)
        assertEquals(10.0, windMixed!!, 0.00001)
        assertEquals(35.0, chartPoints(cities, CompareChartMetric.RAIN_RISK, mixed).single()[0]!!, 0.00001)
    }
}
