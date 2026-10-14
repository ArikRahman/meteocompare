package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.domain.model.ClockFormatPreference
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

class SunTimesRowTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setContentWith24HourClock(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalClockFormatPreference provides ClockFormatPreference.HOUR_24) {
                content()
            }
        }
    }

    @Test
    fun normal_times_are_visible_and_accessible() {
        setContentWith24HourClock { SunTimesRow(LocalTime.of(6, 12), LocalTime.of(21, 45)) }
        composeRule.onNodeWithText("06:12").assertIsDisplayed()
        composeRule.onNodeWithText("21:45").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SUN_TIMES_ROW)
            .assertContentDescriptionContains("06:12", substring = true)
            .assertContentDescriptionContains("21:45", substring = true)
    }

    @Test
    fun missing_times_are_rendered_as_dashes_without_crashing() {
        setContentWith24HourClock { SunTimesRow(null, null) }
        composeRule.onAllNodesWithText("—").assertCountEquals(2)
    }

    @Test
    fun partial_data_keeps_the_available_time() {
        setContentWith24HourClock { SunTimesRow(null, LocalTime.of(18, 0)) }
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("18:00").assertIsDisplayed()
    }

    @Test
    fun explicit_clock_choice_changes_sunrise_and_sunset_without_changing_values() {
        val mode = mutableStateOf(ClockFormatPreference.HOUR_24)
        composeRule.setContent {
            CompositionLocalProvider(LocalClockFormatPreference provides mode.value) {
                SunTimesRow(LocalTime.of(6, 12), LocalTime.of(21, 45))
            }
        }
        composeRule.onNodeWithText("06:12").assertIsDisplayed()
        composeRule.runOnIdle { mode.value = ClockFormatPreference.HOUR_12 }
        composeRule.onNodeWithText("6:12 AM").assertIsDisplayed()
        composeRule.onNodeWithText("9:45 PM").assertIsDisplayed()
        composeRule.onNodeWithText("06:12").assertDoesNotExist()
    }
}
