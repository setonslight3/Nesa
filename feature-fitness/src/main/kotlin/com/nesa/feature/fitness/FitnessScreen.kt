package com.nesa.feature.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.PerceivedEffort
import com.nesa.core.model.WorkoutRoutine
import com.nesa.core.scheduling.FitnessSummary
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.NoticeEmphasis
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.theme.NesaSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * The fitness overview.
 *
 * Deliberately short. It answers three questions — how is the week going, what
 * can I do today, what have I done — and offers a one-tap way to log a routine
 * as performed. Anything more elaborate would be a training app; NESA's fitness
 * module exists to keep training inside the same honest picture of the day as
 * everything else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitnessScreen(
    onBack: () -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FitnessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    var loggingRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val loggedMessage = stringResource(R.string.fitness_logged)

    NesaScaffold(
        title = stringResource(R.string.fitness_title),
        modifier = modifier,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = NesaSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            WeekSummary(state.summary)

            if (state.summary.restRecommended) {
                NoticeCard(
                    text = stringResource(R.string.fitness_rest_advised),
                    emphasis = NoticeEmphasis.INFORMATION
                )
            }

            confirmation?.let { NoticeCard(text = it) }

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
            SectionHeader(title = stringResource(R.string.fitness_routines))

            if (!state.hasRoutines) {
                NoticeCard(text = stringResource(R.string.fitness_no_routines))
            }

            state.routines.forEach { routine ->
                RoutineCard(
                    routine = routine,
                    onEdit = { onEditRoutine(routine.id) },
                    onDelete = { viewModel.onDeleteRoutine(routine.id) },
                    onLog = { loggingRoutine = if (loggingRoutine?.id == routine.id) null else routine }
                )

                // The effort question is asked inline rather than in a dialog:
                // a session logged is worth more than a session logged
                // precisely, and an extra screen is where people give up.
                if (loggingRoutine?.id == routine.id) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                        Text(
                            text = stringResource(R.string.fitness_effort_prompt),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        PerceivedEffort.entries.forEach { effort ->
                            TextButton(
                                onClick = {
                                    viewModel.onLogRoutine(routine, effort)
                                    loggingRoutine = null
                                    confirmation = loggedMessage
                                }
                            ) {
                                Text(stringResource(effort.labelRes()))
                            }
                        }
                    }
                }
            }

            FilledTonalButton(onClick = onAddRoutine) {
                Text(stringResource(R.string.fitness_add_routine))
            }

            if (state.recentSessions.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
                SectionHeader(title = stringResource(R.string.fitness_recent))

                val routineNames = state.routines.associate { it.id to it.name }
                state.recentSessions.forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.fitness_session_summary,
                                session.routineId?.let { routineNames[it] }
                                    ?: stringResource(R.string.fitness_session_unplanned),
                                session.durationMinutes,
                                session.date.format(dateFormat)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { viewModel.onDeleteSession(session.id) }) {
                            Text(stringResource(R.string.fitness_delete))
                        }
                    }
                }
            }

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }
}

@Composable
private fun WeekSummary(summary: FitnessSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.xs)) {
        SectionHeader(title = stringResource(R.string.fitness_week_heading))
        Text(
            text = stringResource(
                R.string.fitness_week_sessions,
                summary.sessionsThisWeek,
                summary.weeklyTarget
            ),
            style = MaterialTheme.typography.titleMedium
        )
        LinearProgressIndicator(
            progress = { summary.weeklyProgress },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (summary.streakWeeks > 0) {
                stringResource(R.string.fitness_streak, summary.streakWeeks)
            } else {
                stringResource(R.string.fitness_streak_none, summary.weeklyTarget)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (summary.volumeKgThisWeek > 0.0) {
            Text(
                text = stringResource(
                    R.string.fitness_volume,
                    summary.volumeKgThisWeek.roundToInt().toString()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Bound to a local first. `daysSinceLast` is a public property of a
        // class in :core-scheduling, and Kotlin will not smart-cast one of those
        // across a module boundary — another module could, in principle, make it
        // a custom getter that returns something different on the second read.
        // The local is that second read taken once.
        //
        // "Never trained" and "trained today" must also read differently, which
        // is why null is a branch of its own and not folded into zero.
        val daysSinceLast = summary.daysSinceLast
        Text(
            text = when (daysSinceLast) {
                null -> stringResource(R.string.fitness_never_trained)
                0L -> stringResource(R.string.fitness_last_today)
                1L -> stringResource(R.string.fitness_last_yesterday)
                else -> stringResource(R.string.fitness_last_days, daysSinceLast)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoutineCard(
    routine: WorkoutRoutine,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLog: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NesaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.xs)
        ) {
            Text(text = routine.name, style = MaterialTheme.typography.titleMedium)
            routine.focus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.fitness_routine_summary,
                    routine.exercises.size,
                    routine.schedulableDuration.toMinutes().toInt()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                FilledTonalButton(onClick = onLog) { Text(stringResource(R.string.fitness_log)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.fitness_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.fitness_delete)) }
            }
        }
    }
}

internal fun PerceivedEffort.labelRes(): Int = when (this) {
    PerceivedEffort.EASY -> R.string.effort_easy
    PerceivedEffort.MODERATE -> R.string.effort_moderate
    PerceivedEffort.HARD -> R.string.effort_hard
    PerceivedEffort.MAXIMAL -> R.string.effort_maximal
}
