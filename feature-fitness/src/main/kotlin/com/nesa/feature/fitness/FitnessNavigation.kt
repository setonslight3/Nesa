package com.nesa.feature.fitness

import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object FitnessRoutes {
    const val ROOT = "fitness"
    const val ARG_ROUTINE_ID = "routineId"

    private const val BASE = "routine"
    const val NEW_ROUTINE = "$BASE/new"
    const val EDIT_ROUTINE = "$BASE/edit/{$ARG_ROUTINE_ID}"

    fun editRoutine(routineId: String): String = "$BASE/edit/$routineId"
}

fun NavGraphBuilder.fitnessGraph(
    onBack: () -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    composable(FitnessRoutes.ROOT) {
        FitnessScreen(
            onBack = onBack,
            onAddRoutine = onAddRoutine,
            onEditRoutine = onEditRoutine,
            modifier = modifier
        )
    }
}

fun NavGraphBuilder.routineEditorGraph(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    composable(FitnessRoutes.NEW_ROUTINE) {
        RoutineEditorScreen(onDone = onDone, modifier = modifier)
    }
    composable(
        route = FitnessRoutes.EDIT_ROUTINE,
        arguments = listOf(navArgument(FitnessRoutes.ARG_ROUTINE_ID) { type = NavType.StringType })
    ) {
        RoutineEditorScreen(onDone = onDone, modifier = modifier)
    }
}
