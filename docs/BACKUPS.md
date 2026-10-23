# Manual progress backup and restore

Settings → Progress backups opens Pro's file export/import. Android's system
document picker creates or selects the file; the app requests no broad storage
permission. A selected import is copied to private staging storage and validated
before a preview appears. Only **Replace progress with this backup** changes
progress. Cancelling the picker or preview leaves progress unchanged. The staged
bytes are used at confirmation even if the provider's original file changes.

Free or unresolved ownership cannot export/import a new file. Pro is checked
again at the storage transaction. Recovery of the app's own pre-import snapshot
remains available after Pro is lost, so undoing an import does not require a new
purchase. Neither path writes purchase storage or grants ownership. Billing
reconciliation runs separately after restoration; network failure cannot undo
locally cached Pro.

## File and validation contract

The current user-facing file format is UTF-8 JSON, `format: parlor-progress`, schema
4, with a unique snapshot ID, creation time and content version. Schema 2 remains
accepted without photos; schema 3 retains photos. Both legacy versions use Linen
and light appearance. Schema 4 adds all six theme IDs and explicit appearance. Schema 1 was an
earlier in-memory contract without a released manual export; it is not accepted
as an import. Future schemas and unavailable/newer content are rejected.

The file contains settings, attempts with board/undo/redo/timing/viewport/hint
flags, independent campaign bests, dated Daily records, completion IDs/dates,
achievements, summary totals and the reward ledger. Purchase tokens, entitlement
cache and UMP consent have no fields. Reminder opt-in, kind, chosen time and the
sent-date claim live separately in `noBackupFilesDir` and never enter an export.
The legacy serialized `PlayerSettings.notifications` field remains compatible but
does not control delivery. Import and recovery cannot enable reminders or clear
their daily limit; see [REMINDERS.md](REMINDERS.md).
Schemas 3 and 4 include each referenced puzzle photo exactly once, in 4,000-character
Base64 chunks. SHA-256, JPEG structure/metadata, square dimensions, decode and
payload limits are checked before replacement. Missing photos fail the entire
import. The preview includes the photo count; see [PHOTOS.md](PHOTOS.md).

Bounds are 32 MiB, 64 nesting levels, 4,096 raw characters per JSON string and
one million structural separators. Files can contain at most 20,000 attempt
records, 40,000 total history entries, 10,000 entries per attempt and 30 Daily
detail records. Exceeding a limit fails without replacing data. Streaming decode
avoids a second full UTF-16 copy of the input; strict UTF-8 validation precedes it.
Each serialized attempt and the combined player ledger/profile are limited to
1 MiB so individual progress rows fit Android's database cursor on API 26.
The combined recovery snapshot can exceed that size and is read in 64K-character
chunks; clearing its settings marker never loads the full snapshot into a cursor.

Validation checks known level IDs/revisions, immutable board geometry and engine
legality. Unfinished histories replay from the bundled initial state; every cost,
before/after pair and redo continuation must match the shipping engine. Completed
records must have a legal goal state and retained completion claim. Unknown
variants, malformed counters/dates, duplicate attempt IDs, unsupported settings,
inconsistent totals and invalid Daily references are rejected. Daily assignments
are checked against their frozen schedule epoch, preserving older opened epochs.
This is local consistency validation, not signed anti-cheat protection.

## Replacement and interruption recovery

Room schema 3 adds `backup_state` and schema 4 adds private photo rows; migrations
from schemas 1–3 preserve progress and existing recovery copies.
In one Room transaction, an import saves a complete previous-progress
snapshot, replaces attempts/bests/Daily/profile/image data, retains anti-duplication
records and records the settings that must be applied. If any SQL write fails,
all those changes roll back together. Only one pre-import recovery snapshot is
kept; a subsequent confirmed import replaces it.

DataStore is a separate transactional store. After Room commits, the application
applies the recorded settings, then clears the pending marker. An Activity can
go away without cancelling this work. A process killed between those steps
finishes the marker during startup before Home is exposed. If settings cannot
be written, a recovery screen blocks gameplay and offers retry. Reapplying the
same settings after an interruption is safe. A slow document read/export does
not hold a recreated Home behind the provider.

