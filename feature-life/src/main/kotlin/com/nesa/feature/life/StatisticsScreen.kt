package com.nesa.feature.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.scheduling.WeekStatistics
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.theme.NesaSpacing
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Daily and weekly figures.
 *
 * Short on purpose. The blueprint's UI rule is "do not fill screens with
 * dashboards unless the data helps a decision", so this shows the few numbers
 * that change what someone does tomorrow and stops. No charts, no trends, no
 * graphs of a fortnight nobody remembers.
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NesaScaffold(
        title = stringResource(R.string.stats_title),
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
            if (!state.hasAnything) {
                NoticeCard(text = stringResource(R.string.stats_empty))
                return@NesaScaffold
            }

            state.today?.let { today ->
                SectionHeader(title = stringResource(R.string.stats_today))
                Text(
                    text = stringResource(R.string.stats_today_line, today.completed, today.planned),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            state.week?.let { week ->
                HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
                SectionHeader(title = stringResource(R.string.stats_week))
                WeekPanel(week)

                week.bestDay?.let { best ->
                    Text(
                        text = stringResource(
                            R.string.stats_best_day,
                            best.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            best.completed
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (week.currentStreakDays > 0) {
                    Text(
                        text = stringResource(R.string.stats_streak, week.currentStreakDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            state.lastWeek?.let { last ->
                if (last.hasData) {
                    HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
                    SectionHeader(title = stringResource(R.string.stats_last_week))
                    WeekPanel(last)
                }
            }

            NoticeCard(text = stringResource(R.string.stats_skip_note))

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }
}

@Composable
private fun WeekPanel(week: WeekStatistics) {
    val dayFormat = DateTimeFormatter.ofPattern("EEE")

    Text(
        text = stringResource(
            R.string.stats_week_line,
            week.completed,
            (week.completionRate * 100).roundToInt()
        ),
        style = MaterialTheme.typography.titleMedium
    )
    LinearProgressIndicator(
        progress = { week.completionRate },
        modifier = Modifier.fillMaxWidth()
    )

    week.days.filter { it.hasData }.forEach { day ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = day.date.format(dayFormat),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                // Missed and skipped are shown apart, everywhere, always.
                text = stringResource(
                    R.string.stats_day_line,
                    day.completed,
                    day.missed,
                    day.skipped
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
