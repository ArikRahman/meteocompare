package com.meteocompare.app.ui.localitycompare

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import java.time.LocalDate

@Preview(name = "Localities comparison · light", showBackground = true, widthDp = 430)
@Composable
private fun LocalityCompareLightPreview() {
    MaterialTheme {
        LocalityCompareContent(
            state = LocalityCompareState(
                favorites = listOf(
                    City("paris", "Paris", null, "France", 48.85, 2.35),
                    City("lyon", "Lyon", null, "France", 45.76, 4.84),
                    City("lille", "Lille", null, "France", 50.63, 3.06)
                ),
                selectedIds = listOf("paris", "lyon"),
                forecasts = mapOf(
                    "paris" to CompareForecastState.Loading,
                    "lyon" to CompareForecastState.Failed("offline")
                )
            ),
            calculator = ConfidenceCalculator(EqualWeighting()),
            onBack = {}, onToggleCity = {}, onRefresh = {},
            currentDate = LocalDate.of(2026, 10, 14)
        )
    }
}
