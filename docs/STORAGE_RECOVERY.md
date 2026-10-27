> **Historical record:** this document predates the [fully free edition](FREE_EDITION.md). Pro, ads, billing and reward limits described below no longer apply.

# Unreadable local storage

Storage read failures now open a recovery screen instead of escaping a UI
coroutine or leaving the app indefinitely loading. Room and settings readers
report a typed failure without emitting an invented empty save. Retry creates
fresh subscriptions; a late failure from an older subscription cannot replace
the new result. Coroutine cancellation, fatal errors and errors in a flow's
consumer are not treated as damaged storage.

## Progress database

The Room open helper supplies its own SQLite corruption callback. It does not
invoke AndroidX's default callback, which deletes database files, and it disables
the helper's data-loss recovery option. A database with a newer `user_version`
is refused before Room attempts a downgrade or checks its current schema.
Existing Room 1–3 migrations remain registered; the current version stays 4.

The recovery screen offers Retry and Close. A newer save asks for a newer app;
an unreadable save explains that its files have been kept. Neither action resets
progress, issues fresh rewards, or changes entitlement. Recovery/loading placement
suppresses banners and consent forms. A failed live attempt observer also pauses
input and retains the last decoded board until a successful retry.

Campaign bests are validated after JSON decoding, before observation, access
checks, completion updates or export. Database reads and backup imports share
the same star, point, time, move and completion-date bounds. The decoded level ID
must also match its database row. Readable JSON with invalid field values enters
the damaged-save gate without changing that row or emitting empty progress.
Retry rereads the saved values; it cannot repair them by itself. A malformed best
encountered during completion rolls back the transaction, including its receipt,
board/time and rewards. Existing valid independent bests remain unchanged.

For a damaged progress database, confirmed Pro owners can choose a backup through
the system document picker. Validation and preview do not open or change the
damaged database. Unsupported files, missing/corrupt/future reward safeguards,
temporary I/O problems and newer database versions cannot authorize replacement.
Preserving a file does not establish that its contents can be recovered. Clearing
app data or uninstalling removes local saves.

## Restoring a damaged database

After explicit confirmation, an application-owned operation drains progress
transactions, closes the current Room instance and builds a separate database.
The stable database owner lets existing repositories, rewards and reminders use
the selected database without retaining a closed Room instance. Imported rows,
photos and the pending-settings marker commit together, with the independent
reward safeguards retained. The prepared file uses a rollback journal and is
closed and synced before selection. Pro access is checked when choosing the file,
on confirmation and immediately before the switch.

`parlor.db` and `parlor-recovered.db` alternate as active and previous storage.
`files/recovery/active-database.json` atomically selects the active slot. A missing
selector uses the original database for existing installs; malformed/future
selectors or missing selected files fail closed. An orphaned recovered file
cannot cause a missing original database to be created empty.

A process stopped before the selector switch still opens the previous save. The
uncommitted candidate is ignored; choosing and confirming the file again rebuilds
it. After the switch, startup opens the complete candidate and finishes its
pending settings before exposing Home. If the rename succeeds but a later sync
reports an error, the operation checks the visible selector and resumes the
selected complete database instead of repeating the import. This ordering retains the old file and selects a replacement only after it is
complete; physical power-loss testing remains required.

The damaged database is retained in its original slot, including its private
photos. Only one previous database is kept; a subsequent confirmed recovery can
replace the older copy. Photo deletion explains this and removes that previous
database as well as current/recovery photos and both staged previews. Ordinary
backup and recovery operations share a mutex with privacy deletion. Current moves,
bests and reward records survive photo deletion.

Preview confirmation is discarded on process restart and never authorizes an
automatic import. The staged file is removed when the recovery service is
recreated, and confirmed photo deletion also clears it. Cancel revokes confirmation
even if deleting that private file fails; the screen reports the cleanup failure
without importing or crashing. Close or activity recreation does not cancel already confirmed
work while the process lives. A process killed during preparation requires file
selection and confirmation again. Restored settings skip first-run onboarding;
device consent, purchase and reminder records remain independent. An unreadable
settings store still requires the existing separate reset confirmation before the
pending backup settings can finish.

Android automatic backup/transfer includes both database slots, their SQLite
sidecars, the selector and its older AtomicFile `.bak`. Unfinished `.new` files
remain excluded. Keeping a previous database can increase automatic-backup size;
the Android quota and real device transfer must still be qualified.

## Independent recovery safeguards

The application now maintains `files/recovery/protection.json` separately from
Room. One application-owned instance serializes access. It records the reward
ledger, dated completion receipts and earned achievements. It contains no boards,
photos, settings, purchase tokens or consent.
This is a prerequisite for importing into a damaged database: unreadable Room
cannot be the only source of the claims an import must retain.

Before a transaction can commit a player/reward change, it writes the corresponding
safeguards with AtomicFile, explicitly syncs the file, verifies the committed
bytes and syncs the containing directory and its parent. An unchanged record
avoids another file write. Failed writes/read-backs invalidate the cached proof;
Retry must read and validate the disk record again. A preservation failure
throws inside the Room transaction, rolls back that transaction and enters the
storage recovery gate. Startup bootstraps existing saves from the readable ledger,
completion receipts and sticky hint flags in unfinished attempts. Legacy imported
hint flags are added before import commits, without waiting for another launch.

