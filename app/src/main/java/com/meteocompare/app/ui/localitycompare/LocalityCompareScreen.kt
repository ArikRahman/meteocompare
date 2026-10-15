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
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.buildLocalityDayMetrics
import com.meteocompare.app.ui.theme.confidenceColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val TAG_LOCALITY_COMPARE = "locality_comparison"
private val METRIC_WIDTH = 112.dp
private val CITY_WIDTH = 174.dp
private val CELL_HEIGHT = 64.dp
private const val COMPARISON_METRIC_COUNT = 7

@Composable
fun LocalityCompareScreen(onBack: () -> Unit) {
    val viewModel: LocalityCompareViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The calculator is part of the presentation here only for the selected date;
    // weather data stays in the ViewModel and never refetches on date changes.
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
    currentDate: LocalDate = LocalDate.now()
) {
    val availableDates = remember(state.selectedIds, state.forecasts, currentDate) {
        state.selectedIds.flatMap { id ->
            (state.forecasts[id] as? CompareForecastState.Ready)
                ?.forecast?.seriesByModel?.values.orEmpty()
                .flatMap { it.daily.dates }
        }.filter { !it.isBefore(currentDate) }.distinct().sorted()
    }
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
                    if (availableDates.isEmpty()) {
                        val waiting = state.selectedIds.any { id ->
                            state.forecasts[id] == null || state.forecasts[id] is CompareForecastState.Loading
                        }
                        CompareInfo(stringResource(
                            if (waiting) R.string.compare_loading else R.string.compare_no_dates
                        ))
                    }
                    val units = WeatherUnits(state.unitSystem)
                    val summaries = remember(state.selectedIds, state.forecasts, selectedDate, calculator) {
                        state.selectedIds.associateWith { id ->
                            (state.forecasts[id] as? CompareForecastState.Ready)?.forecast?.let {
                                buildLocalityDayMetrics(it, selectedDate, calculator)
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
                            ComparisonCell(stringResource(R.string.compare_temperature))
                            ComparisonCell(stringResource(R.string.compare_rain))
                            ComparisonCell(stringResource(R.string.compare_rain_probability))
                            ComparisonCell(stringResource(R.string.compare_cloud_cover))
                            ComparisonCell(stringResource(R.string.compare_wind))
                            ComparisonCell(stringResource(R.string.compare_sunshine))
                            ComparisonCell(stringResource(R.string.compare_agreement))
                        }
                        state.selectedCities.forEach { city ->
                            val result = state.forecasts[city.id]
                            val values = summaries[city.id]
                            Column(Modifier.width(CITY_WIDTH).testTag("compare_column_${city.id}")) {
                                ComparisonHeading(city.name)
                                when (result) {
                                    is CompareForecastState.Loading, null -> CompareInfoCell(stringResource(R.string.compare_loading))
                                    is CompareForecastState.Failed -> CompareInfoCell(stringResource(R.string.compare_unavailable))
                                    is CompareForecastState.Ready -> if (values == null) {
                                        CompareInfoCell(stringResource(R.string.compare_no_data_for_day))
                                    } else {
                                        ComparisonValue(units.temp(values.minC) + " / " + units.temp(values.maxC))
                                        ComparisonValue(units.rain(values.precipitationMm))
                                        ComparisonValue(
                                            values.rainProbabilityPercent?.let { "$it %" } ?: "—",
                                            modifier = Modifier.testTag("compare_rain_probability_${city.id}")
                                        )
                                        ComparisonValue(
                                            values.cloudCoverPercent?.let { "$it %" } ?: "—",
                                            modifier = Modifier.testTag("compare_cloud_cover_${city.id}")
                                        )
                                        ComparisonValue(units.speed(values.maxWindKmh))
                                        ComparisonValue(values.estimatedSunHours?.let {
                                            stringResource(R.string.compare_sun_hours_value, units.number(it, 1, locale))
                                        } ?: "—")
                                        ComparisonValue(
                                            values.agreementPercent?.let { "$it %" } ?: "—",
                                            values.agreementPercent?.let { confidenceColor(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.compare_rain_cloud_disclaimer),
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
                        text = stringResource(R.string.compare_agreement_disclaimer),
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
