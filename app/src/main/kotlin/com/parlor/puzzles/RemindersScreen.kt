package com.parlor.puzzles

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlor.core.platform.reminders.*
import com.parlor.core.design.rememberDisplayFormats

@Composable
internal fun RemindersRoute(reminders: AndroidReminders, onBack: () -> Unit) {
    val state by reminders.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionAttempted by rememberSaveable { mutableStateOf(false) }
    var actionError by rememberSaveable { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf<TimePickerDialog?>(null) }
    DisposableEffect(Unit) { onDispose { timePicker?.setOnDismissListener(null); timePicker?.dismiss() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionAttempted = true
        if (granted) reminders.update { it.copy(enabled = true) } else reminders.refresh()
    }
    RemindersScreen(state, permissionAttempted, actionError, onBack,
        onEnabled = { enabled ->
            actionError = false
            if (!enabled) reminders.update { it.copy(enabled = false) }
            else try {
                permissionAttempted = true
                reminders.preparePermission()
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else reminders.update { it.copy(enabled = true) }
            } catch (_: Exception) { actionError = true }
        },
        onKind = { kind -> reminders.update { it.copy(kind = kind) } },
        onTime = {
            val minute = state.preferences.minuteOfDay
            timePicker?.dismiss()
            timePicker = TimePickerDialog(context, { _, h, m -> reminders.update { it.copy(minuteOfDay = h * 60 + m) } },
                minute / 60, minute % 60, DateFormat.is24HourFormat(context)).apply {
                setOnDismissListener { timePicker = null }; show()
            }
        },
        onSystemSettings = {
            actionError = false
            try { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            catch (_: Exception) { actionError = true }
        }, onRetry = { actionError = false; reminders.refresh() })
}

@Composable
internal fun RemindersScreen(state: ReminderUiState, permissionAttempted: Boolean = false, actionError: Boolean = false,
    onBack: () -> Unit, onEnabled: (Boolean) -> Unit, onKind: (ReminderKind) -> Unit, onTime: () -> Unit,
    onSystemSettings: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val time = rememberDisplayFormats().clock(state.preferences.minuteOfDay, DateFormat.is24HourFormat(context))
    val editable = state.ready && !state.busy && !state.error
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.reminders_body))
        if (!state.ready || state.busy) CircularProgressIndicator()
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.preferences.enabled,
            enabled = state.ready && !state.busy && (!state.error || state.preferences.enabled),
            role = Role.Switch, onValueChange = onEnabled), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders_enable), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.preferences.enabled, onCheckedChange = null)
        }
        Text(stringResource(R.string.reminders_type), style = MaterialTheme.typography.titleMedium)
        for (kind in ReminderKind.entries) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
            state.preferences.kind == kind, enabled = editable, role = Role.RadioButton, onClick = { onKind(kind) }),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = state.preferences.kind == kind, onClick = null)
            Text(stringResource(if (kind == ReminderKind.DAILY) R.string.reminders_daily else R.string.reminders_streak), Modifier.weight(1f))
        }
        Text(stringResource(if (state.preferences.kind == ReminderKind.DAILY) R.string.reminders_daily_detail else R.string.reminders_streak_detail))
        OutlinedButton(onClick = onTime, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.reminders_time, time))
        }
        Text(stringResource(R.string.reminders_timing))
        Text(stringResource(R.string.reminders_skip))
        Text(stringResource(R.string.reminders_device))
        if (((permissionAttempted || state.preferences.enabled) && !state.permitted) || state.error) {
            if (!state.permitted) Text(stringResource(R.string.reminders_permission))
            OutlinedButton(onClick = onSystemSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.reminders_android_settings))
            }
        }
        if (state.error || actionError) {
            Text(stringResource(R.string.reminders_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
        }
    }
}
