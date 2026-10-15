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
