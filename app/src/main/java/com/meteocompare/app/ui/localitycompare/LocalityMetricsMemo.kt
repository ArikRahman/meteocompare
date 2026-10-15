package com.meteocompare.app.ui.localitycompare

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.LocalityDayMetrics
import com.meteocompare.app.domain.usecase.LocalityHourMetrics
import com.meteocompare.app.domain.usecase.buildLocalityDayMetrics
import com.meteocompare.app.domain.usecase.buildLocalityHourMetrics
import java.time.LocalDate
import java.util.LinkedHashMap

/**
 * Small, UI-scoped LRU of expensive consensus/solar summaries. A visual redraw,
 * unit/theme/language change, or a return to a visited date does not repeat the
 * calculations. New forecast or UV object identities invalidate the entry.
 *
 * This cache neither reads disk nor launches a coroutine nor accesses a network.
 * It belongs to the comparison screen and is intentionally bounded.
 */
internal class LocalityMetricsMemo(
    private val calculator: ConfidenceCalculator,
    private val maxEntries: Int = 72
) {
    init { require(maxEntries > 0) }

    private data class Key(val cityId: String, val date: LocalDate, val hour: Int?)
    private data class CacheEntry(
        val forecast: CityForecast,
        val uv: UvForecast?,
        val daily: LocalityDayMetrics? = null,
        val hourly: LocalityHourMetrics? = null
    )
    private val entries = object : LinkedHashMap<Key, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CacheEntry>?): Boolean =
            size > maxEntries
    }

    fun day(forecast: CityForecast, date: LocalDate, uv: UvForecast?): LocalityDayMetrics? {
        val key = Key(forecast.city.id, date, null)
        val entry = entries[key]
        if (entry != null && entry.forecast === forecast && entry.uv === uv) return entry.daily
        return buildLocalityDayMetrics(forecast, date, calculator, uv).also { value ->
            entries[key] = CacheEntry(forecast, uv, daily = value)
        }
    }

    fun hour(forecast: CityForecast, date: LocalDate, hour: Int, uv: UvForecast?): LocalityHourMetrics? {
        val key = Key(forecast.city.id, date, hour)
        val entry = entries[key]
        if (entry != null && entry.forecast === forecast && entry.uv === uv) return entry.hourly
        return buildLocalityHourMetrics(forecast, date, hour, calculator, uv).also { value ->
            entries[key] = CacheEntry(forecast, uv, hourly = value)
        }
    }

    fun size(): Int = entries.size
}
