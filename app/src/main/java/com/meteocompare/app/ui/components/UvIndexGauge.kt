package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.validUvIndexOrNull

private val uvColors = listOf(
    Color(0xFF36A667), Color(0xFFE2C83C), Color(0xFFF39738),
    Color(0xFFE65257), Color(0xFF8F53BA)
)

internal fun uvRiskLabelRes(value: Double): Int = when {
    value < 3 -> R.string.uv_risk_low
    value < 6 -> R.string.uv_risk_moderate
    value < 8 -> R.string.uv_risk_high
    value < 11 -> R.string.uv_risk_very_high
    else -> R.string.uv_risk_extreme
}

/** Shared UV gauge for home and today's summary. Absent values are not shown as zero. */
@Composable
fun UvIndexGauge(value: Double?, modifier: Modifier = Modifier) {
    val safeValue = value?.validUvIndexOrNull() ?: return
    val label = stringResource(uvRiskLabelRes(safeValue))
    val formattedValue = stringResource(R.string.uv_index_value, safeValue)
    val accessibility = stringResource(R.string.uv_accessibility, formattedValue, label)
    Column(
        modifier = modifier.testTag("uv_gauge").semantics(mergeDescendants = true) {
            contentDescription = accessibility
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.uv_index_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(formattedValue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(9.dp)) {
            Box(
                Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center)
                    .background(Brush.horizontalGradient(uvColors), RoundedCornerShape(50))
            )
            Box(
                Modifier.offset(x = (maxWidth - 9.dp) * (safeValue / 11.0).coerceIn(0.0, 1.0).toFloat())
                    .size(9.dp)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                    .align(Alignment.CenterStart)
            )
        }
    }
}

/** Readable small label placed immediately below the weather condition icon/text. */
@Composable
fun UvIndexCompact(value: Double?, modifier: Modifier = Modifier, small: Boolean = false) {
    val safeValue = value?.validUvIndexOrNull() ?: return
    val formatted = stringResource(R.string.uv_index_value, safeValue)
    val description = stringResource(
        R.string.uv_accessibility, formatted, stringResource(uvRiskLabelRes(safeValue))
    )
    Text(
        text = stringResource(R.string.uv_compact, formatted),
        modifier = modifier.testTag("uv_compact").semantics {
            contentDescription = description
        },
        style = if (small) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
            else MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}
