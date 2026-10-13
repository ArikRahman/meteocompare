package com.meteocompare.app.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetWeatherNightIconTest {
    @Test fun day_and_night_icons_differ_for_sun_bearing_conditions() {
        listOf(
            WeatherCondition.CLEAR,
            WeatherCondition.MAINLY_CLEAR,
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.RAIN_SHOWERS,
            WeatherCondition.SNOW_SHOWERS
        ).forEach { condition ->
            val day = WidgetWeatherIconRenderer.render(condition, 96, isNight = false)
            val night = WidgetWeatherIconRenderer.render(condition, 96, isNight = true)
            val dayPixels = IntArray(day.width * day.height)
            val nightPixels = IntArray(night.width * night.height)
            day.getPixels(dayPixels, 0, day.width, 0, 0, day.width, day.height)
            night.getPixels(nightPixels, 0, night.width, 0, 0, night.width, night.height)
            assertFalse("Night should differ from day for $condition", dayPixels.contentEquals(nightPixels))
            assertTrue(nightPixels.any { it ushr 24 != 0 })
            day.recycle()
            night.recycle()
        }
    }

    @Test fun overcast_remains_the_same_at_night() {
        val day = WidgetWeatherIconRenderer.render(WeatherCondition.OVERCAST, 48, false)
        val night = WidgetWeatherIconRenderer.render(WeatherCondition.OVERCAST, 48, true)
        assertTrue(day.sameAs(night))
        day.recycle()
        night.recycle()
    }
}
