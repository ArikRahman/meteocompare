package com.meteocompare.app.ui.localitycompare

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalitySelectionTest {
    @Test fun defaultsToTwoFavoritesWithoutDuplicates() {
        assertEquals(listOf("a", "b"), restoreComparedCities(listOf("a", "b", "c"), emptyList()))
        assertEquals(listOf("a", "b"), restoreComparedCities(listOf("a", "b", "c"), listOf("a", "a")))
    }

    @Test fun removalOfFavoriteReplacesItWithAnother() {
        assertEquals(listOf("b", "c"), restoreComparedCities(listOf("b", "c", "d"), listOf("a", "b")))
    }

    @Test fun selectionStaysBetweenTwoAndThree() {
        assertEquals(listOf("a", "b"), toggleComparedCity(listOf("a", "b"), "a"))
        assertEquals(listOf("a", "b", "c"), toggleComparedCity(listOf("a", "b"), "c"))
        assertEquals(listOf("a", "b", "c"), toggleComparedCity(listOf("a", "b", "c"), "d"))
        assertEquals(listOf("a", "c"), toggleComparedCity(listOf("a", "b", "c"), "b"))
    }
}
