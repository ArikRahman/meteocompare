package com.meteocompare.app.domain.model

/** Persistent clock choice. AUTO preserves the app-language behavior of earlier releases. */
enum class ClockFormatPreference {
    AUTO, HOUR_12, HOUR_24;

    companion object {
        fun fromString(value: String?): ClockFormatPreference =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
