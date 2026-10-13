package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastDayNightTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val date = LocalDate.of(2026, 6, 21)
    private val city = City(
        id = "paris", name = "Paris", country = "France",
        latitude = 48.8566, longitude = 2.3522, timezone = "Europe/Paris"
    )

    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant()

    @Test fun `clear night and midday use local solar hours`() {
        assertEquals(true, isNightAtCoordinates(city.latitude, city.longitude, at(2), zone))
        assertEquals(false, isNightAtCoordinates(city.latitude, city.longitude, at(12), zone))
        assertEquals(true, isNightAtCoordinates(city.latitude, city.longitude, at(23), zone))
    }

    @Test fun `API sunrise and sunset have priority and sunset itself is night`() {
        val sunrise = at(7)
        val sunset = at(20)
        val daily = DailyForecast(
            dates = listOf(date), tempMin = listOf(12.0), tempMax = listOf(24.0),
            precipitationSum = listOf(0.0), windSpeedMax = listOf(10.0),
            sunrise = listOf(sunrise), sunset = listOf(sunset)
        )
        val forecast = CityForecast(city, mapOf(WeatherModel.GFS to ForecastSeries(
            WeatherModel.GFS,
            HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()), daily
        )))
        assertEquals(
            listOf(true, false, false, true),
            forecastNightFlags(forecast, listOf(at(6), at(7), at(19, 59), at(20)))
        )
    }

    @Test fun `cross midnight remains based on the next local date`() {
        val forecast = CityForecast(city, emptyMap())
        assertEquals(
            listOf(true, true, false),
            forecastNightFlags(forecast, listOf(
                at(23), date.plusDays(1).atTime(2, 0).atZone(zone).toInstant(),
                date.plusDays(1).atTime(12, 0).atZone(zone).toInstant()
            ))
        )
    }

    @Test fun `invalid coordinates do not invent night state`() {
        assertNull(isNightAtCoordinates(Double.NaN, 0.0, Instant.now(), zone))
    }
}
