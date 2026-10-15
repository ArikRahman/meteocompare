package com.meteocompare.app.ui.localitycompare

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalityCompareContentTest {
    @get:Rule val compose = createComposeRule()
    private val cities = listOf(
        City("paris", "Paris", null, "France", 48.85, 2.35, "Europe/Paris"),
        City("lyon", "Lyon", null, "France", 45.75, 4.85, "Europe/Paris"),
        City("lille", "Lille", null, "France", 50.63, 3.05, "Europe/Paris")
    )

    @Test fun selectedCities_renderInParallelEvenWhenAForecastIsUnavailable() {
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(
                        favorites = cities,
                        selectedIds = listOf("paris", "lyon"),
                        forecasts = mapOf(
                            "paris" to CompareForecastState.Failed("offline"),
                            "lyon" to CompareForecastState.Loading
                        )
                    ),
                    calculator = ConfidenceCalculator(EqualWeighting()),
                    onBack = {}, onToggleCity = {}, onRefresh = {},
                    currentDate = LocalDate.of(2026, 10, 14)
                )
            }
        }
        compose.onNodeWithTag("compare_column_paris").assertExists()
        compose.onNodeWithTag("compare_column_lyon").assertExists()
        compose.onNodeWithTag("compare_city_lille").assertExists()
    }

    @Test fun thirdFavoriteCanBeSelected() {
        var lastCity: String? = null
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(favorites = cities, selectedIds = listOf("paris", "lyon")),
                    calculator = ConfidenceCalculator(EqualWeighting()),
                    onBack = {}, onToggleCity = { lastCity = it }, onRefresh = {},
                    currentDate = LocalDate.of(2026, 10, 14)
                )
            }
        }
        compose.onNodeWithTag("compare_city_lille").performClick()
        compose.runOnIdle { assertEquals("lille", lastCity) }
    }

    @Test fun rainChanceAndCloudCover_renderPerCityAndRespectMissingData() {
        val date = LocalDate.of(2026, 10, 14)
        fun ready(city: City, clouds: List<Int?>, probability: Int?): CompareForecastState.Ready {
            val start = date.atStartOfDay(java.time.ZoneId.of(city.timezone!!)).toInstant()
            val hourly = HourlyForecast(
                timestamps = (0L until 24).map { start.plusSeconds(it * 3600) },
                temperature2m = List(24) { 16.0 }, precipitation = List(24) { 0.0 },
                windSpeed10m = List(24) { 10.0 }, cloudCover = clouds
            )
            val daily = DailyForecast(
                dates = listOf(date), tempMax = listOf(20.0), tempMin = listOf(10.0),
                precipitationSum = listOf(3.0), windSpeedMax = listOf(10.0),
                precipitationProbabilityMax = listOf(probability)
            )
            val series = ForecastSeries(WeatherModel.GFS, hourly, daily)
            return CompareForecastState.Ready(CityForecast(city, mapOf(WeatherModel.GFS to series)))
        }
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(
                        favorites = cities,
                        selectedIds = listOf("paris", "lyon"),
                        forecasts = mapOf(
                            "paris" to ready(cities[0], List(24) { 60 }, 70),
                            "lyon" to ready(cities[1], emptyList(), null)
                        )
                    ),
                    calculator = ConfidenceCalculator(EqualWeighting()),
                    onBack = {}, onToggleCity = {}, onRefresh = {}, currentDate = date
                )
            }
        }
        compose.onNodeWithTag("compare_cloud_cover_paris").assertTextEquals("60 %")
        compose.onNodeWithTag("compare_cloud_cover_lyon").assertTextEquals("—")
        compose.onNodeWithTag("compare_rain_probability_paris").assertExists()
        compose.onNodeWithTag("compare_rain_probability_lyon").assertTextEquals("—")
    }
}
