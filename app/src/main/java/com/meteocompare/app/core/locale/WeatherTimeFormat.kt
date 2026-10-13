package com.meteocompare.app.core.locale

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Canonical clock formatting for MeteoCompare's selected application language.
 *
 * English uses a 12-hour clock with uppercase AM/PM regardless of device
 * clock preferences or English region (en-US, en-GB, en-AU, …). French, German,
 * Spanish and Italian keep the app's existing 24-hour presentation.
 *
 * This class only formats local times. Callers first convert Instants into the
 * destination city's timezone; it must never change or infer that timezone.
 */
object WeatherTimeFormat {
    private fun english(locale: Locale) = locale.language.equals("en", ignoreCase = true)

    private fun englishHour(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("h ")
        // Locale.UK / en-AU otherwise output lowercase "am" and "pm" on Android/JVM.
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(locale)

    private fun englishClock(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("h:mm ")
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(locale)

    /** Compact labels for hourly forecasts, timelines, charts and widgets. */
    fun hour(locale: Locale): DateTimeFormatter = if (english(locale)) {
        englishHour(locale)
    } else DateTimeFormatter.ofPattern("H'h'", locale)

    /** Hour headings that historically pad 00–09 in 24-hour locales. */
    fun hourPadded(locale: Locale): DateTimeFormatter = if (english(locale)) {
        englishHour(locale)
    } else DateTimeFormatter.ofPattern("HH'h'", locale)

    /** Exact clock time for sunrise/sunset, radar, tides and notifications. */
    fun clock(locale: Locale): DateTimeFormatter = if (english(locale)) {
        englishClock(locale)
    } else DateTimeFormatter.ofPattern("HH:mm", locale)

    /** Selected point on Chart View. */
    fun dateClock(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("EEE d MMM")
        .appendLiteral(" · ")
        .append(clock(locale))
        .toFormatter(locale)

    /** Chart and vigilance day-hour axes. */
    fun dayHour(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("EEE")
        .appendLiteral(' ')
        .append(hour(locale))
        .toFormatter(locale)

    /** Marine chart axes with day on the first line. */
    fun dayClockLines(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("EEE")
        .appendLiteral('\n')
        .append(clock(locale))
        .toFormatter(locale)

    /** Tide details use the localized date but the same application clock. */
    fun dateTime(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendLocalized(FormatStyle.MEDIUM, null)
        .appendLiteral(" · ")
        .append(clock(locale))
        .toFormatter(locale)

    /** Compact date and clock in official weather alert updates. */
    fun shortDateClock(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("dd/MM")
        .appendLiteral(" · ")
        .append(clock(locale))
        .toFormatter(locale)
}
