package com.meteocompare.app.ui.citylist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

class CityCardExpansionUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val score = DayConfidence(
        date = TestFixtures.today,
        tempMax = ConfidenceScore(60, 18.0, 22.0, 20.0, 1.5, 5),
        tempMin = null,
        precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
        windMax = ConfidenceScore(70, 10.0, 14.0, 12.0, 1.0, 5)
    )
    private val paris = CityCardState(
        TestFixtures.paris,
        ForecastState.Loaded(
            today = score,
            currentTemp = 19.0,
            currentCondition = WeatherCondition.CLEAR,
            currentCloudCover = 67,
            next12hTemps = listOf(19.0, 18.0, 17.0, 16.0)
        )
    )
    private val lyon = CityCardState(TestFixtures.lyon, ForecastState.Loading)

    private fun card(cityId: String) = composeRule.onNodeWithTag("$TAG_CITY_CARD$cityId")

    private fun assertExpanded(cityId: String, value: Boolean) {
        card(cityId).assert(SemanticsMatcher.expectValue(CityCardExpandedKey, value))
    }

    @Test fun single_city_stays_expanded_and_opens_details_on_first_tap() {
        var opened: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris),
                    onCityClick = { opened = it },
                    onRemove = {},
                    onRetry = {}
                )
            }
        }

        assertExpanded(TestFixtures.paris.id, true)
        card(TestFixtures.paris.id).performClick()
        composeRule.runOnIdle { assertEquals(TestFixtures.paris.id, opened) }
    }

    @Test fun collapsed_city_shows_condition_temperatures_rain_wind_cloud_and_convergence() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris, lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }

        assertExpanded(TestFixtures.paris.id, false)
        assertExpanded(TestFixtures.lyon.id, true)
        composeRule.onNodeWithTag("$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.paris.id}")
            .assertIsDisplayed()
        composeRule.onNodeWithText("19°", useUnmergedTree = true).assertExists()
        // The future temperature is the same ~H+3 trend as on the full card.
        composeRule.onNodeWithText("↓ 16°", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("76%", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("10–14 km/h", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("67%", useUnmergedTree = true).assertExists()

        val agreement = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_AGREEMENT${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val condition = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CONDITION${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val temperatures = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_TEMPERATURES${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val conditionLabel = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CONDITION_LABEL${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val metrics = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        assertTrue("Agreement must be on the header row", agreement.bottom <= condition.top)
        assertTrue("Condition should keep a square icon background", kotlin.math.abs(condition.width - condition.height) < 1f)
        assertTrue("Current and upcoming temperatures must be beside the condition", kotlin.math.abs(condition.center.y - temperatures.center.y) < 1f)
        assertTrue("Temperatures must follow the weather label", temperatures.left >= conditionLabel.right)
        assertTrue(
            "Temperatures should sit directly after the label, not at the card's right edge",
            temperatures.left - conditionLabel.right <= with(composeRule.density) { 10.dp.toPx() }
        )
        assertTrue("Three weather metrics must be below the condition", metrics.top >= condition.bottom)

        // The three metrics occupy identical cells instead of being weighted by
        // their content lengths; the wind must stay centered in the middle.
        val rain = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_RAIN${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val wind = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_WIND${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val clouds = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CLOUD${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The three weather metrics must have equal widths",
            abs(rain.width - wind.width) <= 1f && abs(wind.width - clouds.width) <= 1f
        )
        assertTrue(
            "Weather metrics must be horizontally distributed evenly",
            abs((wind.center.x - rain.center.x) - (clouds.center.x - wind.center.x)) <= 2f
        )
        assertTrue(
            "Weather metrics must share one baseline row",
            abs(rain.center.y - wind.center.y) <= 1f && abs(wind.center.y - clouds.center.y) <= 1f
        )
        val compactBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        val fullBounds = card(TestFixtures.lyon.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "A compact card should be 32 dp narrower than a full card",
            abs((fullBounds.width - compactBounds.width) -
                with(composeRule.density) { 32.dp.toPx() }) <= with(composeRule.density) { 2.dp.toPx() }
        )
        assertTrue(
            "Compact and full cards must share the same right edge",
            abs(compactBounds.right - fullBounds.right) <= with(composeRule.density) { 1.dp.toPx() }
        )
        assertTrue(
            "The compact card's inset should be on the left",
            compactBounds.left > fullBounds.left
        )
        // Only the expanded city renders its full forecast; the compressed card
        // must not keep the three-column temperature range visible.
        composeRule.onNodeWithText("18–22", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun compact_card_keeps_placeholders_when_forecast_data_is_missing() {
        val partial = paris.copy(
            forecast = ForecastState.Loaded(
                today = score,
                currentTemp = null,
                currentCondition = null,
                currentCloudCover = null
            )
        )
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(partial, lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, false)
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CONDITION${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_TEMPERATURES${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HOME_TEMPERATURE_TREND, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test fun first_tap_expands_without_navigation_and_second_opens_details() {
        var opened: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris, lyon),
                    onCityClick = { opened = it }, onRemove = {}, onRetry = {}
                )
            }
        }

        card(TestFixtures.paris.id).performClick()
        composeRule.runOnIdle { assertEquals(null, opened) }
        assertExpanded(TestFixtures.paris.id, true)
        assertExpanded(TestFixtures.lyon.id, false)
        composeRule.onNodeWithTag("$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.lyon.id}")
            .assertIsDisplayed()

        card(TestFixtures.paris.id).performClick()
        composeRule.runOnIdle { assertEquals(TestFixtures.paris.id, opened) }
    }

    @Test fun expand_and_collapse_animate_width_without_instant_jumps() {
        // Isolate a card from LazyColumn placement animations. If width is
        // snapped rather than animated, the halfway assertion will fail.
        composeRule.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        composeRule.setContent {
            MeteoCompareTheme {
                CityCard(
                    state = paris,
                    expanded = expanded.value,
                    onClick = { expanded.value = !expanded.value },
                    onRemove = {},
                    onRetry = {}
                )
            }
        }
        val collapsedBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { expanded.value = true }
        composeRule.mainClock.advanceTimeBy(130)
        val expandingBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(350)
        val expandedBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expansion should smoothly increase width",
            expandingBounds.width > collapsedBounds.width &&
                expandingBounds.width < expandedBounds.width
        )
        assertTrue(
            "Expansion must keep the card's right edge fixed throughout",
            abs(collapsedBounds.right - expandingBounds.right) <= 1f &&
                abs(expandingBounds.right - expandedBounds.right) <= 1f
        )
        assertTrue(
            "Expansion should smoothly increase height",
            expandingBounds.height > collapsedBounds.height &&
                expandingBounds.height < expandedBounds.height
        )

        composeRule.runOnIdle { expanded.value = false }
        composeRule.mainClock.advanceTimeBy(130)
        val collapsingBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(350)
        val collapsedAgainBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Collapse should smoothly decrease width",
            collapsingBounds.width < expandedBounds.width &&
                collapsingBounds.width > collapsedAgainBounds.width
        )
        assertTrue(
            "Collapse must keep the card's right edge fixed throughout",
            abs(expandedBounds.right - collapsingBounds.right) <= 1f &&
                abs(collapsingBounds.right - collapsedAgainBounds.right) <= 1f
        )
        assertTrue(
            "Collapse should smoothly decrease height",
            collapsingBounds.height < expandedBounds.height &&
                collapsingBounds.height > collapsedAgainBounds.height
        )
        assertTrue(
            "Compact dimensions should return to their original values",
            abs(collapsedAgainBounds.width - collapsedBounds.width) <= 1f &&
                abs(collapsedAgainBounds.height - collapsedBounds.height) <= 1f
        )
    }

    @Test fun newly_added_city_takes_expansion_and_removal_falls_back() {
        val cities = mutableStateOf(listOf(paris))
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = cities.value,
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, true)

        composeRule.runOnIdle { cities.value = listOf(paris, lyon) }
        assertExpanded(TestFixtures.paris.id, false)
        assertExpanded(TestFixtures.lyon.id, true)

        // Re-select Paris, then add a third city: the new favorite wins.
        card(TestFixtures.paris.id).performClick()
        assertExpanded(TestFixtures.paris.id, true)
        val brest = CityCardState(
            TestFixtures.paris.copy(id = "brest", name = "Brest"),
            ForecastState.Loading
        )
        composeRule.runOnIdle { cities.value = listOf(paris, lyon, brest) }
        assertExpanded("brest", true)
        assertExpanded(TestFixtures.paris.id, false)

        composeRule.runOnIdle { cities.value = listOf(paris, lyon) }
        assertExpanded(TestFixtures.lyon.id, true)
    }

    @Test fun collapsed_error_does_not_hide_error_and_can_be_expanded_to_retry() {
        val error = CityCardState(TestFixtures.paris, ForecastState.Error("Réseau indisponible"))
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(error, lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, false)
        composeRule.onNodeWithText("Réseau indisponible", useUnmergedTree = true).assertIsDisplayed()
        card(TestFixtures.paris.id).performClick()
        assertExpanded(TestFixtures.paris.id, true)
        composeRule.onNodeWithText("Réseau indisponible", useUnmergedTree = true).assertIsDisplayed()
    }
}
