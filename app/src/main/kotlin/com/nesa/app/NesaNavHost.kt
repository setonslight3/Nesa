package com.nesa.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.nesa.feature.alarm.AlarmRoutes
import com.nesa.feature.alarm.alarmGraph
import com.nesa.feature.onboarding.OnboardingRoutes
import com.nesa.feature.onboarding.onboardingGraph
import com.nesa.feature.settings.SettingsRoutes
import com.nesa.feature.settings.settingsGraph
import com.nesa.feature.timeline.ActivityEditorRoutes
import com.nesa.feature.timeline.TimelineRoutes
import com.nesa.feature.timeline.activityEditorGraph
import com.nesa.feature.timeline.timelineGraph

/**
 * The one place that knows the order of NESA's screens.
 *
 * Features contribute their own routes; none of them knows what comes before or
 * after it, which is what lets Stage 2 add a module without editing them.
 */
@Composable
fun NesaNavHost(
    startAtOnboarding: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = if (startAtOnboarding) OnboardingRoutes.ROOT else TimelineRoutes.ROOT,
        modifier = modifier
    ) {
        onboardingGraph(
            onFinished = {
                navController.navigate(TimelineRoutes.ROOT) {
                    // Onboarding is finished for good; do not let Back return to it.
                    popUpTo(OnboardingRoutes.ROOT) { inclusive = true }
                }
            }
        )

        timelineGraph(
            onAddActivity = { navController.navigate(ActivityEditorRoutes.NEW) },
            onEditActivity = { activityId ->
                navController.navigate(ActivityEditorRoutes.edit(activityId))
            },
            onOpenSettings = { navController.navigate(SettingsRoutes.ROOT) }
        )

        activityEditorGraph(onDone = { navController.popBackStack() })

        alarmGraph(onBack = { navController.popBackStack() })

        settingsGraph(
            onBack = { navController.popBackStack() },
            onOpenAlarm = { navController.navigate(AlarmRoutes.SETTINGS) }
        )
    }
}

/** Routes the top-level shell needs to reach directly. */
object NesaDestinations {
    const val TIMELINE = TimelineRoutes.ROOT
    const val SETTINGS = SettingsRoutes.ROOT
    const val ALARM = AlarmRoutes.SETTINGS
}
