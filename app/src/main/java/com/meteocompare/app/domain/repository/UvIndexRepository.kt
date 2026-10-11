package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.UvForecast

/** Cache-aware, coalesced per-location UV lookup. No refresh flag by design. */
interface UvIndexRepository {
    suspend fun get(city: City): UvForecast?
}
