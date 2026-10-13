package com.meteocompare.app.core.locale

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTimeFormatTest {
    @Test
    fun `english uses am and pm independently of region`() {
        for (locale in listOf(Locale.US, Locale.UK, Locale.forLanguageTag("en-AU"))) {
            assertEquals("12 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hour(locale)))
            assertEquals("12 PM", LocalTime.NOON.format(WeatherTimeFormat.hour(locale)))
            assertEquals("12 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hourPadded(locale)))
            assertEquals("1 PM", LocalTime.of(13, 0).format(WeatherTimeFormat.hour(locale)))
            assertEquals("11:45 PM", LocalTime.of(23, 45).format(WeatherTimeFormat.clock(locale)))
            assertEquals("12:00 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.clock(locale)))
            assertEquals("12:00 PM", LocalTime.NOON.format(WeatherTimeFormat.clock(locale)))
        }
    }

    @Test
    fun `other supported languages stay on twenty four hour clock`() {
        for (locale in listOf(Locale.FRANCE, Locale.GERMANY, Locale.ITALY, Locale.forLanguageTag("es-ES"))) {
            assertEquals("0h", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hour(locale)))
            assertEquals("13h", LocalTime.of(13, 0).format(WeatherTimeFormat.hour(locale)))
            assertEquals("23:45", LocalTime.of(23, 45).format(WeatherTimeFormat.clock(locale)))
            assertEquals("00h", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hourPadded(locale)))
        }
    }

    @Test
    fun `formatting respects destination city timezone and daylight saving time`() {
        val zone = ZoneId.of("Europe/Paris")
        val beforeJump = Instant.parse("2026-03-29T00:00:00Z").atZone(zone)
        val afterJump = Instant.parse("2026-03-29T01:00:00Z").atZone(zone)
        assertEquals("1 AM", beforeJump.format(WeatherTimeFormat.hour(Locale.US)))
        assertEquals("3 AM", afterJump.format(WeatherTimeFormat.hour(Locale.US)))
        assertEquals("1h", beforeJump.format(WeatherTimeFormat.hour(Locale.FRANCE)))
        assertEquals("3h", afterJump.format(WeatherTimeFormat.hour(Locale.FRANCE)))
    }

    @Test
    fun `longer axis and date labels use same clock convention`() {
        val instant = Instant.parse("2026-10-13T23:30:00Z").atZone(ZoneId.of("UTC"))
        assertEquals("Tue 13 Oct · 11:30 PM", instant.format(WeatherTimeFormat.dateClock(Locale.US)))
        assertEquals("Tue 11 PM", instant.format(WeatherTimeFormat.dayHour(Locale.US)))
        assertEquals("23:30", instant.format(WeatherTimeFormat.clock(Locale.FRANCE)))
        assertEquals("Tue\n11:30 PM", instant.format(WeatherTimeFormat.dayClockLines(Locale.UK)))
        assertEquals("13/10 · 11:30 PM", instant.format(WeatherTimeFormat.shortDateClock(Locale.UK)))
        assertTrue(instant.format(WeatherTimeFormat.dateClock(Locale.FRANCE)).endsWith("23:30"))
    }
}
