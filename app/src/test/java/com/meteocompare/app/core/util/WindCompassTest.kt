package com.meteocompare.app.core.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindCompassTest {
    @Test fun cardinals_followMeteorologicalOrigin() {
        val en = Locale.ENGLISH
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW").forEachIndexed { index, name ->
            assertEquals(name, windCardinal(index * 45, en))
        }
        assertEquals("N", windCardinal(360, en))
        assertEquals("N", windCardinal(337 + 23, en))
        assertEquals("NE", windCardinal(23, en))
        assertEquals("N", windCardinal(22, en))
    }

    @Test fun west_isLocalizedAndMissingValuesStayMissing() {
        for (language in listOf("fr", "es", "it")) {
            val locale = Locale.forLanguageTag(language)
            assertEquals("O", windCardinal(270, locale))
            assertEquals("SO", windCardinal(225, locale))
            assertEquals("NO", windCardinal(315, locale))
        }
        for (language in listOf("en", "de")) {
            val locale = Locale.forLanguageTag(language)
            assertEquals("W", windCardinal(270, locale))
            assertEquals("SW", windCardinal(225, locale))
            assertEquals("NW", windCardinal(315, locale))
        }
        assertNull(windCardinal(null, Locale.FRENCH))
        assertNull(windCardinal(-1, Locale.ENGLISH))
        assertNull(windCardinal(361, Locale.ENGLISH))
    }

    @Test fun circularMeanHandlesNorthWrapAndOpposingVectors() {
        assertEquals(0, meanWindDirection(listOf(359, 1)))
        assertEquals(0, meanWindDirection(listOf(350, 10)))
        assertEquals(90, meanWindDirection(listOf(90, 90)))
        assertNull(meanWindDirection(listOf(0, 180)))
        assertNull(meanWindDirection(listOf(null, -10, 500)))
    }
}
