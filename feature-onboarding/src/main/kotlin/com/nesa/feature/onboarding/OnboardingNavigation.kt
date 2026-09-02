package com.nesa.feature.onboarding

import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Each feature owns its own route and registers itself. The application module
 * decides the order of screens; it never has to know what they contain.
 */
object OnboardingRoutes {
    const val ROOT = "onboarding"
}

fun NavGraphBuilder.onboardingGraph(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(OnboardingRoutes.ROOT) {
        OnboardingScreen(onFinished = onFinished, modifier = modifier)
    }
}
