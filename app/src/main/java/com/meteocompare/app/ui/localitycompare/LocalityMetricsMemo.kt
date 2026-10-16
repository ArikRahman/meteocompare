package com.meteocompare.app.ui.localitycompare

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.LocalityDayMetrics
import com.meteocompare.app.domain.usecase.LocalityHourMetrics
import com.meteocompare.app.domain.usecase.buildLocalityDayMetrics
import com.meteocompare.app.domain.usecase.buildLocalityHourMetrics
import com.meteocompare.app.domain.util.ForecastSolarWindow
import com.meteocompare.app.domain.util.forecastSolarWindow
import java.time.LocalDate
import java.util.LinkedHashMap

/**
 * Small, UI-scoped LRU of expensive consensus/solar summaries. A visual redraw,
 * unit/theme/language change, or a return to a visited date does not repeat the
 * calculations. New forecast or UV object identities invalidate the entry.
 *
 * This cache neither reads disk nor launches a coroutine nor accesses a network.
 * It belongs to the comparison screen and is intentionally bounded. A full
 * three-city day (72 hourly samples plus three daily results) fits in cache.
 */
internal class LocalityMetricsMemo(
    private val calculator: ConfidenceCalculator,
    private val maxEntries: Int = 240
) {
    init { require(maxEntries > 0) }

    private data class Key(val cityId: String, val date: LocalDate, val hour: Int?)
    private data class CacheEntry(
        val forecast: CityForecast,
        val uv: UvForecast?,
        val daily: LocalityDayMetrics? = null,
        val hourly: LocalityHourMetrics? = null
    )
    private data class Revision(val forecast: CityForecast, val uv: UvForecast?)
    private val revisions = mutableMapOf<String, Revision>()
    private val entries = object : LinkedHashMap<Key, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CacheEntry>?): Boolean =
            size > maxEntries
    }

    private data class SolarEntry(val forecast: CityForecast, val window: ForecastSolarWindow)
    private val solarCache = object : LinkedHashMap<Key, SolarEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, SolarEntry>?): Boolean =
            size > maxEntries
    }

    /** Evict obsolete large forecast graphs as soon as a city receives fresh data. */
    private fun trackRevision(forecast: CityForecast, uv: UvForecast?) {
        val id = forecast.city.id
        val previous = revisions[id]
        if (previous != null && (previous.forecast !== forecast || previous.uv !== uv)) {
            entries.keys.removeAll { it.cityId == id }
            // A UV-only update does not invalidate solar data, which belongs to the forecast.
            if (previous.forecast !== forecast) solarCache.keys.removeAll { it.cityId == id }
        }
        revisions[id] = Revision(forecast, uv)
    }

    /** Deselecting a city must release all forecasts retained solely by this UI cache. */
    fun retainCities(ids: Set<String>) {
        revisions.keys.retainAll(ids)
        entries.keys.removeAll { it.cityId !in ids }
        solarCache.keys.removeAll { it.cityId !in ids }
    }

    fun day(forecast: CityForecast, date: LocalDate, uv: UvForecast?): LocalityDayMetrics? {
        trackRevision(forecast, uv)
        val key = Key(forecast.city.id, date, null)
        val entry = entries[key]
        if (entry != null && entry.forecast === forecast && entry.uv === uv) return entry.daily
        return buildLocalityDayMetrics(forecast, date, calculator, uv).also { value ->
            entries[key] = CacheEntry(forecast, uv, daily = value)
        }
    }

    fun hour(forecast: CityForecast, date: LocalDate, hour: Int, uv: UvForecast?): LocalityHourMetrics? {
        trackRevision(forecast, uv)
        val key = Key(forecast.city.id, date, hour)
        val entry = entries[key]
        if (entry != null && entry.forecast === forecast && entry.uv === uv) return entry.hourly
        val solarKey = Key(forecast.city.id, date, null)
        val oldSolar = solarCache[solarKey]
        val solar = if (oldSolar?.forecast === forecast) oldSolar.window else
            forecastSolarWindow(forecast, date).also { solarCache[solarKey] = SolarEntry(forecast, it) }
        return buildLocalityHourMetrics(forecast, date, hour, calculator, uv, solar).also { value ->
            entries[key] = CacheEntry(forecast, uv, hourly = value)
        }
    }

    fun size(): Int = entries.size
}
