package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.core.util.windCardinal
import com.meteocompare.app.ui.components.WindArrow

/** Tag utilisé par les tests de non-régression des cellules vent. */
internal const val TAG_WIND_DETAIL_VALUE_CELL = "wind_detail_value_cell"

/**
 * Affichage commun aux tableaux quotidien et horaire.
 *
 * La V7 plaçait direction et vitesse côte à côte dans une colonne de 80 dp :
 * pour les grandes valeurs, la direction débordait visuellement vers l'échéance
 * précédente et la vitesse pouvait revenir à la ligne.
 *
 * Les trois informations sont maintenant centrées verticalement dans LEUR
 * cellule : direction, vitesse, puis rafales. Les hauteurs de ligne compactes
 * permettent de conserver les lignes de tableau à 56 dp.
 */
@Composable
internal fun WindTableCellContent(
    text: String,
    secondaryText: String?,
    style: ValueStyle?,
    directionDegrees: Int?,
    isWindCell: Boolean
) {
    val hasDirection = directionDegrees != null
    Column(
        modifier = Modifier.fillMaxWidth().then(
            if (isWindCell) Modifier.testTag(TAG_WIND_DETAIL_VALUE_CELL) else Modifier
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (directionDegrees != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WindArrow(directionDegrees = directionDegrees, size = 10.dp)
                windCardinal(directionDegrees, LocalConfiguration.current.locales[0])?.let { cardinal ->
                    Text(
                        text = cardinal,
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = "tnum",
                lineHeight = if (hasDirection) 18.sp else MaterialTheme.typography.bodySmall.lineHeight
            ),
            color = style?.color ?: Color.Unspecified,
            fontWeight = style?.fontWeight,
            maxLines = if (isWindCell) 1 else Int.MAX_VALUE,
            softWrap = !isWindCell,
            overflow = if (isWindCell) TextOverflow.Ellipsis else TextOverflow.Clip
        )
        secondaryText?.let { secondary ->
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    lineHeight = if (hasDirection) 13.sp else MaterialTheme.typography.labelSmall.lineHeight
                ),
                color = style?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
