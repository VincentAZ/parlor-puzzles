package com.parlor.puzzles

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.parlor.core.design.rememberDisplayFormats
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlor.core.data.*

@Composable
internal fun DatabaseRecoveryRoute(recovery: DamagedDatabaseRecovery, pro: Boolean, onRetry: () -> Unit, onClose: () -> Unit) {
    val state by recovery.state.collectAsStateWithLifecycle()
    var pickerError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(recovery::select) }
    DatabaseRecoveryScreen(state, true, pickerError, onRetry, onClose,
        onChoose = {
            pickerError = false
            try { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            catch (_: Exception) { pickerError = true }
        }, onConfirm = { recovery.confirm() }, onDiscard = { recovery.discard() })
}

@Composable
internal fun DatabaseRecoveryScreen(state: DatabaseRecoveryState, pro: Boolean, pickerError: Boolean = false,
                                    onRetry: () -> Unit, onClose: () -> Unit, onChoose: () -> Unit,
                                    onConfirm: () -> Unit, onDiscard: () -> Unit) {
    val preview = state.preview.takeIf { state.phase == DatabaseRecoveryPhase.PREVIEW }
    BackHandler { if (preview != null) onDiscard() else onClose() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)
        .testTag("storage-recovery"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(if (preview != null) R.string.database_restore_title else R.string.storage_title),
            style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (preview != null) R.string.database_restore_detail else R.string.storage_damaged))
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(if (state.phase == DatabaseRecoveryPhase.RESTORING) R.string.database_restoring else R.string.backup_reading),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (pickerError || state.phase == DatabaseRecoveryPhase.ERROR) {
            Text(stringResource(when (state.problem) {
                DatabaseRecoveryProblem.PRO_REQUIRED -> R.string.database_restore_pro
                DatabaseRecoveryProblem.SAFEGUARDS -> R.string.database_restore_safeguards
                DatabaseRecoveryProblem.FILE -> R.string.backup_file_error
                else -> if (pickerError) R.string.backup_file_error else R.string.database_restore_failed
            }), Modifier.testTag("database-recovery-error").semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error)
        }
        if (preview != null) {
            Text(stringResource(R.string.backup_created, rememberDisplayFormats().timestamp(preview.createdAtMs)))
            val numbers = rememberDisplayFormats()
            Text(stringResource(R.string.backup_summary, numbers.integer(preview.levels), numbers.integer(preview.unfinished), numbers.integer(preview.stars)))
            Text(stringResource(R.string.backup_photos, numbers.integer(preview.photos)))
            Text(stringResource(R.string.database_restore_claims))
            Button(onClick = onConfirm, enabled = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("database-restore-confirm")) {
                Text(stringResource(R.string.database_restore_confirm))
            }
            OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("database-restore-cancel")) {
                Text(stringResource(R.string.backup_cancel))
            }
        } else {
            Button(onClick = onRetry, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-retry")) {
                Text(stringResource(R.string.retry))
            }
            OutlinedButton(onClick = onChoose, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("database-restore-choose")) {
                Text(stringResource(R.string.backup_import))
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage-close")) {
            Text(stringResource(R.string.close))
        }
    }
}
