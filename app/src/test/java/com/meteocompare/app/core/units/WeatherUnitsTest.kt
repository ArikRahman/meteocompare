package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.UnitSystem
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WeatherUnitsTest {
    private val metric = WeatherUnits()
    private val imperial = WeatherUnits(UnitSystem.IMPERIAL)

    @Test fun `metric is default including absent and unknown persisted values`() {
        listOf(null, "", "INVALID", "IMPERIAL").forEach {
            assertEquals(UnitSystem.METRIC, UnitSystem.fromStorage(it))
        }
        UnitSystem.entries.forEach { assertEquals(it, UnitSystem.fromStorage(it.storageKey)) }
        assertEquals(UnitSystem.METRIC, metric.system)
    }

    @Test fun `UK preset mixes Celsius and millimetres with miles and mph`() {
        val uk = WeatherUnits(UnitSystem.UK_MIXED)
        assertEquals("°C", uk.temperatureUnit)
        assertEquals("°", uk.temperatureSuffix)
        assertEquals("mph", uk.windUnit)
        assertEquals("mi", uk.distanceUnit)
        assertEquals("mm", uk.precipitationUnit)
        assertEquals("m", uk.heightUnit)
        assertEquals("hPa", uk.label(WeatherUnit.PRESSURE))
        assertEquals(0.0, uk.temperature(0.0), 0.0)
        assertEquals(2.0, uk.temperatureDelta(2.0), 0.0)
        assertEquals(10.0, uk.wind(16.09344), 1e-10)
        assertEquals(10.0, uk.distance(16.09344), 1e-10)
        assertEquals(25.4, uk.precipitation(25.4), 0.0)
        assertEquals(10.0, uk.height(10.0), 0.0)
        assertEquals(1013.25, uk.pressure(1013.25), 0.0)
        assertEquals("20°", uk.temp(20.0, locale = Locale.UK))
        assertEquals("10 mph", uk.speed(16.09344, Locale.UK))
        assertEquals("25.4 mm", uk.rain(25.4, Locale.UK))
        assertEquals("10.0 mi", uk.format(16.09344, WeatherUnit.DISTANCE, 1, Locale.UK))
        assertEquals("10.0 m", uk.format(10.0, WeatherUnit.HEIGHT, 1, Locale.UK))
        assertEquals("1013 hPa", uk.format(1013.25, WeatherUnit.PRESSURE, locale = Locale.UK))
    }

    @Test fun `UK preset persists without depending on app language`() {
        assertEquals(UnitSystem.UK_MIXED, UnitSystem.fromStorage("uk_mixed"))
        val uk = WeatherUnits(UnitSystem.fromStorage("uk_mixed"))
        for (locale in listOf(Locale.UK, Locale.FRANCE, Locale.GERMANY, Locale.ITALY)) {
            assertEquals("mph", uk.windUnit)
            assertEquals("°C", uk.temperatureUnit)
            assertEquals("mm", uk.precipitationUnit)
            assertEquals("mi", uk.distanceUnit)
            assertTrue(uk.speed(16.09344, locale).endsWith(" mph"))
        }
    }

    @Test fun `absolute temperature conversion includes the offset exactly once`() {
        listOf(-40.0 to -40.0, 0.0 to 32.0, 20.0 to 68.0, 100.0 to 212.0).forEach { (c, f) ->
            assertEquals(f, imperial.temperature(c), 1e-10)
            assertEquals(c, metric.temperature(c), 0.0)
        }
    }

    @Test fun `bias spread MAE and thresholds are temperature differences`() {
        assertEquals(0.0, imperial.temperatureDelta(0.0), 0.0)
        assertEquals(3.6, imperial.temperatureDelta(2.0), 1e-10)
        assertEquals(-3.6, imperial.temperatureDelta(-2.0), 1e-10)
        assertEquals("3.6 °F", imperial.format(2.0, WeatherUnit.TEMPERATURE, 1, Locale.US, delta = true))
        assertEquals("35.6 °F", imperial.format(2.0, WeatherUnit.TEMPERATURE, 1, Locale.US))
        for (low in -50..45 step 5) {
            val high = low + 5.25
            assertEquals(imperial.temperatureDelta(high - low),
                imperial.temperature(high) - imperial.temperature(low.toDouble()), 1e-10)
        }
    }

    @Test fun `wind rainfall marine heights and distances use exact factors`() {
        assertEquals(10.0, imperial.wind(16.09344), 1e-10)
        assertEquals(1.0, imperial.precipitation(25.4), 1e-10)
        assertEquals(10.0, imperial.height(3.048), 1e-10)
        assertEquals(10.0, imperial.distance(16.09344), 1e-10)
        assertEquals("10 mph", imperial.speed(16.09344, Locale.US))
        assertEquals("1.00 in", imperial.rain(25.4, Locale.US))
        assertEquals("10.0 ft", imperial.format(3.048, WeatherUnit.HEIGHT, 1, Locale.US))
        assertEquals("10.0 mi", imperial.format(16.09344, WeatherUnit.DISTANCE, 1, Locale.US))
        assertEquals("in/h", imperial.label(WeatherUnit.PRECIPITATION_RATE))
    }

    @Test fun `rounding happens after conversion and handles negative temperatures`() {
        assertEquals("33°F", imperial.temp(0.4, locale = Locale.US))
        assertEquals("-40°F", imperial.temp(-40.0, locale = Locale.US))
        assertEquals("0°", metric.temp(-0.1, locale = Locale.US))
        assertEquals("10 mph", imperial.speed(15.9, Locale.US))
    }

    @Test fun `missing and non finite values never crash or masquerade as zero`() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            for (units in listOf(metric, imperial)) {
                assertEquals("—", units.temp(value))
                assertEquals("—", units.rain(value))
                assertEquals("—", units.speed(value))
                assertEquals("—", units.format(value, WeatherUnit.HEIGHT))
            }
        }
    }

    @Test fun `trace precipitation remains distinguishable from dry weather`() {
        assertEquals("0.00 in", imperial.rain(0.0, Locale.US))
        assertEquals("<0.01 in", imperial.rain(0.05, Locale.US))
        assertEquals("0.01 in", imperial.rain(0.2, Locale.US))
        assertEquals("0.1 mm", metric.rain(0.05, Locale.US))
        assertEquals("1,00 in", imperial.rain(25.4, Locale.FRANCE))
        assertEquals("3,6 °F", imperial.format(2.0, WeatherUnit.TEMPERATURE, 1, Locale.FRANCE, delta = true))
    }

    @Test fun `non dimensional values and direction angles are unchanged`() {
        assertEquals(82.0, imperial.convert(82.0, WeatherUnit.PERCENT), 0.0)
        assertEquals(9.0, imperial.convert(9.0, WeatherUnit.SECONDS), 0.0)
        // Angles are rendered by their existing direction formatter, never as temperature.
        assertEquals("82%", imperial.format(82.0, WeatherUnit.PERCENT, locale = Locale.US))
        assertEquals("9 s", imperial.format(9.0, WeatherUnit.SECONDS, locale = Locale.US))
    }

    @Test fun `marine negative levels and hourly rain rates use the same display boundary`() {
        assertEquals("-10.0 ft", imperial.format(-3.048, WeatherUnit.HEIGHT, 1, Locale.US))
        assertEquals("1.00 in/h", imperial.format(25.4, WeatherUnit.PRECIPITATION_RATE, 1, Locale.US))
        assertEquals("25.4 mm/h", metric.format(25.4, WeatherUnit.PRECIPITATION_RATE, 1, Locale.US))
    }

    @Test fun `overflow during display conversion is treated as unavailable`() {
        assertEquals("—", imperial.temp(Double.MAX_VALUE))
        assertEquals("—", imperial.format(Double.MAX_VALUE, WeatherUnit.HEIGHT))
        assertFalse(metric.temp(1e12, locale = Locale.US).contains("2147483647"))
    }

    @Test fun `switching unit systems preserves order and relative position within chart ranges`() {
        for (unit in listOf(WeatherUnit.TEMPERATURE, WeatherUnit.WIND_SPEED, WeatherUnit.PRECIPITATION, WeatherUnit.HEIGHT, WeatherUnit.DISTANCE)) {
            val low = 2.0
            val center = 5.5
            val high = 12.0
            val expected = (center - low) / (high - low)
            val actual = (imperial.convert(center, unit) - imperial.convert(low, unit)) /
                (imperial.convert(high, unit) - imperial.convert(low, unit))
            assertEquals(unit.name, expected, actual, 1e-10)
            assertTrue(imperial.convert(high, unit) > imperial.convert(low, unit))
        }
    }
}
