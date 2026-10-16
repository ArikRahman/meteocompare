package com.meteocompare.app.ui.localitycompare

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import java.time.LocalDate
import java.time.ZoneId

private val previewDay = LocalDate.of(2026, 10, 14)
private val previewCities = listOf(
    City("paris", "Paris", null, "France", 48.85, 2.35, "Europe/Paris"),
    City("lyon", "Lyon", null, "France", 45.76, 4.84, "Europe/Paris")
)

private fun previewForecast(city: City, high: Double, clouds: Int): CityForecast {
    val start = previewDay.atStartOfDay(ZoneId.of("Europe/Paris")).toInstant()
    val hourly = HourlyForecast(
        timestamps = (0L until 24).map { start.plusSeconds(it * 3600) },
        temperature2m = List(24) { high - 3 },
        precipitation = List(24) { 0.1 },
        precipitationProbability = List(24) { 35 },
        windSpeed10m = List(24) { 12.0 },
        cloudCover = List(24) { clouds }
    )
    val daily = DailyForecast(
        dates = listOf(previewDay), tempMin = listOf(high - 8),
        tempMax = listOf(high), precipitationSum = listOf(2.4),
        precipitationProbabilityMax = listOf(35), windSpeedMax = listOf(19.0),
        sunrise = listOf(start.plusSeconds(7 * 3600)),
        sunset = listOf(start.plusSeconds(18 * 3600))
    )
    return CityForecast(city, mapOf(WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily)))
}

@Preview(name = "Localities · visual metrics · light", showBackground = true, widthDp = 430)
@Preview(name = "Localities · visual metrics · tablet", showBackground = true, widthDp = 800)
@Composable
private fun LocalityCompareMetricsPreview() {
    val forecasts = mapOf(
        "paris" to CompareForecastState.Ready(previewForecast(previewCities[0], 23.0, 65)),
        "lyon" to CompareForecastState.Ready(previewForecast(previewCities[1], 27.0, 20))
    )
    MaterialTheme {
        LocalityCompareContent(
            state = LocalityCompareState(
                favorites = previewCities, selectedIds = previewCities.map { it.id },
                forecasts = forecasts,
                uvForecasts = mapOf(
                    "paris" to UvForecast(emptyMap(), mapOf(previewDay to 5.0)),
                    "lyon" to UvForecast(emptyMap(), mapOf(previewDay to 8.0))
                )
            ),
            calculator = ConfidenceCalculator(EqualWeighting()),
            onBack = {}, onToggleCity = {}, onRefresh = {},
            currentDate = previewDay
        )
    }
}

@Preview(name = "Localities · hourly chart · phone", showBackground = true, widthDp = 400)
@Preview(name = "Localities · hourly chart · tablet", showBackground = true, widthDp = 800)
@Composable
private fun LocalityHourlyChartPreview() {
    val entries = previewCities.mapIndexed { index, city ->
        HourChartCity(city.id, city.name, (0..23).map { hour ->
            com.meteocompare.app.domain.usecase.LocalityHourMetrics(
                instant = previewDay.atTime(hour, 0).atZone(ZoneId.of(city.timezone ?: "UTC")).toInstant(),
                temperatureC = 13.0 + (hour * (24 - hour) / 24.0) + index * 3.0,
                precipitationMm = if (hour in 14..18) 0.6 else 0.0,
                rainProbabilityPercent = if (hour in 14..18) 60 else 20,
                cloudCoverPercent = (30 + hour + index * 12).coerceIn(0, 100),
                windKmh = 10.0 + index * 4,
                estimatedSunHours = if (hour in 8..18) 0.7 else 0.0,
                agreementPercent = 78 - index * 12,
                uvIndex = if (hour in 8..18) 4.0 else 0.0,
                modelCount = 2
            )
        })
    }
    MaterialTheme {
        LocalityHourlyChart(
            cities = entries,
            units = com.meteocompare.app.core.units.WeatherUnits(),
            locale = java.util.Locale.FRANCE,
            hourFormatter = java.time.format.DateTimeFormatter.ofPattern("HH'h'", java.util.Locale.FRANCE),
            selectedHour = 14,
            onHourSelected = {}
        )
    }
}


@Preview(name = "Localities · unified overview", showBackground = true, widthDp = 375)
@Composable
private fun LocalityComparisonOverviewPreview() {
    val first = com.meteocompare.app.domain.usecase.LocalityDayMetrics(
        date = previewDay, minC = 14.0, maxC = 22.0, precipitationMm = 4.2,
        rainProbabilityPercent = 65, cloudCoverPercent = 72, maxWindKmh = 24.0,
        estimatedSunHours = 3.2, agreementPercent = 79, modelCount = 4, uvMax = 5.1
    )
    val second = first.copy(maxC = 26.0, precipitationMm = 0.4,
        rainProbabilityPercent = 12, cloudCoverPercent = 20, uvMax = 7.0)
    MaterialTheme {
        androidx.compose.foundation.layout.Column {
            LocalityComparisonOverview(
                cities = previewCities,
                days = mapOf("paris" to first, "lyon" to second),
                forecasts = emptyMap(),
                units = com.meteocompare.app.core.units.WeatherUnits(),
                locale = java.util.Locale.FRANCE,
                selectedMetric = CompareChartMetric.TEMPERATURE,
                onMetricSelected = {}
            )
            LocalityHourDetails(
                cities = previewCities.map { HourChartCity(it.id, it.name, List(24) { null }) },
                selectedHour = 14, hourLabel = "14h", expanded = false,
                onToggle = {}, units = com.meteocompare.app.core.units.WeatherUnits(),
                locale = java.util.Locale.FRANCE
            )
            LocalityComparisonExplanations(expanded = false, onToggle = {})
        }
    }
}
