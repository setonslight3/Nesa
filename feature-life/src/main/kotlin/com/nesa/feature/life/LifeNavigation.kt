package com.nesa.feature.life

import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object LifeRoutes {
    const val ROOT = "life"
    const val REVIEW = "life/review"
    const val STATISTICS = "life/statistics"

    const val ARG_SCHEDULE_ID = "scheduleId"
    private const val BASE = "life/schedule"
    const val EDIT_SCHEDULE = "$BASE/{$ARG_SCHEDULE_ID}"

    fun editSchedule(scheduleId: String): String = "$BASE/$scheduleId"
}

fun NavGraphBuilder.lifeGraph(
    onBack: () -> Unit,
    onEditSchedule: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(LifeRoutes.ROOT) {
        LifeSchedulesScreen(
            onBack = onBack,
            onEditSchedule = onEditSchedule,
            onOpenReview = onOpenReview,
            onOpenStatistics = onOpenStatistics,
            modifier = modifier
        )
    }

    composable(
        route = LifeRoutes.EDIT_SCHEDULE,
        arguments = listOf(navArgument(LifeRoutes.ARG_SCHEDULE_ID) { type = NavType.StringType })
    ) {
        ScheduleEditorScreen(onDone = onBack, modifier = modifier)
    }

    composable(LifeRoutes.REVIEW) {
        NightReviewScreen(onBack = onBack, modifier = modifier)
    }

    composable(LifeRoutes.STATISTICS) {
        StatisticsScreen(onBack = onBack, modifier = modifier)
    }
}
