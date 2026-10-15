package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalityComparisonTest {
    private val day = LocalDate.of(2026, 6, 20)
    private val rise = Instant.parse("2026-06-20T06:00:00Z")
    private val set = Instant.parse("2026-06-20T18:00:00Z")

    @Test fun cloudAdjustedSunshine_usesOnlyOverlappingDaylight() {
        val hours = (0L until 24).map { Instant.parse("2026-06-20T00:00:00Z").plusSeconds(it * 3600) }
        val clouds = hours.mapIndexed { i, _ -> if (i in 6..17) 25 else 100 }
        assertEquals(9.0, estimateCloudAdjustedDaylight(rise, set, hours, clouds), 0.001)
    }

    @Test fun cloudAdjustedSunshine_rejectsIncompleteCloudData() {
        val hours = (6L..8L).map { rise.plusSeconds((it - 6L) * 3600L) }
        assertNull(estimateCloudAdjustedDaylight(rise, set, hours, listOf(0, 0, 0)))
    }

    @Test fun cloudAdjustedSunshine_doesNotCountRepeatedTimestamps() {
        val hours = (0L until 12).map { rise.plusSeconds(it * 3600) }
        assertEquals(6.0, estimateCloudAdjustedDaylight(rise, set, hours + hours, List(24) { 50 }), 0.001)
    }

    @Test fun cloudAdjustedSunshine_invalidSolarWindowReturnsNull() {
        assertNull(estimateCloudAdjustedDaylight(set, rise, listOf(rise), listOf(0)))
    }

    @Test fun dailyMetrics_followExplicitDateAndIgnoreMissingDays() {
        val daily = DailyForecast(
            dates = listOf(day),
            tempMax = listOf(25.0), tempMin = listOf(15.0),
            precipitationSum = listOf(2.0), windSpeedMax = listOf(15.0),
            sunrise = listOf(rise), sunset = listOf(set),
            precipitationProbabilityMax = listOf(80)
        )
        val hourly = HourlyForecast(
            timestamps = (0L until 12).map { rise.plusSeconds(it * 3600) },
            temperature2m = List(12) { 20.0 }, precipitation = List(12) { 0.0 },
            windSpeed10m = List(12) { 15.0 }, cloudCover = List(12) { 0 }
        )
        val forecast = CityForecast(
            city = City("a", "Example", null, "Example", 48.0, 2.0, "UTC"),
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily),
                WeatherModel.ICON_GLOBAL to ForecastSeries(WeatherModel.ICON_GLOBAL, hourly, daily)
            )
        )
        val calculator = ConfidenceCalculator(EqualWeighting())
        assertNull(buildLocalityDayMetrics(forecast, day.plusDays(1), calculator))
        val result = requireNotNull(buildLocalityDayMetrics(forecast, day, calculator))
        assertEquals(25.0, result.maxC!!, 0.001)
        assertEquals(15.0, result.minC!!, 0.001)
        assertEquals(15.0, result.maxWindKmh!!, 0.001)
        assertEquals(12.0, result.estimatedSunHours!!, 0.001)
        assertEquals(2, result.modelCount)
        assertEquals(80, result.rainProbabilityPercent)
        assertEquals(0, result.cloudCoverPercent)
        assertTrue(result.agreementPercent == null || result.agreementPercent in 0..100)
    }

    private fun cloudForecast(
        date: LocalDate,
        zone: String,
        firstClouds: List<Int?>,
        secondClouds: List<Int?>? = null,
        timestamps: List<Instant> = (0L until 24).map {
            date.atStartOfDay(ZoneId.of(zone)).toInstant().plusSeconds(it * 3600)
        }
    ): CityForecast {
        val daily = DailyForecast(
            dates = listOf(date), tempMax = listOf(null), tempMin = listOf(null),
            precipitationSum = listOf(null), windSpeedMax = listOf(null)
        )
        fun series(model: WeatherModel, values: List<Int?>) = ForecastSeries(
            model, HourlyForecast(
                timestamps = timestamps, temperature2m = emptyList(),
                precipitation = emptyList(), windSpeed10m = emptyList(), cloudCover = values
            ), daily
        )
        val models = mutableMapOf(WeatherModel.GFS to series(WeatherModel.GFS, firstClouds))
        if (secondClouds != null) models[WeatherModel.ICON_GLOBAL] =
            series(WeatherModel.ICON_GLOBAL, secondClouds)
        return CityForecast(City("clouds", "Clouds", null, "France", 48.0, 2.0, zone), models)
    }

    @Test fun cloudCover_isMedianOfDailyModelMeans() {
        val forecast = cloudForecast(day, "UTC", List(24) { 20 }, List(24) { 80 })
        assertEquals(50, localityDailyCloudCoverPercent(forecast, day))
        assertNull(localityDailyCloudCoverPercent(forecast, day.plusDays(1)))
    }

    @Test fun cloudCover_rejectsSparseDataAndInvalidValues() {
        val forecast = cloudForecast(day, "UTC", List(5) { 70 })
        assertNull(localityDailyCloudCoverPercent(forecast, day))
        val invalid = cloudForecast(day, "UTC", List(24) { if (it < 13) 101 else 75 })
        assertNull(localityDailyCloudCoverPercent(invalid, day))
    }

    @Test fun cloudCover_ignoresDuplicatedHoursAndIncompleteModel() {
        val timestamps = (0L until 24).map { rise.plusSeconds(it * 3600) }
        val forecast = cloudForecast(
            day, "UTC", List(24) { 30 } + List(24) { 100 }, List(3) { 90 },
            timestamps = timestamps + timestamps
        )
        assertEquals(30, localityDailyCloudCoverPercent(forecast, day))
    }

    @Test fun cloudCover_respectsCityLocalDateAtUtcBoundary() {
        val localStart = day.atStartOfDay(ZoneId.of("Europe/Paris")).toInstant()
        val timestamps = (0L until 12).map { localStart.plusSeconds(it * 3600) }
        val forecast = cloudForecast(day, "Europe/Paris", List(12) { 45 }, timestamps = timestamps)
        assertEquals(45, localityDailyCloudCoverPercent(forecast, day))
    }

    @Test fun cloudCover_respects23And25HourDaysAtDst() {
        val zone = ZoneId.of("Europe/Paris")
        val spring = LocalDate.of(2026, 3, 29)
        val fall = LocalDate.of(2026, 10, 25)
        fun firstTwelve(date: LocalDate) = (0L until 12).map {
            date.atStartOfDay(zone).toInstant().plusSeconds(it * 3600)
        }
        assertEquals(60, localityDailyCloudCoverPercent(
            cloudForecast(spring, zone.id, List(12) { 60 }, timestamps = firstTwelve(spring)), spring
        ))
        assertNull(localityDailyCloudCoverPercent(
            cloudForecast(fall, zone.id, List(12) { 60 }, timestamps = firstTwelve(fall)), fall
        ))
    }

    @Test fun missingRainProbability_doesNotBecomesZeroPercent() {
        val forecast = cloudForecast(day, "UTC", List(24) { 30 })
        val result = requireNotNull(buildLocalityDayMetrics(
            forecast, day, ConfidenceCalculator(EqualWeighting())
        ))
        assertNull(result.rainProbabilityPercent)
        assertEquals(30, result.cloudCoverPercent)
    }
}
