package com.nesa.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.ThemeMode
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NesaTimePickerDialog
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.NoticeEmphasis
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.component.SwitchRow
import com.nesa.core.ui.component.TimeField
import com.nesa.core.ui.format.label
import com.nesa.core.ui.theme.NesaSpacing
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings.
 *
 * Only what Stage 1 actually has: the shape of the day, how insistent NESA is,
 * reminders, appearance, and a way into the alarm. Nothing is here to look
 * complete — every row changes real behaviour.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAlarm: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingField by remember { mutableStateOf<DayWindowField?>(null) }
    var name by remember(state.settings.displayName) {
        mutableStateOf(state.settings.displayName.orEmpty())
    }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    NesaScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        onBack = onBack
    ) { padding ->
        val window = state.settings.dayWindow

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = NesaSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.onDisplayNameChanged(it)
                },
                label = { Text(stringResource(R.string.settings_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(title = stringResource(R.string.settings_reliability_title))
            ReliabilitySection(
                state = state,
                onOpen = { intent -> intent?.let(context::startActivity) },
                viewModel = viewModel
            )

            SectionHeader(title = stringResource(R.string.settings_day_title))
            NoticeCard(text = stringResource(R.string.settings_day_help))

            TimeField(
                label = stringResource(R.string.settings_wake),
                value = window.wakeTime,
                onClick = { editingField = DayWindowField.WAKE }
            )
            TimeField(
                label = stringResource(R.string.settings_sleep),
                value = window.sleepTarget,
                supportingText = if (window.sleepTargetIsAfterMidnight) {
                    stringResource(R.string.settings_sleep_after_midnight)
                } else {
                    null
                },
                onClick = { editingField = DayWindowField.SLEEP }
            )
            TimeField(
                label = stringResource(R.string.settings_morning_ends),
                value = window.morningEnds,
                onClick = { editingField = DayWindowField.MORNING_ENDS }
            )
            TimeField(
                label = stringResource(R.string.settings_evening_starts),
                value = window.eveningStarts,
                onClick = { editingField = DayWindowField.EVENING_STARTS }
            )
            TimeField(
                label = stringResource(R.string.settings_night_starts),
                value = window.nightStarts,
                onClick = { editingField = DayWindowField.NIGHT_STARTS }
            )

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
            SectionHeader(title = stringResource(R.string.settings_guidance_title))
            NoticeCard(text = stringResource(R.string.settings_guidance_help))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                GuidancePersonality.entries.forEach { guidance ->
                    FilterChip(
                        selected = state.settings.guidance == guidance,
                        onClick = { viewModel.onGuidanceChanged(guidance) },
                        label = { Text(guidance.label()) }
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.settings_guidance_detail,
                    state.settings.guidance.maxReminders,
                    state.settings.guidance.missedGraceMinutes
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SwitchRow(
                title = stringResource(R.string.settings_reminders),
                supportingText = stringResource(R.string.settings_reminders_support),
                checked = state.settings.remindersEnabled,
                onCheckedChange = viewModel::onRemindersEnabledChanged,
                enabled = state.notificationsAllowed
            )

            if (!state.notificationsAllowed) {
                NoticeCard(
                    text = stringResource(R.string.settings_notifications_blocked),
                    emphasis = NoticeEmphasis.WARNING,
                    action = {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    viewModel.notificationSettingsIntent(context.packageName)
                                )
                            }
                        ) {
                            Text(stringResource(R.string.settings_notifications_action))
                        }
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
            SectionHeader(title = stringResource(R.string.settings_appearance_title))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.settings.themeMode == mode,
                        onClick = { viewModel.onThemeModeChanged(mode) },
                        label = { Text(mode.themeLabel()) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))

            NavigationRow(
                title = stringResource(R.string.settings_alarm),
                onClick = onOpenAlarm
            )

            SectionHeader(title = stringResource(R.string.settings_about_title))
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }

    val field = editingField
    if (field != null) {
        val window = state.settings.dayWindow
        NesaTimePickerDialog(
            initial = when (field) {
                DayWindowField.WAKE -> window.wakeTime
                DayWindowField.SLEEP -> window.sleepTarget
                DayWindowField.MORNING_ENDS -> window.morningEnds
                DayWindowField.EVENING_STARTS -> window.eveningStarts
                DayWindowField.NIGHT_STARTS -> window.nightStarts
            },
            confirmLabel = stringResource(R.string.settings_confirm),
            cancelLabel = stringResource(R.string.settings_cancel),
            onConfirm = { time: LocalTime ->
                viewModel.onDayWindowFieldChanged(field, time)
                editingField = null
            },
            onDismiss = { editingField = null }
        )
    }
}

/**
 * Says plainly whether the alarm can be trusted, and offers the fix for each
 * thing that is missing.
 *
 * The failure this prevents is the worst one NESA has: an alarm that does not
 * ring because Android quietly declined to run the app in the background, with
 * nothing anywhere saying so.
 */
@Composable
private fun ReliabilitySection(
    state: SettingsUiState,
    onOpen: (Intent?) -> Unit,
    viewModel: SettingsViewModel
) {
    val reliability = state.reliability
    // Read here, not inside the click lambda: LocalContext is a composable read
    // and a lambda is not a composable scope.
    val packageName = LocalContext.current.packageName

    NoticeCard(
        text = stringResource(
            if (reliability.isFullyReliable) {
                R.string.settings_reliability_ok
            } else {
                R.string.settings_reliability_problem
            }
        ),
        emphasis = if (reliability.isFullyReliable) {
            NoticeEmphasis.INFORMATION
        } else {
            NoticeEmphasis.WARNING
        }
    )

    // Battery optimisation is first because it is the most common cause by a
    // wide margin, and the only one with a one-tap system prompt.
    PermissionRow(
        title = stringResource(R.string.settings_reliability_battery),
        supportingText = stringResource(R.string.settings_reliability_battery_support),
        granted = reliability.ignoringBatteryOptimisations,
        onFix = { onOpen(viewModel.batteryOptimisationRequest()) }
    )
    PermissionRow(
        title = stringResource(R.string.settings_reliability_exact),
        supportingText = stringResource(R.string.settings_reliability_exact_support),
        granted = reliability.exactAlarmsAllowed,
        onFix = { onOpen(viewModel.exactAlarmSettings()) }
    )
    // Second only to battery optimisation in how often it is the actual cause,
    // and unlike that one it has never been asked for at all.
    PermissionRow(
        title = stringResource(R.string.settings_reliability_overlay),
        supportingText = stringResource(R.string.settings_reliability_overlay_support),
        granted = reliability.canAppearOverOtherApps,
        onFix = { onOpen(viewModel.overlaySettings()) }
    )
    PermissionRow(
        title = stringResource(R.string.settings_reliability_notifications),
        supportingText = stringResource(R.string.settings_reliability_notifications_support),
        granted = reliability.notificationsAllowed,
        onFix = { onOpen(viewModel.notificationSettingsIntent(packageName)) }
    )

    // Whether the platform is actually holding the alarm. Permissions can all be
    // granted and the alarm still be gone, and only this tells them apart.
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    Text(
        text = stringResource(
            if (reliability.alarmArmed) {
                R.string.settings_reliability_armed
            } else {
                R.string.settings_reliability_not_armed
            }
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = if (reliability.alarmArmed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    )
    reliability.nextSystemAlarmMillis?.let { millis ->
        Text(
            text = stringResource(
                R.string.settings_reliability_next_system,
                Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (reliability.silentlyDropped) {
        NoticeCard(
            text = stringResource(R.string.settings_reliability_not_armed_support),
            emphasis = NoticeEmphasis.WARNING
        )
    }

    // Offered, never imposed: a permanent notification is a real cost, so this
    // stays off until the user decides their phone needs it.
    SwitchRow(
        title = stringResource(R.string.settings_reliability_keep_alive),
        supportingText = stringResource(R.string.settings_reliability_keep_alive_support),
        checked = state.settings.keepAliveEnabled,
        onCheckedChange = viewModel::onKeepAliveChanged
    )

    var testMessage by remember { mutableStateOf<String?>(null) }
    val armedTemplate = stringResource(R.string.settings_reliability_test_armed)
    val failedMessage = stringResource(R.string.settings_reliability_test_failed)

    FilledTonalButton(
        onClick = {
            viewModel.onTestAlarm { millis ->
                testMessage = if (millis == null) {
                    failedMessage
                } else {
                    String.format(
                        armedTemplate,
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .format(timeFormatter)
                    )
                }
            }
        }
    ) {
        Text(stringResource(R.string.settings_reliability_test))
    }
    testMessage?.let { NoticeCard(text = it) }

    // The way out for a phone that will not run a third-party alarm on time.
    if (viewModel.systemAlarmAvailable) {
        var handoffMessage by remember { mutableStateOf<String?>(null) }
        val handedOff = stringResource(R.string.settings_reliability_handoff_done)
        val handoffFailed = stringResource(R.string.settings_reliability_handoff_failed)

        SectionHeader(title = stringResource(R.string.settings_reliability_handoff_title))
        NoticeCard(text = stringResource(R.string.settings_reliability_handoff_body))
        Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
            FilledTonalButton(
                onClick = {
                    viewModel.onHandOffToSystemAlarm { intent ->
                        handoffMessage = if (intent == null) {
                            handoffFailed
                        } else {
                            onOpen(intent)
                            handedOff
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_reliability_handoff_create))
            }
            TextButton(onClick = { onOpen(viewModel.systemAlarmListIntent()) }) {
                Text(stringResource(R.string.settings_reliability_handoff_show))
            }
        }
        handoffMessage?.let { NoticeCard(text = it) }
    }

    // The alarm's own trace. Whichever step is missing is the bug, and reading
    // it needs no adb, no cable and no laptop.
    SectionHeader(
        title = stringResource(R.string.settings_reliability_events),
        trailing = null
    )
    if (state.alarmEvents.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_reliability_events_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        // No inner scroll: the settings screen already scrolls vertically, and
        // nesting two scrollables in the same direction is both a Compose hazard
        // and unpleasant to use. Showing the tail is enough — the last alarm is
        // the one being diagnosed.
        Column(Modifier.fillMaxWidth()) {
            state.alarmEvents.takeLast(MAX_VISIBLE_EVENTS).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = viewModel::onClearAlarmEvents) {
            Text(stringResource(R.string.settings_reliability_events_clear))
        }
    }

    // No API exposes the manufacturer auto-start switches, so the honest move is
    // to say what to look for rather than pretend NESA can check it.
    NoticeCard(text = stringResource(R.string.settings_reliability_manufacturer))
    TextButton(onClick = { onOpen(viewModel.appDetailsSettings()) }) {
        Text(stringResource(R.string.settings_reliability_open_app_settings))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    supportingText: String,
    granted: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NesaSpacing.touchTarget)
            .padding(vertical = NesaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NesaSpacing.md)
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                text = stringResource(R.string.settings_reliability_granted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            FilledTonalButton(onClick = onFix) {
                Text(stringResource(R.string.settings_reliability_fix))
            }
        }
    }
}

/** Enough to see a whole alarm from arming to outcome, without burying the screen. */
private const val MAX_VISIBLE_EVENTS = 25

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NesaSpacing.touchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = NesaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun ThemeMode.themeLabel(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
)
