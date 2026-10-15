package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.util.SolarTimes
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** A common *calendar date* is evaluated in each locality’s own timezone. */
data class LocalityDayMetrics(
    val date: LocalDate,
    val minC: Double?,
    val maxC: Double?,
    val precipitationMm: Double?,
    /** Consensus-estimated daily chance of rain, not a calibrated observed frequency. */
    val rainProbabilityPercent: Int?,
    /** Median of model daily cloud-cover means, evaluated in the locality timezone. */
    val cloudCoverPercent: Int?,
    val maxWindKmh: Double?,
    /** Cloud-adjusted *estimate* of clear-sky-equivalent hours, NOT measured sunshine. */
    val estimatedSunHours: Double?,
    /** Model convergence (not forecast accuracy or rain probability). */
    val agreementPercent: Int?,
    val modelCount: Int
)

/**
 * Uses the same robust daily consensus as the detail screen; no averaging of
 * independent model families is performed here for temperature/wind/rain.
 */
fun buildLocalityDayMetrics(
    forecast: CityForecast,
    date: LocalDate,
    calculator: ConfidenceCalculator
): LocalityDayMetrics? {
    if (forecast.seriesByModel.values.none { date in it.daily.dates }) return null
    val daily = calculator.dayConfidence(forecast, date)
    val precipitation = daily.precipitation
    val centralRain = when (precipitation) {
        null -> null
        else -> precipitation.meta.centralAmountMm ?: when (precipitation) {
            is PrecipitationConfidence.NoRain -> 0.0
            is PrecipitationConfidence.Rain -> precipitation.meanMm
            is PrecipitationConfidence.Divided -> precipitation.rainMeanMm
        }
    }?.takeIf { it.isFinite() && it >= 0.0 }
    val count = listOfNotNull(
        daily.tempMin?.modelCount,
        daily.tempMax?.modelCount,
        daily.windMax?.modelCount,
        precipitation?.modelCount
    ).maxOrNull() ?: 0
    return LocalityDayMetrics(
        date = date,
        minC = daily.tempMin?.centralValue?.takeIf(Double::isFinite),
        maxC = daily.tempMax?.centralValue?.takeIf(Double::isFinite),
        precipitationMm = centralRain,
        rainProbabilityPercent = precipitation?.meta?.probabilityPercent?.takeIf { it in 0..100 },
        cloudCoverPercent = localityDailyCloudCoverPercent(forecast, date),
        maxWindKmh = daily.windMax?.centralValue?.takeIf(Double::isFinite),
        estimatedSunHours = estimatedSunshineHours(forecast, date),
        agreementPercent = daily.convergencePercent?.coerceIn(0, 100),
        modelCount = count
    )
}

/**
 * Median of the available per-model daily cloud-cover averages. Require at
 * least half of each locality's calendar day (23/24/25 hours around DST) so
 * a single hourly observation never stands in for a daily percentage.
 * Duplicate/invalid samples and models without this day are ignored.
 */
internal fun localityDailyCloudCoverPercent(forecast: CityForecast, date: LocalDate): Int? {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val dayHours = (end.epochSecond - start.epochSecond) / 3600.0
    if (dayHours <= 0.0) return null

    val means = forecast.seriesByModel.values.mapNotNull { series ->
        if (date !in series.daily.dates) return@mapNotNull null
        val seen = hashSetOf<Instant>()
        val valid = series.hourly.timestamps.mapIndexedNotNull { index, timestamp ->
            if (timestamp < start || timestamp >= end) return@mapIndexedNotNull null
            val clouds = series.hourly.cloudCover.getOrNull(index)?.takeIf { it in 0..100 }
                ?: return@mapIndexedNotNull null
            clouds.takeIf { seen.add(timestamp) }
        }
        if (valid.size < dayHours / 2.0) null else valid.average()
    }.sorted()
    if (means.isEmpty()) return null
    val middle = means.size / 2
    val median = if (means.size % 2 == 1) means[middle]
    else (means[middle - 1] + means[middle]) / 2.0
    return median.roundToInt().coerceIn(0, 100)
}

/** Median of independent model estimates avoids over-weighting a dense hourly grid. */
private fun estimatedSunshineHours(forecast: CityForecast, date: LocalDate): Double? {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val hours = forecast.seriesByModel.values.mapNotNull { series ->
        val day = series.daily.dates.indexOf(date)
        if (day < 0) return@mapNotNull null
        val apiRise = series.daily.sunrise.getOrNull(day)
        val apiSet = series.daily.sunset.getOrNull(day)
        val (rise, set) = if (apiRise != null && apiSet != null && apiSet > apiRise) {
            apiRise to apiSet
        } else {
            val fallback = runCatching {
                SolarTimes.compute(forecast.city.latitude, forecast.city.longitude, date, zone)
            }.getOrNull() ?: return@mapNotNull null
            val start = fallback.sunrise?.let { date.atTime(it).atZone(zone).toInstant() }
            val end = fallback.sunset?.let { date.atTime(it).atZone(zone).toInstant() }
            if (start == null || end == null || end <= start) return@mapNotNull null
            start to end
        }
        estimateCloudAdjustedDaylight(
            rise, set, series.hourly.timestamps, series.hourly.cloudCover
        )
    }.sorted()
    if (hours.isEmpty()) return null
    val middle = hours.size / 2
    return if (hours.size % 2 == 1) hours[middle]
    else (hours[middle - 1] + hours[middle]) / 2.0
}

/**
 * Hourly cloud cover is interpreted as applying to [hour, hour+1h). Only
 * overlapping daylight contributes. Reject partial data (< 50% daylight)
 * instead of presenting an artificially sunny/overcast full day.
 */
fun estimateCloudAdjustedDaylight(
    sunrise: Instant,
    sunset: Instant,
    timestamps: List<Instant>,
    cloudCover: List<Int?>
): Double? {
    if (sunset <= sunrise) return null
    val daylightSeconds = (sunset.epochSecond - sunrise.epochSecond).toDouble()
    if (daylightSeconds <= 0.0 || daylightSeconds > 24.0 * 3600.0) return null
    var coveredSeconds = 0.0
    var clearSeconds = 0.0
    // Only use unique hours; duplicate timestamps cannot double-count coverage.
    val seen = hashSetOf<Instant>()
    for (index in 0 until minOf(timestamps.size, cloudCover.size)) {
        val start = timestamps[index]
        val clouds = cloudCover[index] ?: continue
        if (clouds !in 0..100 || !seen.add(start)) continue
        val end = start.plusSeconds(3600)
        val overlap = max(0L, minOf(end.epochSecond, sunset.epochSecond) -
            maxOf(start.epochSecond, sunrise.epochSecond)).toDouble()
        coveredSeconds += overlap
        clearSeconds += overlap * (100 - clouds) / 100.0
    }
    if (coveredSeconds < daylightSeconds * 0.5) return null
    return (daylightSeconds / 3600.0 * clearSeconds / coveredSeconds).coerceIn(0.0, 24.0)
}
