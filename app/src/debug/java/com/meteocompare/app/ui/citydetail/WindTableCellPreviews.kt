package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

/** Vérifie visuellement la cellule de vent commune aux tableaux horaire et quotidien. */
@MeteoComponentPreview
@Composable
private fun WindTableCellPreview() {
    MeteoPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(80.dp)) {
                WindTableCellContent(
                    text = "42 km/h",
                    secondaryText = "R 65",
                    style = null,
                    directionDegrees = 225,
                    isWindCell = true
                )
            }
            Box(Modifier.width(80.dp)) {
                WindTableCellContent(
                    text = "28 mph",
                    secondaryText = "G 41",
                    style = null,
                    directionDegrees = 45,
                    isWindCell = true
                )
            }
        }
    }
}
