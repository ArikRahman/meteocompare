package com.meteocompare.app.ui.localitycompare

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
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
private val CITY_WIDTH = 244.dp
private val METRIC_ROW_HEIGHT = 82.dp
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
                    val units = remember(state.unitSystem) { WeatherUnits(state.unitSystem) }
                    // Expensive model consensus and astronomical summaries live in a bounded
                    // screen-local memo. Locale, theme, scroll and chip redraws do not recompute them.
                    val memo = remember(calculator) { LocalityMetricsMemo(calculator) }
                    val selectedCities = state.selectedCities
                    val dailySummaries = if (comparisonMode == "day") {
                        selectedCities.associate { city ->
                            city.id to (state.forecasts[city.id] as? CompareForecastState.Ready)
                                ?.forecast?.let { memo.day(it, selectedDate, state.uvForecasts[city.id]) }
                        }
                    } else emptyMap()
                    val hourlySummaries = if (comparisonMode == "hour") {
                        selectedCities.associate { city ->
                            city.id to (state.forecasts[city.id] as? CompareForecastState.Ready)
                                ?.forecast?.let { memo.hour(it, selectedDate, selectedHour, state.uvForecasts[city.id]) }
                        }
                    } else emptyMap()
                    Text(
                        stringResource(R.string.compare_metric_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        selectedCities.forEachIndexed { index, city ->
                            val result = state.forecasts[city.id]
                            val day = dailySummaries[city.id]
                            val hour = hourlySummaries[city.id]
                            CompareLocalityCard(
                                cityName = city.name,
                                cityId = city.id,
                                index = index,
                                isHourly = comparisonMode == "hour",
                                day = day,
                                hour = hour,
                                units = units,
                                locale = locale,
                                unavailable = when {
                                    result == null || result is CompareForecastState.Loading ->
                                        stringResource(R.string.compare_loading)
                                    result is CompareForecastState.Failed ->
                                        stringResource(R.string.compare_unavailable)
                                    comparisonMode == "day" && day == null ->
                                        stringResource(R.string.compare_no_data_for_day)
                                    comparisonMode == "hour" && hour == null ->
                                        stringResource(R.string.compare_no_data_for_hour)
                                    else -> null
                                }
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                val disclaimerColor = MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    text = stringResource(
                                        if (comparisonMode == "day") R.string.compare_rain_cloud_disclaimer
                                        else R.string.compare_hour_rain_cloud_disclaimer
                                    ),
                                    style = MaterialTheme.typography.bodySmall, color = disclaimerColor
                                )
                                Text(stringResource(R.string.compare_sunshine_disclaimer),
                                    style = MaterialTheme.typography.bodySmall, color = disclaimerColor)
                                Text(
                                    stringResource(
                                        if (comparisonMode == "day") R.string.compare_agreement_disclaimer
                                        else R.string.compare_hour_agreement_disclaimer
                                    ), style = MaterialTheme.typography.bodySmall, color = disclaimerColor
                                )
                                Text(stringResource(R.string.compare_uv_disclaimer),
                                    style = MaterialTheme.typography.bodySmall, color = disclaimerColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareInfo(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A city is one vertically aligned, self-explanatory column. No frozen label column
 * consumes half the viewport on narrow phones; each metric keeps its own label.
 * All progress bars represent a *known* scale, not an invented ranking.
 */
@Composable
private fun CompareLocalityCard(
    cityName: String,
    cityId: String,
    index: Int,
    isHourly: Boolean,
    day: com.meteocompare.app.domain.usecase.LocalityDayMetrics?,
    hour: com.meteocompare.app.domain.usecase.LocalityHourMetrics?,
    units: WeatherUnits,
    locale: java.util.Locale,
    unavailable: String?
) {
    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.surface.luminance() < 0.5f
    val rainTone = if (darkTheme) Color(0xFF91CAFF) else Color(0xFF286AA4)
    val cloudTone = colors.secondary
    val sunTone = if (darkTheme) Color(0xFFF4C46A) else Color(0xFF956213)
    val windTone = colors.tertiary
    val agreement = if (isHourly) hour?.agreementPercent else day?.agreementPercent
    val uv = if (isHourly) hour?.uvIndex else day?.uvMax
    val uvValid = uv?.takeIf { it.isFinite() && it >= 0.0 }
    val uvTone = uvValid?.let { uvIntensityColor(it, darkTheme) }
    val temp = if (isHourly) units.temp(hour?.temperatureC, locale = locale)
        else if (day == null) "—" else
            units.temp(day.minC, locale = locale) + " / " + units.temp(day.maxC, locale = locale)

    Surface(
        modifier = Modifier.width(CITY_WIDTH).testTag("compare_column_$cityId"),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(colors.primaryContainer, colors.secondaryContainer)),
                        RoundedCornerShape(17.dp)
                    ).padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationCity, contentDescription = null,
                    tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                Text(cityName, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = colors.onPrimaryContainer, modifier = Modifier.weight(1f))
                Text("${index + 1}", color = colors.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium)
            }
            if (unavailable != null) {
                Box(Modifier.fillMaxWidth().height(METRIC_ROW_HEIGHT * COMPARISON_METRIC_COUNT),
                    contentAlignment = Alignment.Center) {
                    Text(unavailable, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp))
                }
            } else {
                CompareMetricTile(
                    label = stringResource(if (isHourly) R.string.compare_hour_temperature else R.string.compare_temperature),
                    value = temp, icon = Icons.Default.Thermostat, tone = colors.primary,
                    large = true
                )
                CompareMetricTile(
                    label = stringResource(if (isHourly) R.string.compare_hour_rain else R.string.compare_rain),
                    value = units.rain(if (isHourly) hour?.precipitationMm else day?.precipitationMm, locale),
                    icon = Icons.Default.WaterDrop, tone = rainTone
                )
                val rain = if (isHourly) hour?.rainProbabilityPercent else day?.rainProbabilityPercent
                CompareMetricTile(
                    label = stringResource(R.string.compare_rain_probability),
                    value = rain?.let { "$it %" } ?: "—", icon = Icons.Default.Opacity,
                    tone = rainTone, progress = rain?.let { it / 100f },
                    valueTag = "compare_rain_probability_$cityId",
                    barTag = "compare_rain_bar_$cityId"
                )
                val clouds = if (isHourly) hour?.cloudCoverPercent else day?.cloudCoverPercent
                CompareMetricTile(
                    label = stringResource(if (isHourly) R.string.compare_hour_cloud_cover else R.string.compare_cloud_cover),
                    value = clouds?.let { "$it %" } ?: "—", icon = Icons.Default.Cloud,
                    tone = cloudTone, progress = clouds?.let { it / 100f },
                    valueTag = "compare_cloud_cover_$cityId",
                    barTag = "compare_cloud_bar_$cityId"
                )
                CompareMetricTile(
                    label = stringResource(if (isHourly) R.string.compare_hour_wind else R.string.compare_wind),
                    value = units.speed(if (isHourly) hour?.windKmh else day?.maxWindKmh, locale = locale),
                    icon = Icons.Default.Air, tone = windTone
                )
                CompareMetricTile(
                    label = stringResource(R.string.compare_sunshine),
                    value = (if (isHourly) hour?.estimatedSunHours else day?.estimatedSunHours)?.let {
                        stringResource(R.string.compare_sun_hours_value, units.number(it, 1, locale))
                    } ?: "—",
                    icon = Icons.Default.WbSunny, tone = sunTone
                )
                CompareMetricTile(
                    label = stringResource(if (isHourly) R.string.compare_hour_agreement else R.string.compare_agreement),
                    value = agreement?.let { "$it %" } ?: "—", icon = Icons.Default.CheckCircle,
                    tone = agreement?.let { confidenceColor(it) } ?: colors.onSurfaceVariant,
                    progress = agreement?.let { it / 100f }, barTag = "compare_agreement_bar_$cityId"
                )
                CompareMetricTile(
                    label = stringResource(if (isHourly) R.string.compare_uv_hour else R.string.compare_uv_max),
                    value = uvValid?.let { stringResource(R.string.uv_index_value, it) } ?: "—",
                    icon = Icons.Default.LightMode,
                    tone = uvTone ?: colors.onSurfaceVariant,
                    progress = uvValid?.let { (it / 11.0).coerceIn(0.0, 1.0).toFloat() },
                    valueTag = "compare_uv_$cityId",
                    barTag = "compare_uv_bar_$cityId"
                )
            }
        }
    }
}

@Composable
private fun CompareMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Color,
    progress: Float? = null,
    valueTag: String? = null,
    barTag: String? = null,
    large: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val normalized = progress?.takeIf(Float::isFinite)?.coerceIn(0f, 1f)
    Column(
        modifier = Modifier.fillMaxWidth().height(METRIC_ROW_HEIGHT)
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = tone)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            value,
            modifier = if (valueTag == null) Modifier else Modifier.testTag(valueTag),
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (value == "—") colors.onSurfaceVariant else if (large) colors.onSurface else tone
        )
        if (normalized != null) {
            Box(Modifier.fillMaxWidth().height(4.dp)
                .background(colors.surfaceContainerHighest, CircleShape)) {
                Box(
                    Modifier.fillMaxWidth(normalized).fillMaxHeight()
                        .background(tone, CircleShape)
                        .then(if (barTag == null) Modifier else Modifier.testTag(barTag))
                )
            }
        } else {
            Box(Modifier.height(4.dp))
        }
    }
}
