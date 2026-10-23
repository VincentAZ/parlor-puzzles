package com.parlor.feature.play

import com.parlor.core.design.FoldAwareAlertDialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.parlor.core.data.PhotoCodec
import com.parlor.core.model.Attempt
import kotlinx.coroutines.CancellationException

internal data class PhotoRender(val id: String? = null, val bitmap: ImageBitmap? = null, val failed: Boolean = false)

@Composable
internal fun savedPhoto(viewModel: PlayViewModel, id: String?, pro: Boolean): PhotoRender {
    val loaded by produceState(PhotoRender(), id, pro) {
        value = PhotoRender()
        if (id != null && pro) {
            value = try {
                val bitmap = viewModel.loadPhoto(id)?.asImageBitmap()
                PhotoRender(id, bitmap, bitmap == null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { PhotoRender(id, failed = true) }
        }
    }
    return if (pro && loaded.id == id) loaded else PhotoRender()
}

@Composable
internal fun PhotoControls(attempt: Attempt, state: PhotoEditState, viewModel: PlayViewModel,
                           pro: Boolean, enabled: Boolean, failed: Boolean, onPro: (() -> Unit)?) {
    val context = LocalContext.current.applicationContext
    var offeredAttempt by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = offeredAttempt; offeredAttempt = null
        if (uri != null && id != null) viewModel.preparePhoto(id) {
            require(uri.scheme == "content")
            requireNotNull(context.contentResolver.openInputStream(uri)).use { PhotoCodec.normalize(it, context.cacheDir) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.play_photo_heading), style = MaterialTheme.typography.titleMedium)
        if (!pro) Text(stringResource(if (attempt.photoId == null) R.string.play_photo_pro else R.string.play_photo_retained))
        if (failed) Text(stringResource(R.string.play_photo_missing), color = MaterialTheme.colorScheme.error)
        if (state.error) Text(stringResource(R.string.play_photo_error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("photo-error").semantics { liveRegion = LiveRegionMode.Polite })
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.play_photo_working))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pro) OutlinedButton(onClick = {
                offeredAttempt = attempt.id
                try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: Exception) { offeredAttempt = null; viewModel.preparePhoto(attempt.id) { error("Picker unavailable") } }
            }, enabled = enabled && !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("photo-choose")) {
                Text(stringResource(if (attempt.photoId == null) R.string.play_photo_choose else R.string.play_photo_change))
            } else if (onPro != null) OutlinedButton(onClick = { viewModel.leave(onPro) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.play_photo_open_pro)) }
            if (attempt.photoId != null) TextButton(onClick = viewModel::removePhoto, enabled = enabled && !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("photo-remove")) { Text(stringResource(R.string.play_photo_remove)) }
        }
        Text(stringResource(R.string.play_photo_local), style = MaterialTheme.typography.bodySmall)
    }
    if (state.preview != null && state.bitmap != null) FoldAwareAlertDialog(
        onDismissRequest = viewModel::discardPhoto,
        title = { Text(stringResource(R.string.play_photo_preview)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(state.bitmap.asImageBitmap(), stringResource(R.string.play_photo_description),
                    Modifier.fillMaxWidth().aspectRatio(1f).testTag("photo-crop"))
                Text(stringResource(R.string.play_photo_crop_detail))
            }
        },
        confirmButton = { TextButton(onClick = viewModel::confirmPhoto, enabled = pro && !state.busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("photo-confirm")) { Text(stringResource(R.string.play_photo_use)) } },
        dismissButton = { TextButton(onClick = viewModel::discardPhoto, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.play_photo_cancel)) } })
}
