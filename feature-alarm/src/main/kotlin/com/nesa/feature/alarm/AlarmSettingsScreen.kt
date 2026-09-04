package com.nesa.feature.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.Alarm
import com.nesa.core.model.ChallengeDifficulty
import com.nesa.core.model.WakeChallengeType
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NesaTimePickerDialog
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.NoticeEmphasis
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.component.SwitchRow
import com.nesa.core.ui.component.TimeField
import com.nesa.core.ui.theme.NesaSpacing
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Alarm configuration.
 *
 * Where Android limits what NESA can promise — exact alarms in particular — the
 * screen says so plainly and offers the way to fix it, rather than quietly
 * behaving worse than the user expects.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingTime by remember { mutableStateOf(false) }
    val nextFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM) }

    // The system ringtone picker, rather than a list of NESA's own.
    // RingtoneManager.ACTION_RINGTONE_PICKER shows every sound the phone already
    // has, including ones the user has added, and it is the screen they already
    // know from the clock app. Writing our own would show fewer sounds and look
    // unfamiliar for no gain.
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // A null here is the picker's "Silent" entry. Stored as null, which
            // AlarmAudioPlayer reads as "use the device default" — NESA does not
            // keep an alarm that cannot make a sound.
            viewModel.onSoundChanged(picked?.toString())
        }
    }

    NesaScaffold(
        title = stringResource(R.string.alarm_title),
        modifier = modifier,
        onBack = onBack
    ) { padding ->
        val alarm = state.alarm
        if (alarm == null) return@NesaScaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = NesaSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            if (state.exactAlarmsUnavailable) {
                NoticeCard(
                    text = stringResource(R.string.alarm_inexact_warning),
                    emphasis = NoticeEmphasis.WARNING,
                    action = {
                        val settingsIntent = viewModel.exactAlarmSettingsIntent()
                        if (settingsIntent != null) {
                            TextButton(onClick = { context.startActivity(settingsIntent) }) {
                                Text(stringResource(R.string.alarm_inexact_action))
                            }
                        }
                    }
                )
            }

            SwitchRow(
                title = stringResource(R.string.alarm_enabled),
                checked = alarm.enabled,
                onCheckedChange = viewModel::onEnabledChanged
            )

            TimeField(
                label = stringResource(R.string.alarm_time),
                value = alarm.time,
                supportingText = state.nextRing?.let {
                    stringResource(R.string.alarm_next_ring, it.format(nextFormatter))
                } ?: stringResource(R.string.alarm_not_scheduled),
                onClick = { pickingTime = true }
            )

            SectionHeader(title = stringResource(R.string.alarm_repeat))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in alarm.days,
                        onClick = { viewModel.onDayToggled(day) },
                        label = { Text(day.shortLabel()) }
                    )
                }
            }
            if (!alarm.repeats) {
                Text(
                    text = stringResource(R.string.alarm_repeat_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
            SectionHeader(title = stringResource(R.string.alarm_challenge_title))

            SwitchRow(
                title = stringResource(R.string.alarm_challenge_required),
                supportingText = stringResource(R.string.alarm_challenge_required_support),
                checked = alarm.challenge.required,
                onCheckedChange = viewModel::onChallengeRequiredChanged
            )
            SwitchRow(
                title = stringResource(R.string.alarm_challenge_adaptive),
                supportingText = stringResource(R.string.alarm_challenge_adaptive_support),
                checked = alarm.challenge.adaptive,
                onCheckedChange = viewModel::onChallengeAdaptiveChanged,
                enabled = alarm.challenge.required
            )

            Text(
                text = stringResource(R.string.alarm_challenge_type),
                style = MaterialTheme.typography.bodyLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                WakeChallengeType.entries.forEach { type ->
                    FilterChip(
                        selected = alarm.challenge.type == type,
                        onClick = { viewModel.onChallengeTypeChanged(type) },
                        enabled = alarm.challenge.required,
                        label = { Text(type.label()) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.alarm_challenge_difficulty),
                style = MaterialTheme.typography.bodyLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                ChallengeDifficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = alarm.challenge.difficulty == difficulty,
                        onClick = { viewModel.onChallengeDifficultyChanged(difficulty) },
                        enabled = alarm.challenge.required,
                        label = { Text(difficulty.label()) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
            SectionHeader(title = stringResource(R.string.alarm_snooze_title))

            StepperRow(
                label = stringResource(R.string.alarm_snooze_minutes),
                value = stringResource(R.string.alarm_snooze_minutes_value, alarm.snooze.snoozeMinutes),
                sliderValue = alarm.snooze.snoozeMinutes.toFloat(),
                range = 1f..30f,
                steps = 28,
                onChange = { viewModel.onSnoozeMinutesChanged(it.toInt()) }
            )
            StepperRow(
                label = stringResource(R.string.alarm_snooze_max),
                value = stringResource(R.string.alarm_snooze_max_value, alarm.snooze.maxSnoozes),
                sliderValue = alarm.snooze.maxSnoozes.toFloat(),
                range = 0f..10f,
                steps = 9,
                onChange = { viewModel.onMaxSnoozesChanged(it.toInt()) }
            )
            StepperRow(
                label = stringResource(R.string.alarm_retry_minutes),
                value = stringResource(R.string.alarm_retry_minutes_value, alarm.snooze.autoRetryMinutes),
                sliderValue = alarm.snooze.autoRetryMinutes.toFloat(),
                range = 1f..30f,
                steps = 28,
                onChange = { viewModel.onRetryMinutesChanged(it.toInt()) }
            )
            StepperRow(
                label = stringResource(R.string.alarm_retry_max),
                value = stringResource(R.string.alarm_snooze_max_value, alarm.snooze.maxAutoRetries),
                sliderValue = alarm.snooze.maxAutoRetries.toFloat(),
                range = 0f..10f,
                steps = 9,
                onChange = { viewModel.onMaxRetriesChanged(it.toInt()) }
            )

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))

            SectionHeader(title = stringResource(R.string.alarm_sound_section))

            // Remembered against the URI: resolving a ringtone's title opens the
            // media file, and doing that on every recomposition would put file
            // I/O on the main thread for a string that only changes when the
            // user picks a different sound.
            val soundName = remember(alarm.soundUri) { ringtoneTitle(context, alarm.soundUri) }
            Text(
                text = stringResource(R.string.alarm_sound_current, soundName),
                style = MaterialTheme.typography.bodyLarge
            )
            FilledTonalButton(
                onClick = {
                    soundPicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TITLE,
                                context.getString(R.string.alarm_sound_picker_title)
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            // No "Silent" entry: this is an alarm.
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                alarm.soundUri?.let(Uri::parse)
                            )
                        }
                    )
                }
            ) {
                Text(stringResource(R.string.alarm_sound_choose))
            }

            StepperRow(
                label = stringResource(R.string.alarm_volume),
                value = stringResource(R.string.alarm_volume_value, alarm.volumePercent),
                sliderValue = alarm.volumePercent.toFloat(),
                range = Alarm.MIN_VOLUME_PERCENT.toFloat()..100f,
                // 10% steps across a 10..100 range.
                steps = 8,
                onChange = { viewModel.onVolumeChanged(it.toInt()) }
            )
            Text(
                text = stringResource(R.string.alarm_volume_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SwitchRow(
                title = stringResource(R.string.alarm_vibrate),
                checked = alarm.vibrate,
                onCheckedChange = viewModel::onVibrateChanged
            )
            StepperRow(
                label = stringResource(R.string.alarm_fade_in),
                value = stringResource(R.string.alarm_fade_in_value, alarm.fadeInSeconds),
                sliderValue = alarm.fadeInSeconds.toFloat(),
                range = 0f..60f,
                steps = 11,
                onChange = { viewModel.onFadeInChanged(it.toInt()) }
            )

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }

    if (pickingTime) {
        NesaTimePickerDialog(
            initial = state.alarm?.time ?: java.time.LocalTime.of(7, 0),
            confirmLabel = stringResource(R.string.alarm_confirm),
            cancelLabel = stringResource(R.string.alarm_cancel),
            onConfirm = {
                viewModel.onTimeChanged(it)
                pickingTime = false
            },
            onDismiss = { pickingTime = false }
        )
    }
}

/**
 * A labelled slider that commits once.
 *
 * Every change to an alarm is persisted and re-armed, so writing on each pixel
 * of a drag would mean dozens of database writes and alarm reschedules for one
 * gesture. The thumb tracks locally and the value is committed on release.
 */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    var dragged by remember(sliderValue) { mutableFloatStateOf(sliderValue) }

    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged) },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
