package com.meteocompare.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** UV forecast from Open-Meteo's best-match product, independent of model consensus. */
data class UvForecast(
    val hourly: Map<Instant, Double>,
    val dailyMaximum: Map<LocalDate, Double>
) {
    /** The forecast's hourly timestamps are UTC instants, even around DST changes. */
    fun at(instant: Instant): Double? = hourly[instant.truncatedTo(ChronoUnit.HOURS)]
    fun maxOn(date: LocalDate): Double? = dailyMaximum[date]
}

fun Double.validUvIndexOrNull(): Double? = takeIf { it.isFinite() && it >= 0.0 }
