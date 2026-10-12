package com.meteocompare.app.ui.citydetail

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetailSolarCardLogicTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val date = LocalDate.of(2026, 6, 21)
    private val city = City(
        id = "paris", name = "Paris", country = "France",
        latitude = 48.8566, longitude = 2.3522, timezone = "Europe/Paris"
    )

    private fun forecast(sunrise: Instant?, sunset: Instant?): CityForecast {
        val daily = DailyForecast(
            dates = listOf(date),
            tempMax = listOf(24.0),
            tempMin = listOf(12.0),
            precipitationSum = listOf(0.0),
            windSpeedMax = listOf(10.0),
            sunrise = listOf(sunrise),
            sunset = listOf(sunset)
        )
        return CityForecast(city, mapOf(
            WeatherModel.GFS to ForecastSeries(
                WeatherModel.GFS,
                HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()),
                daily
            )
        ))
    }

    @Test fun prefers_api_times() {
        val sunrise = date.atTime(6, 32).atZone(zone).toInstant()
        val sunset = date.atTime(21, 49).atZone(zone).toInstant()
        assertEquals(DetailSolarWindow(sunrise, sunset), detailSolarWindow(forecast(sunrise, sunset), date))
    }

    @Test fun missing_api_times_fall_back_to_local_astronomy() {
        val result = detailSolarWindow(forecast(null, null), date)
        assertNotNull(result.sunrise)
        assertNotNull(result.sunset)
        val localHour = result.sunrise!!.atZone(zone).hour
        assertEquals(true, localHour in 4..8)
    }

    @Test fun progress_is_only_available_during_daylight() {
        val sunrise = Instant.parse("2026-06-21T04:00:00Z")
        val sunset = Instant.parse("2026-06-21T20:00:00Z")
        val window = DetailSolarWindow(sunrise, sunset)
        assertNull(solarProgress(window, sunrise.minusSeconds(1)))
        assertEquals(0.5f, solarProgress(window, sunrise.plusSeconds(8 * 3600))!!, .0001f)
        assertNull(solarProgress(window, sunset.plusSeconds(1)))
        assertNull(solarProgress(DetailSolarWindow(null, null), sunrise))
    }
}
