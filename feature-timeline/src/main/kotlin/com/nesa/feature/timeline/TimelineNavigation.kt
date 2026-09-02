package com.nesa.feature.timeline

import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object TimelineRoutes {
    const val ROOT = "timeline"
}

object ActivityEditorRoutes {
    const val ARG_ACTIVITY_ID = "activityId"

    private const val BASE = "activity"
    const val NEW = "$BASE/new"
    const val EDIT = "$BASE/edit/{$ARG_ACTIVITY_ID}"

    fun edit(activityId: String): String = "$BASE/edit/$activityId"
}

fun NavGraphBuilder.timelineGraph(
    onAddActivity: () -> Unit,
    onEditActivity: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(TimelineRoutes.ROOT) {
        TimelineScreen(
            onAddActivity = onAddActivity,
            onEditActivity = onEditActivity,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    }
}

fun NavGraphBuilder.activityEditorGraph(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(ActivityEditorRoutes.NEW) {
        ActivityEditorScreen(onDone = onDone, modifier = modifier)
    }
    composable(
        route = ActivityEditorRoutes.EDIT,
        arguments = listOf(
            navArgument(ActivityEditorRoutes.ARG_ACTIVITY_ID) { type = NavType.StringType }
        )
    ) {
        ActivityEditorScreen(onDone = onDone, modifier = modifier)
    }
}
