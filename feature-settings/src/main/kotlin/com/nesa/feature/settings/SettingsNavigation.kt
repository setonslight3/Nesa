package com.nesa.feature.settings

import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object SettingsRoutes {
    const val ROOT = "settings"
}

fun NavGraphBuilder.settingsGraph(
    onBack: () -> Unit,
    onOpenAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(SettingsRoutes.ROOT) {
        SettingsScreen(onBack = onBack, onOpenAlarm = onOpenAlarm, modifier = modifier)
    }
}
