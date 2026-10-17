package com.meteocompare.app.ui.citylist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CityCardExpansionTest {
    @Test fun no_favorites_means_no_expanded_card() {
        assertNull(resolveExpandedCityId("paris", listOf("paris"), emptyList()))
    }

    @Test fun one_favorite_is_always_expanded() {
        assertEquals("paris", resolveExpandedCityId(null, emptyList(), listOf("paris")))
        assertEquals("paris", resolveExpandedCityId("removed", listOf("removed"), listOf("paris")))
    }

    @Test fun restoration_keeps_the_previously_expanded_city() {
        val ids = listOf("paris", "lyon", "brest")
        // Au premier chargement, tous les favoris sont déjà connus :
        // aucun ne doit être interprété comme un nouvel ajout.
        assertEquals("paris", resolveExpandedCityId("paris", ids, ids))
        assertEquals("brest", resolveExpandedCityId("deleted", ids, ids))
    }

    @Test fun initially_the_last_favorite_is_expanded() {
        assertEquals(
            "brest",
            resolveExpandedCityId(null, emptyList(), listOf("paris", "lyon", "brest"))
        )
    }

    @Test fun each_newly_added_city_becomes_expanded() {
        assertEquals(
            "lyon",
            resolveExpandedCityId("paris", listOf("paris"), listOf("paris", "lyon"))
        )
        assertEquals(
            "brest",
            resolveExpandedCityId("lyon", listOf("paris", "lyon"), listOf("paris", "lyon", "brest"))
        )
    }

    @Test fun manual_expansion_survives_weather_refresh_and_reordering() {
        val ids = listOf("paris", "lyon", "brest")
        assertEquals("paris", resolveExpandedCityId("paris", ids, ids))
        assertEquals("paris", resolveExpandedCityId("paris", ids, ids.reversed()))
    }

    @Test fun removing_the_expanded_city_falls_back_to_last_remaining() {
        assertEquals(
            "lyon",
            resolveExpandedCityId("brest", listOf("paris", "lyon", "brest"), listOf("paris", "lyon"))
        )
        assertEquals(
            "paris",
            resolveExpandedCityId("paris", listOf("paris", "lyon"), listOf("paris"))
        )
    }

    @Test fun tapping_a_collapsed_card_only_expands_then_open_details() {
        assertEquals(CityCardTapAction.EXPAND, cityCardTapAction("paris", "lyon"))
        assertEquals(CityCardTapAction.OPEN_DETAILS, cityCardTapAction("paris", "paris"))
    }
}
