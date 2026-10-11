package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UvForecastDto(
    val timezone: String,
    val hourly: UvHourlyDto? = null,
    val daily: UvDailyDto? = null
)

@Serializable
data class UvHourlyDto(
    val time: List<String>,
    @SerialName("uv_index") val uvIndex: List<Double?>? = null
)

@Serializable
data class UvDailyDto(
    val time: List<String>,
    @SerialName("uv_index_max") val uvIndexMax: List<Double?>? = null
)
