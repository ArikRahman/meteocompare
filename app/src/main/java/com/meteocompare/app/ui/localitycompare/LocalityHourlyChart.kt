package com.meteocompare.app.ui.localitycompare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.usecase.LocalityHourMetrics
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A single local wall-clock hour across cities, not a shared UTC instant. */
internal enum class CompareChartMetric(val labelRes: Int) {
    TEMPERATURE(R.string.compare_hour_temperature),
    RAIN(R.string.compare_hour_rain),
    RAIN_RISK(R.string.compare_rain_probability),
    CLOUDS(R.string.compare_hour_cloud_cover),
    WIND(R.string.compare_hour_wind),
    SUNSHINE(R.string.compare_sunshine),
    AGREEMENT(R.string.compare_hour_agreement),
    UV(R.string.compare_uv_hour);

    fun rawValue(hour: LocalityHourMetrics?): Double? = when (this) {
        TEMPERATURE -> hour?.temperatureC
        RAIN -> hour?.precipitationMm
        RAIN_RISK -> hour?.rainProbabilityPercent?.toDouble()
        CLOUDS -> hour?.cloudCoverPercent?.toDouble()
        WIND -> hour?.windKmh
        SUNSHINE -> hour?.estimatedSunHours
        AGREEMENT -> hour?.agreementPercent?.toDouble()
        UV -> hour?.uvIndex
    }?.takeIf(Double::isFinite)

    fun displayValue(hour: LocalityHourMetrics?, units: WeatherUnits, locale: Locale): String {
        val value = rawValue(hour) ?: return "—"
        return when (this) {
            TEMPERATURE -> units.temp(value, locale = locale)
            RAIN -> units.rain(value, locale)
            WIND -> units.speed(value, locale = locale)
            RAIN_RISK, CLOUDS, AGREEMENT -> "${value.roundToInt()} %"
            SUNSHINE -> "${units.number(value, 1, locale)} h"
            UV -> units.number(value, 1, locale)
        }
    }

    /** Convert values before drawing so UK mph / °C and other profiles agree with labels. */
    fun plotValue(hour: LocalityHourMetrics?, units: WeatherUnits): Double? = rawValue(hour)?.let {
        when (this) {
            TEMPERATURE -> units.temperature(it)
            RAIN -> units.precipitation(it)
            WIND -> units.wind(it)
            else -> it
        }
    }
}

internal data class ChartRange(val lower: Double, val upper: Double, val hasData: Boolean)

/** Immutable y-axis extent computed only when the plotted metric/data changes. */
internal fun chartRange(points: List<List<Double?>>): ChartRange {
    var lowest = Double.POSITIVE_INFINITY
    var highest = Double.NEGATIVE_INFINITY
    for (city in points) for (value in city) {
        if (value != null && value.isFinite()) {
            if (value < lowest) lowest = value
            if (value > highest) highest = value
        }
    }
    if (!lowest.isFinite()) return ChartRange(0.0, 1.0, false)
    val span = (highest - lowest).takeIf { it > 0.001 } ?: 1.0
    return ChartRange(lowest - span * .12, highest + span * .12, true)
}

internal data class HourChartCity(
    val id: String,
    val name: String,
    /** Exactly 24 slots; null is a genuine gap (including DST skipped hours). */
    val hours: List<LocalityHourMetrics?>
)

/** Pure chart preparation, independent of Compose and network I/O. */
internal fun chartPoints(
    cities: List<HourChartCity>, metric: CompareChartMetric, units: WeatherUnits
): List<List<Double?>> = cities.map { city ->
    (0..23).map { hour -> metric.plotValue(city.hours.getOrNull(hour), units) }
}

