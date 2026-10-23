package com.parlor.puzzles

import com.parlor.core.design.FoldAwareAlertDialog

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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Deletion remains available without Pro and never alters the user's original image. */
@Composable
fun PhotosScreen(onDelete: suspend () -> Unit, onBack: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.photos_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.photos_body))
        Text(stringResource(R.string.photos_backups))
        OutlinedButton(onClick = { confirm = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("photos-delete")) { Text(stringResource(R.string.photos_delete)) }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.photos_deleting), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        outcome?.let {
            Text(stringResource(if (it == "done") R.string.photos_deleted else R.string.photos_error),
                color = if (it == "done") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("photos-outcome").semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (confirm) FoldAwareAlertDialog(onDismissRequest = { if (!busy) confirm = false },
        title = { Text(stringResource(R.string.photos_confirm_title)) },
        text = { Text(stringResource(R.string.photos_confirm_body),
            Modifier.verticalScroll(rememberScrollState()).testTag("photos-delete-detail")) },
        confirmButton = { TextButton(onClick = {
            if (!busy) {
                busy = true; outcome = null; confirm = false
                scope.launch {
                    try { onDelete(); outcome = "done" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { outcome = "error" }
                    finally { busy = false; confirm = false }
                }
            }
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("photos-confirm")) { Text(stringResource(R.string.photos_delete)) } },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.photos_cancel)) } })
}
