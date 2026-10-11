package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

/** Debug-only coverage for both the full UV gauge and compact chronology label. */
@MeteoComponentPreview
@Composable
private fun UvIndexComponentsPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(0.8, 4.1, 6.3, 9.0, 11.5).forEach { index ->
                    UvIndexGauge(value = index)
                }
                UvIndexCompact(value = 5.4)
            }
        }
    }
}
