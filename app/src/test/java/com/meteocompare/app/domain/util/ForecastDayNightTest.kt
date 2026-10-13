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

    @Test fun `a complete API pair from one engine wins over partial data from another`() {
        val first = DailyForecast(
            dates = listOf(date), tempMin = listOf(12.0), tempMax = listOf(24.0),
            precipitationSum = listOf(0.0), windSpeedMax = listOf(10.0),
            sunrise = listOf(at(5)), sunset = listOf(null)
        )
        val second = first.copy(sunrise = listOf(at(7)), sunset = listOf(at(20)))
        val forecast = CityForecast(city, linkedMapOf(
            WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, emptyHourly(), first),
            WeatherModel.ICON_GLOBAL to ForecastSeries(WeatherModel.ICON_GLOBAL, emptyHourly(), second)
        ))
        assertEquals(ForecastSolarWindow(at(7), at(20)), forecastSolarWindow(forecast, date))
        assertEquals(listOf(true, false, true), forecastNightFlags(forecast, listOf(at(6), at(7), at(20))))
    }

    @Test fun `invalid provider interval falls back instead of inverting night`() {
        val daily = DailyForecast(
            dates = listOf(date), tempMin = listOf(12.0), tempMax = listOf(24.0),
            precipitationSum = listOf(0.0), windSpeedMax = listOf(10.0),
            sunrise = listOf(at(22)), sunset = listOf(at(5))
        )
        val forecast = CityForecast(city, mapOf(
            WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, emptyHourly(), daily)
        ))
        val window = forecastSolarWindow(forecast, date)
        assertEquals(true, window.isValid)
        assertEquals(false, nightAt(window, at(12)))
        assertEquals(true, nightAt(window, at(2)))
    }

    @Test fun `DST switch uses instant not wall clock labels`() {
        val dstDate = LocalDate.of(2026, 3, 29)
        val sunrise = dstDate.atTime(7, 30).atZone(zone).toInstant()
        val sunset = dstDate.atTime(20, 30).atZone(zone).toInstant()
        val window = ForecastSolarWindow(sunrise, sunset)
        assertEquals(true, nightAt(window, sunrise.minusSeconds(1)))
        assertEquals(false, nightAt(window, sunrise))
        assertEquals(false, nightAt(window, sunset.minusSeconds(1)))
        assertEquals(true, nightAt(window, sunset))
    }

    @Test fun `invalid coordinates and polar day remain unknown without API`() {
        val invalid = city.copy(latitude = Double.NaN)
        assertNull(forecastIsNightAt(CityForecast(invalid, emptyMap()), at(2)))
        val polarCity = city.copy(latitude = 80.0, longitude = 16.0, timezone = "Arctic/Longyearbyen")
        val summer = LocalDate.of(2026, 6, 21)
        val noon = summer.atTime(12, 0).atZone(ZoneId.of("Arctic/Longyearbyen")).toInstant()
        assertNull(forecastIsNightAt(CityForecast(polarCity, emptyMap()), noon))
    }

    private fun emptyHourly() = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList())
}
