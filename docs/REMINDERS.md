# Local reminders

Settings → Reminders offers **Daily puzzle** or **Keep a streak going**, with a
chosen local time (initially 7:00 PM). Reminders start off. Choosing a kind or time
does not turn them on. On Android 13 and later, turning on Remind me requests
notification permission in that context; declining leaves the preference off.
Android notification settings remain available when permission is blocked or
local reminder storage fails. An existing opt-in blocked by Android is shown as
blocked, without silently changing the player's choice.

There is one shared delivery limit across both kinds: at most one posting attempt
per local calendar date. Neither kind is promotional. Both skip a date with a
qualifying puzzle completion, including campaign replays and older Daily
completions credited that day. Streak reminders also require yesterday's completion
credit. Opening the app removes a displayed reminder. The app suppresses delivery
while MainActivity is visible, including a 750-ms departure grace period that
avoids a gap during Activity recreation.

## Scheduling and delivery

- The adapter uses a one-shot `AlarmManager.setWindow` with a one-hour window and
  `RTC`, which does not wake a sleeping device. The manifest requests neither
  exact-alarm access nor an app wake lock. Android can defer delivery further while
  idle or under battery restrictions; a force-stopped app cannot promise reminders.
- A private, initially disabled receiver handles the due callback, reboot, clock
  and timezone changes, and package replacement. Opt-in enables the receiver;
  disabling cancels its alarm and notification. A boot callback recalculates the
  next local time. The next alarm is installed before a notification is posted.
- Every callback rechecks the local date, chosen time, permission, foreground
  state, completion credit and durable daily claim. An expired date does not get
  a catch-up alert. If the chosen time has passed during a refresh, the next alarm
  is for a later date. A DST gap uses the next valid local time; an overlap cannot
  send twice. A timezone change retains the selected hour and minute.
- The channel starts at `IMPORTANCE_LOW`, with no default sound or badge. Android
  lets the user change channel behavior. Each post includes a timeout calculated
  to the next local midnight at posting. It contains no personal progress or
  advertising text.
- The notification's immutable, explicit Activity intent opens **today's** Daily
  Challenge after content and onboarding are ready. A saved pending request
  survives Activity recreation and is consumed after navigation. Existing Android
  Back behavior is retained. Opening a reminder does not award completion credit.

These choices follow Android's guidance for
[inexact alarms and reboot rescheduling](https://developer.android.com/develop/background-work/services/alarms/schedule),
[notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
[channels](https://developer.android.com/develop/ui/compose/notifications/channels)
and [notification navigation](https://developer.android.com/develop/ui/views/notifications/navigation).

## Persistence and failure behavior

`noBackupFilesDir/reminders-v1.json` is an AtomicFile containing versioned reminder
preferences and the latest claimed delivery date. It is bounded to 4 KiB and uses
strict UTF-8 and schema validation. It is excluded from manual exports, Android
Auto Backup and device transfer. A new install starts off even if restored progress
contains a legacy `PlayerSettings.notifications` value. That old serialized field
remains for file compatibility; it is not the delivery preference.

Before posting, delivery saves its date claim. A process death or notification
failure after this write may miss that day's alert; retrying cannot post a second
one. Turning reminders off and on, changing kind/time, importing a progress file
or resetting progress cannot remove the claim. The high-water date also prevents
clock rollback from repeating a date; a clock moved far ahead can therefore delay
future reminders after it is corrected. This is a local duplicate safeguard,
not a trusted clock or protection against deliberate file tampering.

The final completion-credit check and notification post run in the same Room
transaction used for completion/import/reset. A completion committed first
suppresses the post; a completion queued behind delivery removes the reminder
when the app refreshes. A preference change invalidates a delivery waiting for its
private claim write. Permission, foreground state, date and target time are checked
again after that write.

Corrupt records are not silently replaced. Storage or scheduling failures cancel
the current alarm, hide the notification and show a retry state. If an opt-out
cannot be persisted, the prior stored opt-in may remain; the UI keeps that state
visible and permits another disable attempt or opening Android notification
settings. Granting Android permission alone never opts in an off preference.

The background path reads local completion credits without resolving purchase
ownership, initializing ads or requesting a network service. Best-effort scheduling
does not promise an exact arrival time or delivery after an OS force-stop.

## Qualification scope

The JVM policy tests cover date limits, concurrent callbacks, DST/timezone/clock
changes, foreground/completion suppression, permission failures, interrupted
storage and the claim-before-post boundary. Android fixtures cover AtomicFile
recovery, malformed files, import/reset isolation, Room completion ordering,
notification navigation and compact 200% text. The passing API 26/API 36 matrix
and the scope of native permission, picker and reboot smoke checks are recorded
in the reminder evidence.

Physical idle/battery behavior, manufacturer scheduling differences, human
TalkBack navigation and long-running reminder delivery remain release
qualification work. Emulator and injected-clock results do not establish them.
