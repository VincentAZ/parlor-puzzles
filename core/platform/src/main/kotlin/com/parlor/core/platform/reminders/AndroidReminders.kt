package com.parlor.core.platform.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.os.Build
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import com.parlor.core.platform.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface RemindersOwner { val reminders: AndroidReminders }

class AndroidReminderDevice(private val context: Context, private val openDaily: () -> Intent) : ReminderDevice {
    companion object {
        const val CHANNEL = "puzzle-reminders-v1"
        const val TAG = "parlor-reminder"
        const val ID = 7140
        const val DUE = "com.parlor.puzzles.action.REMINDER_DUE"
        const val OPEN_DAILY = "com.parlor.puzzles.action.OPEN_DAILY_REMINDER"
        const val DATE = "challenge_date"
    }
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun ensureChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.reminder_channel_description)
            setShowBadge(false)
        })
    }
    override fun prepare(enabled: Boolean) {
        if (enabled) ensureChannel()
        val component = ComponentName(context, ReminderReceiver::class.java)
        val desired = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) != desired)
            context.packageManager.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
    }
    override fun permitted(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    override fun schedule(alarm: ReminderAlarm?) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(DUE)
        if (alarm == null) {
            PendingIntent.getBroadcast(context, ID, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarms.cancel(it); it.cancel()
            }
        } else {
            intent.putExtra(DATE, alarm.date)
            val pending = PendingIntent.getBroadcast(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // One local-calendar alarm. RTC does not wake a sleeping device; no exact-alarm access is requested.
            alarms.setWindow(AlarmManager.RTC, alarm.atMillis, 60 * 60 * 1000L, pending)
        }
    }
    override fun hide() { notifications.cancel(TAG, ID) }
    override fun show(kind: ReminderKind, date: String, expiresAtMillis: Long) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!permitted()) return
        val pending = PendingIntent.getActivity(context, ID, openDaily().setAction(OPEN_DAILY)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = context.getString(if (kind == ReminderKind.DAILY) R.string.reminder_daily_title else R.string.reminder_streak_title)
        val body = context.getString(if (kind == ReminderKind.DAILY) R.string.reminder_daily_body else R.string.reminder_streak_body)
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setTimeoutAfter((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1)).build()
        notifications.notify(TAG, ID, notification)
    }
}

class AndroidReminders(context: Context, private val scope: CoroutineScope, progress: ReminderProgress,
    foreground: () -> Boolean, openDaily: () -> Intent) {
    private val device = AndroidReminderDevice(context, openDaily)
    private val coordinator = ReminderCoordinator(DeviceReminderStore(context), device, progress, foreground = foreground)
    val state = coordinator.state
    fun preparePermission() = device.ensureChannel()
    fun refresh() = scope.launch { coordinator.refresh() }
    fun update(change: (ReminderPreferences) -> ReminderPreferences) = scope.launch { coordinator.update(change) }
    fun receive(date: String?, finished: () -> Unit) = scope.launch {
        try { if (date == null) coordinator.refresh() else coordinator.deliver(date) }
        finally { finished() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(AndroidReminderDevice.DUE, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val owner = context.applicationContext as? RemindersOwner ?: return
        val pending = goAsync()
        owner.reminders.receive(if (intent.action == AndroidReminderDevice.DUE) intent.getStringExtra(AndroidReminderDevice.DATE) else null,
            pending::finish)
    }
}