Safeguard updates retain the largest spending/count/date values, the lower
same-date hint allowance, and the union of completion, reward, hint and achievement
claims. Ordinary confirmed imports also consult this file, so a readable older
database cannot make repeated imports refill a later spent balance. Neither
stored balances nor imported progress grant Pro.

The ordering is deliberately conservative: a safeguard write may succeed before
a later SQL statement fails. Room then rolls back while that safeguard stays
ahead. A later confirmed import may therefore withhold an uncommitted hint/retry
or completion claim; it cannot issue it twice. This file is not a second progress
backup and cannot reconstruct a lost board. Missing files can be bootstrapped only
from readable current progress; recovery reads fail on missing, malformed or
future-version safeguards instead of inventing an empty ledger.

Android backup/transfer allowlists the main file and the `.bak` used by older
AtomicFile implementations. Unfinished `.new` files are excluded. Automatic
transfer and physical power/storage failure still require device qualification.
The restoration flow reads these safeguards before preparing its candidate. Its
write-ahead updates can remain conservatively ahead even if candidate preparation
or the selector switch later fails; retry never adds balances or reopens claims.

The directory sync follows the distinction in the
[Linux fsync contract](https://man7.org/linux/man-pages/man2/fsync.2.html): syncing
file contents does not by itself persist the directory entry. Android exposes
this through [Os.fsync](https://developer.android.com/reference/android/system/Os#fsync(java.io.FileDescriptor)).

## Settings-only reset

Only a settings decoding/corruption failure offers **Reset unreadable settings**.
A temporary I/O failure or a progress/database failure does not offer that action.
The separate confirmation lists the preferences being reset and leaves Cancel
available. Defaults restore Light Linen, the default sound/haptic/motion/timer
choices and unseen puzzle tutorials. Main onboarding stays completed.

After confirmation, an application-owned coroutine serializes with settings
writes. It first copies the exact settings file with `AtomicFile` into
`no_backup/settings-recovery/previous.preferences_pb`. Only after that succeeds
and a read-back SHA-256 check matches the original bytes does the DataStore
corruption handler have permission to replace malformed
protobuf. The handler supplies the final default preferences directly; valid
protobuf containing unreadable JSON is updated through the normal transaction.
If preserving the file fails, replacement is not authorized. A failed reset stays
on the recovery screen and can be retried. Activity recreation or Close does not
cancel confirmed work; after process death, committed defaults can be reopened.

One previous settings file is retained privately, excluded from Android automatic
backup. A subsequent confirmed reset replaces that copy. It contains no puzzle
photos, progress database, purchase tokens or device reminder records. This is
a preservation copy, not a user-import interface or a grant of Pro backup access.
Reset does not alter Room, purchases, consent, earned rewards or reminder opt-in.
An already confirmed backup import can still finish its pending settings marker
on the next startup.

## Verification and limits

The new storage fixtures inject a corrupt on-disk SQLite header, a future database
version with retained records, malformed progress JSON, malformed DataStore
protobuf and unreadable settings JSON. They exercise repeated reads, retry after
repair, exact preservation bytes, store reopen and failure to create the private
copy. A failed initial database read must not claim that a backup import has
committed. Existing migration, import and pending-settings checks run alongside
these cases.

Native compact UI fixtures check confirmation, cancellation, failure, busy-state
controls and 48-dp targets at Android's actual 200% text setting. Cold-process
smokes separately inject test-app files and operate actual recovery controls.
The original read-recovery packet is storage-recovery.
Independent safeguard persistence is recorded in
recovery-protection. The subsequent
database-replacement work is tracked in database-recovery.
Campaign best field/identity checks, retained rows, completion rollback and actual
Activity retry coverage are in saved-best validation.

Emulator injection does not qualify physical disk exhaustion, failing flash,
manufacturer restore behavior or human TalkBack use. Those checks, automatic
restore across devices and production recovery coverage remain open.

Interrupted imports also accept older settings encodings. Completion decodes the
currently stored marker and compares its settings values with those just applied,
then conditionally clears those exact stored bytes in the same Room transaction.
Missing newly defaulted fields, whitespace and key ordering no longer leave an
otherwise applied import stuck. A marker containing different settings, malformed
JSON or unsupported fields remains pending. A failed SQL commit also retains the
marker for retry. The previous raw comparison with re-encoded JSON could strand
older imports; the cold-process fixture exposed that limitation.
The fix and its affected-suite scope are recorded in
legacy settings.

Primary references:
[Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore),
[corruption handler contract](https://developer.android.com/reference/androidx/datastore/core/CorruptionHandler),
and [AndroidX SQLite callback implementation](https://android.googlesource.com/platform/frameworks/support/+/e446b28b091a3a58813c2d169de4f629552cb3ac/sqlite/sqlite/src/main/java/androidx/sqlite/db/SupportSQLiteOpenHelper.kt).

The preservation check also accounts for
[Android AtomicFile's logged sync/rename failures](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/util/AtomicFile.java).
After `finishWrite`, verification errors must not call `failWrite` on that finished
session: the [API 26 implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/util/AtomicFile.java)
deletes the committed base during rollback. Recovery/settings preservation now
ends the write session before read-back verification.
