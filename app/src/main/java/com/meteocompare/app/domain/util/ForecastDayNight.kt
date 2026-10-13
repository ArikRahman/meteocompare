package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Day/night based on the forecast city's location and actual local solar times, not phone theme. */
fun isNightAtCoordinates(
    latitude: Double,
    longitude: Double,
    instant: Instant,
    zone: ZoneId
): Boolean? {
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0) return null
    val date = instant.atZone(zone).toLocalDate()
    val times = SolarTimes.compute(latitude, longitude, date, zone)
    val sunrise = times.sunrise?.atDate(date)?.atZone(zone)?.toInstant() ?: return null
    val sunset = times.sunset?.atDate(date)?.atZone(zone)?.toInstant() ?: return null
    if (!sunset.isAfter(sunrise)) return null
    return instant.isBefore(sunrise) || !instant.isBefore(sunset)
}

/** Prefer provider sunrise/sunset per local date and fall back to NOAA, including after midnight. */
fun forecastNightFlags(forecast: CityForecast, instants: List<Instant>): List<Boolean?> {
    val zone = try {
        ZoneId.of(forecast.city.timezone ?: "UTC")
    } catch (_: Exception) {
        ZoneId.of("UTC")
    }
    val windows = mutableMapOf<LocalDate, Pair<Instant?, Instant?>>()
    return instants.map { instant ->
        val date = instant.atZone(zone).toLocalDate()
        val (sunrise, sunset) = windows.getOrPut(date) {
            val apiSunrise = forecast.seriesByModel.values.asSequence().mapNotNull { series ->
                val i = series.daily.dates.indexOf(date)
                if (i < 0) null else series.daily.sunrise.getOrNull(i)
            }.firstOrNull()
            val apiSunset = forecast.seriesByModel.values.asSequence().mapNotNull { series ->
                val i = series.daily.dates.indexOf(date)
                if (i < 0) null else series.daily.sunset.getOrNull(i)
            }.firstOrNull()
            val calculated = if (apiSunrise == null || apiSunset == null) {
                forecast.city.takeIf { city ->
                    city.latitude.isFinite() && city.latitude in -90.0..90.0 &&
                        city.longitude.isFinite() && city.longitude in -180.0..180.0
                }?.let { city -> SolarTimes.compute(city.latitude, city.longitude, date, zone) }
            } else null
            Pair(
                apiSunrise ?: calculated?.sunrise?.atDate(date)?.atZone(zone)?.toInstant(),
                apiSunset ?: calculated?.sunset?.atDate(date)?.atZone(zone)?.toInstant()
            )
        }
        if (sunrise == null || sunset == null || !sunset.isAfter(sunrise)) null
        else instant.isBefore(sunrise) || !instant.isBefore(sunset)
    }
}

fun forecastIsNightAt(forecast: CityForecast, instant: Instant): Boolean? =
    forecastNightFlags(forecast, listOf(instant)).first()
