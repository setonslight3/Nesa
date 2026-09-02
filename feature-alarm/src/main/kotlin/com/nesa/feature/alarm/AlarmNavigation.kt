package com.nesa.feature.alarm

import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object AlarmRoutes {
    const val SETTINGS = "alarm"
}

fun NavGraphBuilder.alarmGraph(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(AlarmRoutes.SETTINGS) {
        AlarmSettingsScreen(onBack = onBack, modifier = modifier)
    }
}
