package com.meteocompare.app.ui.components

import com.meteocompare.app.domain.model.validUvIndexOrNull
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvIndexRiskTest {
    @Test fun `risk thresholds match index values and translated labels`() {
        val cases = listOf(
            0.0 to UvRisk.LOW, 2.99 to UvRisk.LOW,
            3.0 to UvRisk.MODERATE, 5.99 to UvRisk.MODERATE,
            6.0 to UvRisk.HIGH, 7.99 to UvRisk.HIGH,
            8.0 to UvRisk.VERY_HIGH, 10.99 to UvRisk.VERY_HIGH,
            11.0 to UvRisk.EXTREME, 14.0 to UvRisk.EXTREME
        )
        cases.forEach { (value, expected) ->
            assertEquals("UV $value", expected, uvRisk(value))
            assertEquals(expected.labelRes, uvRiskLabelRes(value))
        }
    }

    @Test fun `missing non finite and negative values are never drawn as UV`() {
        assertNull(Double.NaN.validUvIndexOrNull())
        assertNull(Double.POSITIVE_INFINITY.validUvIndexOrNull())
        assertNull((-1.0).validUvIndexOrNull())
        assertTrue(0.0.validUvIndexOrNull() == 0.0)
    }

    @Test fun `palette changes by risk tier and adapts to dark theme`() {
        assertEquals(5, UvRisk.entries.map { it.barColor }.distinct().size)
        assertEquals(5, UvRisk.entries.map { it.lightTextColor }.distinct().size)
        assertEquals(UvRisk.HIGH.barColor, uvIntensityColor(7.0, darkBackground = true))
        assertEquals(UvRisk.HIGH.lightTextColor, uvIntensityColor(7.0, darkBackground = false))
        UvRisk.entries.forEach { risk ->
            // Contraste des étiquettes colorées sur fond clair (WCAG AA, texte normal).
            val contrast = 1.05f / (risk.lightTextColor.luminance() + 0.05f)
            assertTrue("Contraste UV ${risk.name}: $contrast", contrast >= 4.5f)
        }
    }
}
