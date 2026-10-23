package com.parlor.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.core.data.*

@Composable
fun StorageRecoveryScreen(issue: StorageIssue, reset: SettingsReset, onRetry: () -> Unit,
                          onReset: () -> Unit, onClose: () -> Unit) {
    var confirming by rememberSaveable(issue) { mutableStateOf(false) }
    val busy = reset == SettingsReset.WORKING
    val canReset = issue == StorageIssue(StorageArea.SETTINGS, StorageProblem.DAMAGED)
    BackHandler { if (confirming && !busy) confirming = false else onClose() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)
        .testTag("storage-recovery"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(if (confirming) R.string.storage_reset_title else R.string.storage_title),
            style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(when {
            confirming -> R.string.storage_reset_detail
            issue.problem == StorageProblem.NEWER_VERSION -> R.string.storage_newer
            issue.area == StorageArea.SETTINGS -> R.string.storage_settings
            issue.problem == StorageProblem.DAMAGED -> R.string.storage_damaged
            else -> R.string.storage_unavailable
        }))
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.storage_reset_working), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (reset == SettingsReset.FAILED) {
            Text(stringResource(R.string.storage_reset_failed), Modifier.testTag("storage-reset-error")
                .semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (confirming && canReset) {
            Button(onClick = { confirming = false; onReset() }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-reset-confirm")) {
                Text(stringResource(R.string.storage_reset_confirm))
            }
            OutlinedButton(onClick = { confirming = false }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-reset-cancel")) {
                Text(stringResource(R.string.storage_keep_settings))
            }
        } else {
            Button(onClick = onRetry, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-retry")) {
                Text(stringResource(R.string.retry))
            }
            if (canReset) OutlinedButton(onClick = { confirming = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-reset")) {
                Text(stringResource(R.string.storage_reset))
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-close")) {
            Text(stringResource(R.string.close))
        }
    }
}