internal fun WakeChallengeType.label(): String = stringResource(
    when (this) {
        WakeChallengeType.TAP_SEQUENCE -> R.string.alarm_type_tap
        WakeChallengeType.PATTERN_RECALL -> R.string.alarm_type_pattern
        WakeChallengeType.MEMORY_MATCH -> R.string.alarm_type_memory
        WakeChallengeType.REACTION -> R.string.alarm_type_reaction
    }
)

@Composable
internal fun ChallengeDifficulty.label(): String = stringResource(
    when (this) {
        ChallengeDifficulty.EASY -> R.string.alarm_difficulty_easy
        ChallengeDifficulty.MEDIUM -> R.string.alarm_difficulty_medium
        ChallengeDifficulty.HARD -> R.string.alarm_difficulty_hard
    }
)

@Composable
private fun DayOfWeek.shortLabel(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> R.string.alarm_day_mon
        DayOfWeek.TUESDAY -> R.string.alarm_day_tue
        DayOfWeek.WEDNESDAY -> R.string.alarm_day_wed
        DayOfWeek.THURSDAY -> R.string.alarm_day_thu
        DayOfWeek.FRIDAY -> R.string.alarm_day_fri
        DayOfWeek.SATURDAY -> R.string.alarm_day_sat
        DayOfWeek.SUNDAY -> R.string.alarm_day_sun
    }
)

/**
 * The human name of a sound, for a screen that must not show a content URI.
 *
 * Falls back to "the phone's default" whenever the name cannot be resolved —
 * which includes a sound on a removed SD card, or one whose app has been
 * uninstalled. Those are exactly the cases where AlarmAudioPlayer will fall
 * through to a device default too, so the screen and the alarm agree.
 */
private fun ringtoneTitle(context: android.content.Context, uri: String?): String {
    val fallback = context.getString(R.string.alarm_sound_default)
    if (uri == null) return fallback
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
}