Unreadable Room/DataStore files have a separate recovery gate. It preserves
database files, refuses future database versions, and offers a confirmed,
preserved-copy settings reset only for settings corruption. See
[Storage recovery](STORAGE_RECOVERY.md). The pre-import snapshot described here
requires a readable Room database. The separate Pro damaged-database restore path
validates a chosen file, requires confirmation and prepares an inactive database.
An atomic selector switches to the complete file while retaining the damaged
database. Its pending settings finish before Home after a cold restart. An initial
database read failure is not labelled a committed import.

Import preview/error states do not mutate progress. A partial output file from a
failed provider write is never reported as a successful export and must pass the
same full validation before it can be imported. A pending earned-ad receipt must
finish recovery before an ordinary backup operation starts. Damaged-database
restoration cannot require that receipt to write to unreadable Room first; it
retains the independent safeguards, then lets reward delivery retry against the
recovered database. Duplicate reward IDs still cannot grant twice.

## Retained safeguards

- Completion IDs/dates are unioned with local claims. An older file cannot turn
  a locally completed attempt back into a score-eligible unfinished attempt.
- Hint use is sticky by attempt ID, including after ordinary progress resets.
  A restored board cannot clear a previously recorded hint penalty.
- Earned-hint spending has a cumulative counter. Imported balances subtract local
  spending absent from the file; repeated old-file imports cannot refill spent
  credits. Balances from two files are never added. Divergent device histories
  are treated conservatively; this is replacement, not cloud synchronization.
- The latest reset date, completed rewarded-view counts by date, spent Daily
  retry counts, completion claims and streak dates retain their high-water values.
  Same-date daily hints use the lower remaining allowance. Going backward in
  calendar time cannot issue another daily allowance or reopen the shared cap.
- Restored Daily eligibility is checked against those retained claims and current
  Pro. An exhausted retry becomes practice. Temporary access keeps its original
  wall-clock expiry; importing never starts a new 24-hour period.
- Earned achievements survive replacement. Campaign and Daily result totals come
  from the selected file rather than being added to current totals.

The application now also retains those claims in an independent, verified
write-ahead file. Confirmed imports merge its protection with readable local
progress before replacing rows. See [Independent recovery safeguards](STORAGE_RECOVERY.md#independent-recovery-safeguards)
for failure ordering and its conservative handling of a later SQL rollback. The
file provides no purchase authority and does not enable damaged-database import
by itself.

The private staged file is under `noBackupFilesDir`. The pre-import snapshot and
pending marker are inside the backed-up progress database, so a transferred
database can finish an interrupted import. Both use the progress/settings/photo
envelope. Privacy deletion removes current/recovery photos and staged inputs,
including the previous damaged database kept by file restoration. The deletion
confirmation explains that the previous damaged save is removed. Both database
slots, SQLite sidecars and the atomic selector are allowlisted for Android backup.
Unconfirmed preview files are discarded on process restart. Purchase and consent
storage retain their existing backup exclusions. Automatic Android cloud backup
has a separate 25 MB quota; large databases can exceed it even when a manual file
fits the 32 MiB limit.

Finishing an interrupted import compares settings values rather than JSON bytes
produced by the current serializer. Older markers may omit newly defaulted fields
or use different whitespace/key ordering. After the expected settings have been
applied, one Room transaction verifies the decoded current marker and conditionally
clears its exact stored encoding. Different settings, invalid/unsupported JSON or
a failed SQL commit keep the marker for retry; completed progress and the recovery
copy remain intact. The affected-suite reports and cold-process checks are in
legacy settings evidence.

## Reproduce checks

```sh
./gradlew :core:model:test :core:game:test :core:progress:test :core:data:testDebugUnitTest
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-parallel
```

The game test validates saved states from all 56 current bundled references.
Storage tests cover replacement/recovery, undo/redo/viewport preservation,
retained hints/caps/retries, invalid input, SQL rollback, a disk reopen between
progress and settings, injected settings-write failure, and a recovery snapshot
above 2 MiB with 5,000 completed attempts. The document test
uses the real local system picker to save, select, preview and restore a file;
only its Pro ownership is injected. It does not perform a Play purchase.

Remaining qualification includes arbitrary document providers and cancellation
conditions, physical low-storage/process-kill tests, human TalkBack, automatic
photo transfer and larger production catalogs/history. The full v1
device, beta and production gates still apply.

Primary references: [Android document access](https://developer.android.com/training/data-storage/shared/documents-files),
[DataStore transaction guidance](https://developer.android.com/topic/libraries/architecture/datastore),
and [Kotlin streaming JSON decode](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/decode-from-stream.html).
