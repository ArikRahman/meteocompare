package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Période solaire en instants absolus (ne dépend pas de l'heure du téléphone). */
data class ForecastSolarWindow(val sunrise: Instant?, val sunset: Instant?) {
    val isValid: Boolean get() = sunrise != null && sunset != null && sunset.isAfter(sunrise)
}

/**
 * Source unique pour les écrans, les chronologies et les widgets :
 * une paire API valide provenant d'un MÊME moteur est prioritaire.
 * Les réponses partielles sont complétées par NOAA uniquement si la paire
 * résultante est chronologiquement cohérente ; sinon, fallback NOAA complet.
 * En cas de jour/nuit polaire ou de coordonnées invalides, ne rien inventer.
 */
fun forecastSolarWindow(forecast: CityForecast, date: LocalDate): ForecastSolarWindow {
    val zone = citySolarZone(forecast)
    val apiPairs = forecast.seriesByModel.values.mapNotNull { series ->
        val index = series.daily.dates.indexOf(date)
        if (index < 0) null else ForecastSolarWindow(
            series.daily.sunrise.getOrNull(index), series.daily.sunset.getOrNull(index)
        )
    }
    apiPairs.firstOrNull(ForecastSolarWindow::isValid)?.let { return it }

    val fallback = calculatedSolarWindow(
        forecast.city.latitude, forecast.city.longitude, date, zone
    )
    val apiSunrise = apiPairs.firstNotNullOfOrNull(ForecastSolarWindow::sunrise)
    val apiSunset = apiPairs.firstNotNullOfOrNull(ForecastSolarWindow::sunset)
    val completed = ForecastSolarWindow(apiSunrise ?: fallback.sunrise, apiSunset ?: fallback.sunset)
    return if (completed.isValid) completed else fallback
}

/** Retourne null quand la géolocalisation ou le soleil ne permettent pas de trancher. */
fun nightAt(window: ForecastSolarWindow, instant: Instant): Boolean? {
    if (!window.isValid) return null
    return instant.isBefore(requireNotNull(window.sunrise)) ||
        !instant.isBefore(requireNotNull(window.sunset))
}

private fun citySolarZone(forecast: CityForecast): ZoneId = try {
    ZoneId.of(forecast.city.timezone ?: "UTC")
} catch (_: Exception) {
    ZoneId.of("UTC")
}

private fun calculatedSolarWindow(
    latitude: Double, longitude: Double, date: LocalDate, zone: ZoneId
): ForecastSolarWindow {
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0) {
        return ForecastSolarWindow(null, null)
    }
    val times = SolarTimes.compute(latitude, longitude, date, zone)
    return ForecastSolarWindow(
        times.sunrise?.atDate(date)?.atZone(zone)?.toInstant(),
        times.sunset?.atDate(date)?.atZone(zone)?.toInstant()
    )
}

/** Day/night based on the forecast city's location, never the phone theme. */
fun isNightAtCoordinates(
    latitude: Double,
    longitude: Double,
    instant: Instant,
    zone: ZoneId
): Boolean? {
    val date = instant.atZone(zone).toLocalDate()
    return nightAt(calculatedSolarWindow(latitude, longitude, date, zone), instant)
}

/** Pré-calcul des jours : pas de recalcul astronomique pour chaque heure. */
fun forecastNightFlags(forecast: CityForecast, instants: List<Instant>): List<Boolean?> {
    val zone = citySolarZone(forecast)
    val windows = mutableMapOf<LocalDate, ForecastSolarWindow>()
    return instants.map { instant ->
        val date = instant.atZone(zone).toLocalDate()
        nightAt(windows.getOrPut(date) { forecastSolarWindow(forecast, date) }, instant)
    }
}

fun forecastIsNightAt(forecast: CityForecast, instant: Instant): Boolean? =
    forecastNightFlags(forecast, listOf(instant)).first()
