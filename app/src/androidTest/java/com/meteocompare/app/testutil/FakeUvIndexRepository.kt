package com.meteocompare.app.testutil

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.repository.UvIndexRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UV repository used by Hilt instrumentation tests instead of the Open-Meteo endpoint.
 * Returns no UV data by default, so tests remain deterministic and offline.
 */
@Singleton
class FakeUvIndexRepository @Inject constructor() : UvIndexRepository {
    private val forecasts = ConcurrentHashMap<String, UvForecast>()

    override suspend fun get(city: City): UvForecast? = forecasts[city.id]

    fun setForecast(city: City, forecast: UvForecast?) {
        if (forecast == null) forecasts.remove(city.id) else forecasts[city.id] = forecast
    }

    fun reset() {
        forecasts.clear()
    }
}
