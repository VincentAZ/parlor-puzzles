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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlor.core.data.*
import java.time.LocalDate

@Composable
internal fun BackupRoute(backups: ManualBackup, pro: Boolean, onBack: () -> Unit, onPro: () -> Unit,
                         exportName: String = "parlor-${LocalDate.now()}.json") {
    val state by backups.state.collectAsStateWithLifecycle()
    var pickerError by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(backups::export)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(backups::select) }
    BackupScreen(state, true, pickerError, onBack, onPro,
        onExport = { pickerError = false; try { create.launch(exportName) } catch (_: Exception) { pickerError = true } },
        onImport = { pickerError = false; try { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) } catch (_: Exception) { pickerError = true } },
        onRecovery = backups::previewRecovery, onConfirm = backups::confirm, onDiscard = backups::discard)
}

@Composable
internal fun BackupScreen(state: BackupUiState, pro: Boolean, pickerError: Boolean = false, onBack: () -> Unit,
                          onPro: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onRecovery: () -> Unit,
                          onConfirm: () -> Unit, onDiscard: () -> Unit) {
    val importing = state.phase == BackupPhase.IMPORTING
    BackHandler(importing) { }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack, enabled = !importing, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.backup_body))
        Text(stringResource(R.string.backup_private), style = MaterialTheme.typography.bodyLarge)
        if (state.busy) {
            CircularProgressIndicator()
            Text(stringResource(when (state.phase) {
                BackupPhase.IMPORTING -> R.string.backup_importing
                BackupPhase.EXPORTING -> R.string.backup_exporting
                else -> R.string.backup_reading
            }))
        }
        if (state.phase == BackupPhase.EXPORTED || state.phase == BackupPhase.IMPORTED) {
            Text(stringResource(if (state.phase == BackupPhase.EXPORTED) R.string.backup_exported else R.string.backup_imported))
        }
        if (pickerError || state.phase == BackupPhase.ERROR) Text(stringResource(when (state.problem) {
            BackupProblem.PRO_REQUIRED -> R.string.backup_pro_required
            BackupProblem.REWARD_PENDING -> R.string.backup_reward_pending
            else -> R.string.backup_file_error
        }), color = MaterialTheme.colorScheme.error)
        val preview = state.preview.takeIf { state.phase == BackupPhase.PREVIEW }
        if (preview != null) {
            Text(stringResource(if (preview.recovery) R.string.backup_recovery_title else R.string.backup_preview_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.backup_created, rememberDisplayFormats().timestamp(preview.createdAtMs)))
            val numbers = rememberDisplayFormats()
            Text(stringResource(R.string.backup_summary, numbers.integer(preview.levels), numbers.integer(preview.unfinished), numbers.integer(preview.stars)))
            Text(stringResource(R.string.backup_photos, numbers.integer(preview.photos)))
            Text(stringResource(R.string.backup_replace_detail))
            Button(onClick = onConfirm, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (preview.recovery) R.string.backup_recover_confirm else R.string.backup_import_confirm))
            }
            OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_cancel)) }
        } else if (!state.busy) {
            run {
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_import)) }
            }
            if (state.canRecover) {
                HorizontalDivider()
                Text(stringResource(R.string.backup_recovery_detail))
                OutlinedButton(onClick = onRecovery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_recovery)) }
            }
        }
    }
}

@Composable
internal fun BackupFinishScreen(working: Boolean = false, onRetry: () -> Unit) {
    BackHandler { }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.backup_finish_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(if (working) R.string.backup_importing else R.string.backup_finish_detail))
        if (working) CircularProgressIndicator()
        else Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
    }
}
