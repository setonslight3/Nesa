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
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.scheduling.ReviewItem
import com.nesa.core.scheduling.RescheduleSuggestion
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.NoticeEmphasis
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.theme.NesaSpacing
import java.time.format.DateTimeFormatter

/**
 * Closing the day.
 *
 * The order is deliberate: what went well first, then what is still open. A
 * review that opens with a list of failures is one people stop reading, and the
 * product's whole stance is that recovering is normal rather than shameful.
 */
@Composable
fun NightReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NightReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    NesaScaffold(
        title = stringResource(R.string.review_title),
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
            val review = state.review
            if (review == null) {
                if (!state.loading) NoticeCard(text = stringResource(R.string.review_unavailable))
                return@NesaScaffold
            }

            Text(
                text = stringResource(
                    R.string.review_summary,
                    review.completed.size,
                    review.plannedCount
                ),
                style = MaterialTheme.typography.titleMedium
            )

            if (review.isSettled) {
                NoticeCard(
                    text = stringResource(R.string.review_settled),
                    emphasis = NoticeEmphasis.INFORMATION
                )
            }

            if (review.skipped.isNotEmpty()) {
                // Listed, never counted as a failure. A skip is a decision.
                Text(
                    text = stringResource(R.string.review_skipped, review.skipped.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (review.moved.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
                SectionHeader(title = stringResource(R.string.review_moved))
                review.moved.forEach { item ->
                    Text(
                        text = stringResource(
                            R.string.review_moved_item,
                            item.title,
                            item.block.start.format(timeFormat)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (review.missed.isNotEmpty() || review.deferred.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
                SectionHeader(title = stringResource(R.string.review_open))
            }

            (review.deferred + review.missed).forEach { entry ->
                ReviewCard(
                    entry = entry,
                    timeFormat = timeFormat,
                    onAccept = { viewModel.onAccept(entry.item, entry.suggestion) },
                    onDismiss = { viewModel.onDismiss(entry.item) }
                )
            }

            if (review.tomorrowAnchors.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = NesaSpacing.sm))
                SectionHeader(title = stringResource(R.string.review_tomorrow))
                review.tomorrowAnchors.forEach { anchor ->
                    Text(
                        text = stringResource(
                            R.string.review_moved_item,
                            anchor.title,
                            anchor.block.start.format(timeFormat)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }
}

@Composable
private fun ReviewCard(
    entry: ReviewItem,
    timeFormat: DateTimeFormatter,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NesaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.xs)
        ) {
            Text(text = entry.item.title, style = MaterialTheme.typography.titleSmall)

            Text(
                text = entry.suggestion.describe(timeFormat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // A refusal offers no action, because there is nothing honest to
            // offer. The user can still edit the activity from the timeline.
            if (entry.suggestion !is RescheduleSuggestion.NoRoom) {
                Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                    FilledTonalButton(onClick = onAccept) {
                        Text(stringResource(R.string.review_accept))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.review_not_today))
                    }
                }
            }
        }
    }
}

@Composable
private fun RescheduleSuggestion.describe(timeFormat: DateTimeFormatter): String = when (this) {
    is RescheduleSuggestion.LaterToday ->
        stringResource(R.string.review_later_today, start.format(timeFormat))
    is RescheduleSuggestion.Tomorrow ->
        stringResource(R.string.review_tomorrow_at, start.format(timeFormat))
    RescheduleSuggestion.LetItGo -> stringResource(R.string.review_let_go)
    is RescheduleSuggestion.NoRoom -> reason
}
