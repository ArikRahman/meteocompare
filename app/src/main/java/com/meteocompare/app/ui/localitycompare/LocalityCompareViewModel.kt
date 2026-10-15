package com.meteocompare.app.ui.localitycompare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.UvIndexRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

internal sealed interface CompareForecastState {
    data object Loading : CompareForecastState
    data class Ready(val forecast: CityForecast) : CompareForecastState
    data class Failed(val message: String) : CompareForecastState
}

internal data class LocalityCompareState(
    val favorites: List<City> = emptyList(),
    val selectedIds: List<String> = emptyList(),
    val forecasts: Map<String, CompareForecastState> = emptyMap(),
    val uvForecasts: Map<String, UvForecast> = emptyMap(),
    val unitSystem: UnitSystem = UnitSystem.METRIC
) {
    val selectedCities: List<City> get() = selectedIds.mapNotNull { id -> favorites.firstOrNull { it.id == id } }
}

/**
 * Observes favorites and uses the same cache-aware model requests as Home.
 * Each city is loaded independently; an unavailable destination must not hide
 * the others. Removing a favorite cancels its flow before another can display it.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class LocalityCompareViewModel @Inject constructor(
    private val cities: CityRepository,
    private val forecasts: ForecastRepository,
    private val uvRepository: UvIndexRepository,
    private val preferences: UserPreferencesRepository,
    val calculator: ConfidenceCalculator
) : ViewModel() {
    private val favorites = cities.observeFavorites().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )
    private val selectedIds = MutableStateFlow<List<String>>(emptyList())
    private val loaded = MutableStateFlow<Map<String, CompareForecastState>>(emptyMap())
    private val loadedUv = MutableStateFlow<Map<String, UvForecast>>(emptyMap())
    private val units = preferences.observeUnitSystem().stateIn(
        viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC
    )
    private val manualRefresh = MutableStateFlow(0)
    private var handledRefresh = 0

    val uiState: StateFlow<LocalityCompareState> = combine(
        favorites, selectedIds, loaded, loadedUv, units
    ) { all, selected, data, uv, unit ->
        LocalityCompareState(all, selected, data, uv, unit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalityCompareState())

    init {
        viewModelScope.launch {
            favorites.collect { available ->
                val restored = restoreComparedCities(
                    available.map(City::id), selectedIds.value
                )
                if (restored != selectedIds.value) selectedIds.value = restored.take(3)
            }
        }
        viewModelScope.launch {
            combine(
                favorites,
                selectedIds,
                preferences.observeEnabledModels(),
                preferences.observeRefreshInterval(),
                manualRefresh
            ) { all, selected, models, interval, refresh ->
                Request(
                    cities = selected.mapNotNull { id -> all.firstOrNull { it.id == id } },
                    models = models,
                    interval = interval,
                    refresh = refresh
                )
            }.collectLatest { request ->
                val forceNetwork = request.refresh > handledRefresh
                handledRefresh = request.refresh
                loaded.value = request.cities.associate { it.id to CompareForecastState.Loading }
                loadedUv.value = emptyMap()
                if (request.models.isEmpty()) {
                    loaded.value = request.cities.associate {
                        it.id to CompareForecastState.Failed("No weather models enabled")
                    }
                    return@collectLatest
                }
                coroutineScope {
                    request.cities.forEach { city ->
                        launch {
                            try {
                                val result = uvRepository.get(city)
                                if (result != null) loadedUv.update { it + (city.id to result) }
                            } catch (cancel: kotlinx.coroutines.CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                // UV is optional: never block the multi-model forecasts.
                            }
                        }
                        launch {
                            try {
                                forecasts.getCityForecastStream(
                                    city = city,
                                    models = request.models,
                                    forceRefresh = forceNetwork,
                                    maxCacheAgeMs = request.interval.maxCacheAgeMs
                                ).collect { result ->
                                    val state = when (result) {
                                        is ApiResult.Success -> CompareForecastState.Ready(result.data)
                                        is ApiResult.Error -> CompareForecastState.Failed(result.message)
                                    }
                                    loaded.update { current ->
                                        if (city.id in current) current + (city.id to state) else current
                                    }
                                }
                            } catch (cancel: kotlinx.coroutines.CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                loaded.update { current ->
                                    if (city.id in current) current + (
                                        city.id to CompareForecastState.Failed(error.message.orEmpty())
                                    ) else current
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun toggleCity(id: String) {
        if (favorites.value.none { it.id == id }) return
        selectedIds.update { current -> toggleComparedCity(current, id) }
    }

    fun refresh() { manualRefresh.update { it + 1 } }

    private data class Request(
        val cities: List<City>,
        val models: List<WeatherModel>,
        val interval: RefreshInterval,
        val refresh: Int
    )
}