@Composable
internal fun LocalityHourlyChart(
    cities: List<HourChartCity>,
    units: WeatherUnits,
    locale: Locale,
    hourFormatter: DateTimeFormatter,
    selectedHour: Int,
    onHourSelected: (Int) -> Unit,
    metric: CompareChartMetric = CompareChartMetric.TEMPERATURE,
    onMetricSelected: (CompareChartMetric) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.colorScheme
    val lineColors = listOf(palette.primary, palette.tertiary, palette.secondary)
    val points = remember(cities, units, metric) { chartPoints(cities, metric, units) }
    // Dragging the time cursor must not rescan every plotted point for min/max.
    val range = remember(points) { chartRange(points) }
    val lower = range.lower
    val upper = range.upper

    Surface(modifier = modifier.fillMaxWidth().testTag("compare_hourly_graph"),
        color = palette.surfaceContainerLow, shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CompareChartMetric.entries) { option ->
                    FilterChip(
                        selected = metric == option,
                        onClick = { onMetricSelected(option) },
                        label = { Text(androidx.compose.ui.res.stringResource(option.labelRes)) },
                        modifier = Modifier.testTag("compare_chart_metric_${option.name.lowercase()}")
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cities.forEachIndexed { index, city ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.padding(top = 4.dp).size(9.dp)
                                .background(lineColors[index % lineColors.size], RoundedCornerShape(50))
                        )
                        Text(city.name, style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurface, maxLines = 1)
                    }
                }
            }
            if (!range.hasData) {
                Text(androidx.compose.ui.res.stringResource(R.string.compare_no_data_for_hour),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = palette.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            val gridColor = palette.outlineVariant
            val selectionColor = palette.onSurface
            val baseModifier = Modifier.fillMaxWidth().height(196.dp)
                .testTag("compare_hour_chart")
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(selectedHour.toFloat(), 0f..23f, 22)
                    setProgress { newValue ->
                        onHourSelected(newValue.roundToInt().coerceIn(0, 23))
                        true
                    }
                }
                .pointerInput(cities, metric) {
                    detectTapGestures { offset ->
                        val startX = 22.dp.toPx()
                        val rightX = size.width - 14.dp.toPx()
                        if (rightX > startX) {
                            onHourSelected(chartHourFromX(offset.x, startX, rightX))
                        }
                    }
                }
                .pointerInput(cities, metric) {
                    detectHorizontalDragGestures { change, _ ->
                        val startX = 22.dp.toPx()
                        val rightX = size.width - 14.dp.toPx()
                        if (rightX > startX) {
                            onHourSelected(chartHourFromX(change.position.x, startX, rightX))
                        }
                        change.consume()
                    }
                }
            Canvas(baseModifier) {
                val left = 22.dp.toPx()
                val right = size.width - 14.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = size.height - 12.dp.toPx()
                if (right <= left || bottom <= top) return@Canvas
                fun x(hour: Int) = left + (right - left) * hour / 23f
                fun y(value: Double) = (bottom - ((value - lower) / (upper - lower)).toFloat() * (bottom - top))
                repeat(4) { index ->
                    val gridY = top + (bottom - top) * index / 3f
                    drawLine(gridColor, Offset(left, gridY), Offset(right, gridY), strokeWidth = 1.dp.toPx())
                }
                points.forEachIndexed { index, valuesAtHours ->
                    val tone = lineColors[index % lineColors.size]
                    var segment = Path()
                    var started = false
                    fun flush() {
                        if (started) drawPath(segment, tone, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        segment = Path()
                        started = false
                    }
                    for (hour in 0..23) {
                        val value = valuesAtHours[hour]
                        if (value == null) { flush(); continue }
                        val px = x(hour)
                        val py = y(value)
                        if (!started) { segment.moveTo(px, py); started = true }
                        else segment.lineTo(px, py)
                    }
                    flush()
                    if (valuesAtHours.count { it != null } <= 2) {
                        valuesAtHours.forEachIndexed { hour, value ->
                            if (value != null) drawCircle(tone, 3.dp.toPx(), Offset(x(hour), y(value)))
                        }
                    }
                    valuesAtHours.getOrNull(selectedHour)?.let { value ->
                        drawCircle(tone, radius = 4.dp.toPx(), center = Offset(x(selectedHour), y(value)))
                    }
                }
                drawLine(selectionColor.copy(alpha = .65f),
                    Offset(x(selectedHour), top), Offset(x(selectedHour), bottom),
                    strokeWidth = 1.4.dp.toPx())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18, 23).forEach { tick ->
                    Text(LocalTime.of(tick, 0).format(hourFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = LocalTime.of(selectedHour, 0).format(hourFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.onSurface,
                    modifier = Modifier.testTag("compare_selected_hour")
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.compare_chart_touch_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cities.forEachIndexed { index, city ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(city.name, style = MaterialTheme.typography.labelSmall,
                            color = lineColors[index % lineColors.size], maxLines = 1)
                        Text(metric.displayValue(city.hours.getOrNull(selectedHour), units, locale),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onSurface,
                            modifier = Modifier.testTag("compare_chart_value_${city.id}"))
                    }
                }
            }
        }
    }
}

