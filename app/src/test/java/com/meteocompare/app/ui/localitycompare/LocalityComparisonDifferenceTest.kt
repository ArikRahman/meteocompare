package com.meteocompare.app.ui.localitycompare

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalityComparisonDifferenceTest {
    @Test fun differencesAreSymmetricalAndNeverInventMissingForecasts() {
        assertEquals(3.0, comparisonMaxTemperatureDifference(21.0, 24.0)!!, 0.0001)
        assertEquals(3.0, comparisonMaxTemperatureDifference(24.0, 21.0)!!, 0.0001)
        assertNull(comparisonMaxTemperatureDifference(null, 24.0))
        assertNull(comparisonMaxTemperatureDifference(Double.NaN, 24.0))
        assertNull(comparisonMaxTemperatureDifference(Double.POSITIVE_INFINITY, 24.0))
    }

    @Test fun temperatureGapUsesDeltaConversionsInImperialAndUkProfiles() {
        val gap = comparisonMaxTemperatureDifference(21.0, 24.0)!!
        assertEquals(5.4, WeatherUnits(UnitSystem.IMPERIAL).temperatureDelta(gap), 0.0001)
        assertEquals(3.0, WeatherUnits(UnitSystem.UK_MIXED).temperatureDelta(gap), 0.0001)
    }
}
