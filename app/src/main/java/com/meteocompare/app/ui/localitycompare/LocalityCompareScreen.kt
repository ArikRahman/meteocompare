package com.meteocompare.app.ui.localitycompare

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.buildLocalityDayMetrics
import com.meteocompare.app.domain.usecase.buildLocalityHourMetrics
import com.meteocompare.app.ui.components.uvIntensityColor
import com.meteocompare.app.ui.theme.confidenceColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val TAG_LOCALITY_COMPARE = "locality_comparison"
private val METRIC_WIDTH = 112.dp
private val CITY_WIDTH = 174.dp
private val CELL_HEIGHT = 64.dp
private const val COMPARISON_METRIC_COUNT = 8

@Composable
fun LocalityCompareScreen(onBack: () -> Unit) {
    val viewModel: LocalityCompareViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Only presentation metrics are recomputed for a selected date or hour.
    // Weather/UV streams stay cached in the ViewModel; toggling modes does not refetch.
    val calculator = viewModel.calculator
    LocalityCompareContent(
        state = state,
        calculator = calculator,
        onBack = onBack,
        onToggleCity = viewModel::toggleCity,
        onRefresh = viewModel::refresh
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalityCompareContent(
    state: LocalityCompareState,
    calculator: ConfidenceCalculator,
    onBack: () -> Unit,
    onToggleCity: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    currentDate: LocalDate = LocalDate.now(),
    currentHour: Int = LocalTime.now().hour
) {
    var comparisonMode by rememberSaveable { mutableStateOf("day") }
    var selectedHour by rememberSaveable { mutableStateOf(currentHour.coerceIn(0, 23)) }
    val availableDates = remember(state.selectedIds, state.forecasts, currentDate, comparisonMode) {
        state.selectedIds.flatMap { id ->
            val forecast = (state.forecasts[id] as? CompareForecastState.Ready)?.forecast
                ?: return@flatMap emptyList()
            if (comparisonMode == "hour") {
                val zone = resolveZoneOrUtc(forecast.city.timezone)
                forecast.seriesByModel.values.flatMap { series ->
                    series.hourly.timestamps.map { it.atZone(zone).toLocalDate() }
                }
            } else {
                forecast.seriesByModel.values.flatMap { it.daily.dates }
            }
        }.filter { !it.isBefore(currentDate) }.distinct().sorted()
    }
    val clockPreference = LocalClockFormatPreference.current
    var dateIso by rememberSaveable { mutableStateOf(currentDate.toString()) }
    LaunchedEffect(availableDates) {
        if (availableDates.isNotEmpty() && availableDates.none { it.toString() == dateIso }) {
            dateIso = availableDates.first().toString()
        }
    }
    val selectedDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(currentDate)
    val dateIndex = availableDates.indexOf(selectedDate)
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val hourFormatter = remember(locale, clockPreference) {
        WeatherTimeFormat.hourPadded(locale, clockPreference)
    }

    Scaffold(
        modifier = modifier.testTag(TAG_LOCALITY_COMPARE),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_localities_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("locality_compare_refresh")) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            )
        }
    ) { padding ->
        rememberScrollState().let { vertical ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(vertical),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.compare_localities_select),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (state.favorites.size < 2) {
                    CompareInfo(stringResource(R.string.compare_localities_add_cities))
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.favorites, key = { it.id }) { city ->
                            val isSelected = city.id in state.selectedIds
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleCity(city.id) },
                                enabled = isSelected && state.selectedIds.size > 2 ||
                                    !isSelected && state.selectedIds.size < 3,
                                label = { Text(city.name, maxLines = 1) },
                                modifier = Modifier.testTag("compare_city_${city.id}")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = comparisonMode == "day",
                            onClick = { comparisonMode = "day" },
                            label = { Text(stringResource(R.string.compare_mode_day)) },
                            modifier = Modifier.testTag("compare_mode_day")
                        )
                        FilterChip(
                            selected = comparisonMode == "hour",
                            onClick = { comparisonMode = "hour" },
                            label = { Text(stringResource(R.string.compare_mode_hour)) },
                            modifier = Modifier.testTag("compare_mode_hour")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (dateIndex > 0) dateIso = availableDates[dateIndex - 1].toString() },
                            enabled = dateIndex > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(R.string.compare_previous_day))
                        }
                        Text(
                            text = selectedDate.format(formatter),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("compare_selected_date")
                        )
                        IconButton(
                            onClick = { if (dateIndex in 0 until availableDates.lastIndex) dateIso = availableDates[dateIndex + 1].toString() },
                            enabled = dateIndex in 0 until availableDates.lastIndex
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.compare_next_day))
                        }
                    }
                    if (comparisonMode == "hour") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { selectedHour = (selectedHour + 23) % 24 }) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(R.string.compare_previous_hour))
                            }
                            Text(
                                text = LocalTime.of(selectedHour, 0).format(hourFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("compare_selected_hour")
                            )
                            IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.compare_next_hour))
                            }
                        }
                        Text(
                            text = stringResource(R.string.compare_hour_local_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (availableDates.isEmpty()) {
                        val waiting = state.selectedIds.any { id ->
                            state.forecasts[id] == null || state.forecasts[id] is CompareForecastState.Loading
                        }
                        CompareInfo(stringResource(
                            if (waiting) R.string.compare_loading else R.string.compare_no_dates
                        ))
                    }
                    val units = WeatherUnits(state.unitSystem)
                    val dailySummaries = remember(comparisonMode, state.selectedIds, state.forecasts,
                        state.uvForecasts, selectedDate, calculator) {
                        if (comparisonMode != "day") emptyMap() else state.selectedIds.associateWith { id ->
                            (state.forecasts[id] as? CompareForecastState.Ready)?.forecast?.let {
                                buildLocalityDayMetrics(it, selectedDate, calculator, state.uvForecasts[id])
                            }
                        }
                    }
                    val hourlySummaries = remember(comparisonMode, state.selectedIds, state.forecasts,
                        state.uvForecasts, selectedDate, selectedHour, calculator) {
                        if (comparisonMode != "hour") emptyMap() else state.selectedIds.associateWith { id ->
                            (state.forecasts[id] as? CompareForecastState.Ready)?.forecast?.let {
                                buildLocalityHourMetrics(it, selectedDate, selectedHour, calculator, state.uvForecasts[id])
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.width(METRIC_WIDTH)) {
                            ComparisonHeading(stringResource(R.string.compare_metric_heading))
                            ComparisonCell(stringResource(
                                if (comparisonMode == "day") R.string.compare_temperature
                                else R.string.compare_hour_temperature
                            ))
                            ComparisonCell(stringResource(
                                if (comparisonMode == "day") R.string.compare_rain
                                else R.string.compare_hour_rain
                            ))
                            ComparisonCell(stringResource(R.string.compare_rain_probability))
                            ComparisonCell(stringResource(
                                if (comparisonMode == "day") R.string.compare_cloud_cover
                                else R.string.compare_hour_cloud_cover
                            ))
                            ComparisonCell(stringResource(
                                if (comparisonMode == "day") R.string.compare_wind
                                else R.string.compare_hour_wind
                            ))
                            ComparisonCell(stringResource(R.string.compare_sunshine))
                            ComparisonCell(stringResource(
                                if (comparisonMode == "day") R.string.compare_agreement
                                else R.string.compare_hour_agreement
                            ))
                            ComparisonCell(stringResource(
                                if (comparisonMode == "day") R.string.compare_uv_max
                                else R.string.compare_uv_hour
                            ))
                        }
                        state.selectedCities.forEach { city ->
                            val result = state.forecasts[city.id]
                            val day = dailySummaries[city.id]
                            val hour = hourlySummaries[city.id]
                            Column(Modifier.width(CITY_WIDTH).testTag("compare_column_${city.id}")) {
                                ComparisonHeading(city.name)
                                when {
                                    result is CompareForecastState.Loading || result == null ->
                                        CompareInfoCell(stringResource(R.string.compare_loading))
                                    result is CompareForecastState.Failed ->
                                        CompareInfoCell(stringResource(R.string.compare_unavailable))
                                    comparisonMode == "day" && day == null ->
                                        CompareInfoCell(stringResource(R.string.compare_no_data_for_day))
                                    comparisonMode == "hour" && hour == null ->
                                        CompareInfoCell(stringResource(R.string.compare_no_data_for_hour))
                                    comparisonMode == "day" && day != null -> {
                                        ComparisonValue(units.temp(day.minC, locale = locale) + " / " + units.temp(day.maxC, locale = locale))
                                        ComparisonValue(units.rain(day.precipitationMm, locale))
                                        ComparisonValue(day.rainProbabilityPercent?.let { "$it %" } ?: "—",
                                            modifier = Modifier.testTag("compare_rain_probability_${city.id}"))
                                        ComparisonValue(day.cloudCoverPercent?.let { "$it %" } ?: "—",
                                            modifier = Modifier.testTag("compare_cloud_cover_${city.id}"))
                                        ComparisonValue(units.speed(day.maxWindKmh, locale = locale))
                                        ComparisonValue(day.estimatedSunHours?.let {
                                            stringResource(R.string.compare_sun_hours_value, units.number(it, 1, locale))
                                        } ?: "—")
                                        ComparisonValue(day.agreementPercent?.let { "$it %" } ?: "—",
                                            day.agreementPercent?.let { confidenceColor(it) })
                                        CompareUvValue(day.uvMax, city.id)
                                    }
                                    hour != null -> {
                                        ComparisonValue(units.temp(hour.temperatureC, locale = locale))
                                        ComparisonValue(units.rain(hour.precipitationMm, locale))
                                        ComparisonValue(hour.rainProbabilityPercent?.let { "$it %" } ?: "—",
                                            modifier = Modifier.testTag("compare_rain_probability_${city.id}"))
                                        ComparisonValue(hour.cloudCoverPercent?.let { "$it %" } ?: "—",
                                            modifier = Modifier.testTag("compare_cloud_cover_${city.id}"))
                                        ComparisonValue(units.speed(hour.windKmh, locale = locale))
                                        ComparisonValue(hour.estimatedSunHours?.let {
                                            stringResource(R.string.compare_sun_hours_value, units.number(it, 1, locale))
                                        } ?: "—")
                                        ComparisonValue(hour.agreementPercent?.let { "$it %" } ?: "—",
                                            hour.agreementPercent?.let { confidenceColor(it) })
                                        CompareUvValue(hour.uvIndex, city.id)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            if (comparisonMode == "day") R.string.compare_rain_cloud_disclaimer
                            else R.string.compare_hour_rain_cloud_disclaimer
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        text = stringResource(R.string.compare_sunshine_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        text = stringResource(
                            if (comparisonMode == "day") R.string.compare_agreement_disclaimer
                            else R.string.compare_hour_agreement_disclaimer
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    Text(
                        text = stringResource(R.string.compare_uv_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareInfo(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ComparisonHeading(label: String) {
    Box(Modifier.height(70.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ComparisonCell(label: String) {
    Box(Modifier.height(CELL_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComparisonValue(value: String, tone: Color? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT).padding(bottom = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                color = tone ?: MaterialTheme.colorScheme.onSurface,
                modifier = modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun CompareInfoCell(text: String) {
    Box(Modifier.height(CELL_HEIGHT * COMPARISON_METRIC_COUNT).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp))
    }
}


@Composable
private fun CompareUvValue(value: Double?, cityId: String) {
    val valid = value?.takeIf { it.isFinite() && it >= 0.0 }
    val darkBackground = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ComparisonValue(
        valid?.let { stringResource(R.string.uv_index_value, it) } ?: "—",
        valid?.let { uvIntensityColor(it, darkBackground) },
        modifier = Modifier.testTag("compare_uv_${cityId}")
    )
}
