package com.nesa.feature.life

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.LifeScheduleKind
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.theme.NesaSpacing
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Life module: the recurring shape of a week.
 *
 * Each schedule is independently switchable, which is the product rule about
 * not forcing modules on people expressed as a screen: someone who wants work
 * hours and no meal reminders can have exactly that, and turning one off does
 * not delete it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LifeSchedulesScreen(
    onBack: () -> Unit,
    onEditSchedule: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LifeSchedulesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NesaScaffold(
        title = stringResource(R.string.life_title),
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
            NavigationRow(
                title = stringResource(R.string.life_review),
                onClick = onOpenReview
            )
            NavigationRow(
                title = stringResource(R.string.life_statistics),
                onClick = onOpenStatistics
            )

            HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
            SectionHeader(title = stringResource(R.string.life_schedules))

            if (state.schedules.isEmpty()) {
                NoticeCard(text = stringResource(R.string.life_no_schedules))
            }

            state.schedules.forEach { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onEdit = { onEditSchedule(schedule.id) },
                    onDelete = { viewModel.onDelete(schedule) },
                    onEnabledChanged = { viewModel.onEnabledChanged(schedule, it) }
                )
            }

            if (state.availableKinds.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.life_add))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                    state.availableKinds.forEach { kind ->
                        FilledTonalButton(
                            onClick = { viewModel.onAddSchedule(kind, onEditSchedule) }
                        ) {
                            Text(stringResource(kind.labelRes()))
                        }
                    }
                }
            }

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: LifeSchedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit
) {
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NesaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = schedule.name, style = MaterialTheme.typography.titleMedium)
                Switch(checked = schedule.enabled, onCheckedChange = onEnabledChanged)
            }

            if (schedule.entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.life_schedule_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            schedule.ordered.forEach { entry ->
                Text(
                    text = stringResource(
                        R.string.life_entry_summary,
                        entry.title,
                        entry.start.format(timeFormat),
                        entry.days
                            .sortedBy { it.value }
                            .joinToString(" ") {
                                it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.life_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.life_delete)) }
            }
        }
    }
}

internal fun LifeScheduleKind.labelRes(): Int = when (this) {
    LifeScheduleKind.WORK -> R.string.kind_work
    LifeScheduleKind.SCHOOL -> R.string.kind_school
    LifeScheduleKind.TRAINING -> R.string.kind_training
    LifeScheduleKind.PRAYER -> R.string.kind_prayer
    LifeScheduleKind.MEAL -> R.string.kind_meal
    LifeScheduleKind.CUSTOM -> R.string.kind_custom
}

/**
 * A row that opens another screen.
 *
 * A local copy rather than an import: the settings module has one, but it is
 * private there, and making it public would put a component nobody designed as
 * shared into a shared surface. If a third module needs one, that is the moment
 * to move it into core-ui properly.
 */
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
