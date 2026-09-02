package com.nesa.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.GoalCategory
import com.nesa.core.ui.component.NesaTimePickerDialog
import com.nesa.core.ui.component.SwitchRow
import com.nesa.core.ui.component.TimeField
import com.nesa.core.ui.theme.NesaSpacing
import java.time.LocalTime

/**
 * The whole of onboarding: three short steps, each one skippable.
 *
 * The screen keeps a single primary action at the bottom and never presents a
 * form the user has to complete before NESA becomes useful.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    OnboardingContent(
        state = state,
        onNameChanged = viewModel::onNameChanged,
        onGoalToggled = viewModel::onGoalToggled,
        onWakeTimeChanged = viewModel::onWakeTimeChanged,
        onSleepTargetChanged = viewModel::onSleepTargetChanged,
        onCreateWakeAlarmChanged = viewModel::onCreateWakeAlarmChanged,
        onBack = viewModel::onBack,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
        modifier = modifier
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    onGoalToggled: (GoalCategory) -> Unit,
    onWakeTimeChanged: (LocalTime) -> Unit,
    onSleepTargetChanged: (LocalTime) -> Unit,
    onCreateWakeAlarmChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(NesaSpacing.screen),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.lg)
        ) {
            Text(
                text = stringResource(
                    R.string.onboarding_step,
                    state.step.ordinal + 1,
                    OnboardingStep.entries.size
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep(state.displayName, onNameChanged)
                OnboardingStep.GOALS -> GoalsStep(state.selectedGoals, onGoalToggled)
                OnboardingStep.RHYTHM -> RhythmStep(
                    state = state,
                    onWakeTimeChanged = onWakeTimeChanged,
                    onSleepTargetChanged = onSleepTargetChanged,
                    onCreateWakeAlarmChanged = onCreateWakeAlarmChanged
                )
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            Button(
                onClick = onNext,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = NesaSpacing.xs),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    stringResource(
                        if (state.isLastStep) R.string.onboarding_start else R.string.onboarding_next
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.canGoBack) {
                    TextButton(onClick = onBack, enabled = !state.saving) {
                        Text(stringResource(com.nesa.core.ui.R.string.nesa_back))
                    }
                } else {
                    Spacer(Modifier)
                }
                TextButton(onClick = onSkip, enabled = !state.saving) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(name: String, onNameChanged: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalsStep(selected: Set<GoalCategory>, onToggle: (GoalCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_goals_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.onboarding_goals_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.xs)
        ) {
            GoalCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in selected,
                    onClick = { onToggle(category) },
                    label = { Text(category.label()) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }
    }
}

@Composable
private fun RhythmStep(
    state: OnboardingUiState,
    onWakeTimeChanged: (LocalTime) -> Unit,
    onSleepTargetChanged: (LocalTime) -> Unit,
    onCreateWakeAlarmChanged: (Boolean) -> Unit
) {
    var editing by remember { mutableStateOf<RhythmField?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_rhythm_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.onboarding_rhythm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TimeField(
            label = stringResource(R.string.onboarding_wake_label),
            value = state.wakeTime,
            onClick = { editing = RhythmField.WAKE }
        )
        TimeField(
            label = stringResource(R.string.onboarding_sleep_label),
            value = state.sleepTarget,
            onClick = { editing = RhythmField.SLEEP }
        )
        SwitchRow(
            title = stringResource(R.string.onboarding_create_alarm),
            supportingText = stringResource(R.string.onboarding_create_alarm_support),
            checked = state.createWakeAlarm,
            onCheckedChange = onCreateWakeAlarmChanged
        )
    }

    val field = editing
    if (field != null) {
        NesaTimePickerDialog(
            initial = if (field == RhythmField.WAKE) state.wakeTime else state.sleepTarget,
            confirmLabel = stringResource(R.string.onboarding_confirm),
            cancelLabel = stringResource(R.string.onboarding_cancel),
            onConfirm = { time ->
                if (field == RhythmField.WAKE) onWakeTimeChanged(time) else onSleepTargetChanged(time)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

private enum class RhythmField { WAKE, SLEEP }

@Composable
private fun GoalCategory.label(): String = stringResource(
    when (this) {
        GoalCategory.PRODUCTIVITY -> R.string.goal_productivity
        GoalCategory.FITNESS -> R.string.goal_fitness
        GoalCategory.LEARNING -> R.string.goal_learning
        GoalCategory.SLEEP -> R.string.goal_sleep
        GoalCategory.TIME_MANAGEMENT -> R.string.goal_time_management
        GoalCategory.CONSISTENCY -> R.string.goal_consistency
        GoalCategory.PERSONAL_PROJECTS -> R.string.goal_personal_projects
        GoalCategory.CUSTOM -> R.string.goal_custom
    }
)
