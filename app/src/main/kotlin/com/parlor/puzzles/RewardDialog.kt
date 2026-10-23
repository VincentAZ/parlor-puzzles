package com.parlor.puzzles

import com.parlor.core.design.FoldAwareAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.core.model.Reward
import com.parlor.core.platform.ads.*
import com.parlor.core.design.rememberDisplayFormats

@Composable
internal fun RewardDialog(state: RewardAdState, delivery: RewardDeliveryState, available: Boolean,
                          viewsToday: Int, actionBusy: Boolean = false, actionError: Boolean = false,
                          onWatch: () -> Unit, onClose: () -> Unit, onRetrySave: () -> Unit, onContinue: () -> Unit) {
    val reward = state.reward
    val stage = state.stage
    val canWatch = stage in setOf(RewardStage.OFFER, RewardStage.UNAVAILABLE, RewardStage.CANCELED)
    val loading = stage in setOf(RewardStage.LOADING, RewardStage.SAVING) || actionBusy
    val title = stringResource(when (reward) {
        Reward.Hints -> R.string.reward_hints_title
        is Reward.LevelAccess -> R.string.reward_level_title
        is Reward.DailyRetry -> R.string.reward_daily_title
    })
    FoldAwareAlertDialog(onDismissRequest = { if (!actionBusy) onClose() }, title = { Text(title) },
        modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 600.dp),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val message = when (stage) {
                    RewardStage.OFFER -> if (!available && !delivery.error) stringResource(R.string.reward_unavailable) else when (reward) {
                        Reward.Hints -> stringResource(R.string.reward_hints_body)
                        is Reward.LevelAccess -> stringResource(R.string.reward_level_body)
                        is Reward.DailyRetry -> stringResource(R.string.reward_daily_body, rememberDisplayFormats().date(reward.date))
                    }
                    RewardStage.LOADING -> stringResource(R.string.reward_loading)
                    RewardStage.SHOWING -> stringResource(R.string.reward_showing)
                    RewardStage.SAVING -> stringResource(R.string.reward_saving)
                    RewardStage.EARNED -> stringResource(when (reward) {
                        Reward.Hints -> R.string.reward_hints_earned
                        is Reward.LevelAccess -> R.string.reward_level_earned
                        is Reward.DailyRetry -> R.string.reward_daily_earned
                    })
                    RewardStage.UNAVAILABLE -> stringResource(R.string.reward_unavailable)
                    RewardStage.LIMIT_REACHED -> stringResource(R.string.reward_limit)
                    RewardStage.CANCELED -> stringResource(R.string.reward_canceled)
                    RewardStage.SAVE_ERROR -> stringResource(if (delivery.durable) R.string.reward_saved_retry else R.string.reward_unsaved_retry)
                }
                Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (canWatch) Text(stringResource(R.string.reward_daily_count, viewsToday.coerceIn(0, 10), 10))
                if (delivery.error && stage != RewardStage.SAVE_ERROR) {
                    Text(stringResource(if (delivery.durable) R.string.reward_saved_retry else R.string.reward_recovery_error))
                }
                if (actionError) Text(stringResource(R.string.open_error), color = MaterialTheme.colorScheme.error)
                if (loading) CircularProgressIndicator()
            }
        },
        confirmButton = {
            when {
                delivery.error || stage == RewardStage.SAVE_ERROR -> TextButton(onClick = onRetrySave, enabled = !delivery.checking, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reward_retry_save))
                }
                stage == RewardStage.EARNED -> TextButton(onClick = onContinue, enabled = !actionBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(when (reward) {
                        Reward.Hints -> R.string.pro_prompt_continue
                        is Reward.LevelAccess -> R.string.reward_play_level
                        is Reward.DailyRetry -> R.string.reward_start_retry
                    }))
                }
                canWatch -> TextButton(onClick = onWatch, enabled = available && delivery.canStart, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reward_watch))
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !actionBusy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (stage == RewardStage.LOADING) R.string.reward_cancel else R.string.close))
        } })
}

@Composable
internal fun RewardRecoveryCard(state: RewardDeliveryState, onRetry: () -> Unit) {
    if (!state.error) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (state.durable) R.string.reward_saved_retry else R.string.reward_recovery_error))
            TextButton(onClick = onRetry, enabled = !state.checking, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reward_retry_save)) }
        }
    }
}
