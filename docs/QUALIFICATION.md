# Prototype qualification procedure

The app is still a development prototype. No physical performance, audio latency,
TalkBack usability, production SDK, closed-beta or release gate is certified.

## Fold and cutout qualification

[Fold layouts](FOLD_LAYOUTS.md) documents hinge-safe screens, two-pane play and
Material dialogs. Its focused native checks inject WindowManager geometry;
the API 36 cutout check uses Android's actual double-cutout overlay. Complete
physical folding, closed-cover, split-window, process termination, TalkBack and
provider-owned window checks before closing P7. The current local-testing
checkpoint does not claim a newly completed full regression matrix.

## Motion qualification

[Board motion and completion](MOTION.md) describes the 250 ms presentation layer,
verified Box Pusher walking paths, reduced motion and the one-time celebration.
Native API 26/36 frame tests cover all twelve families and actual Activity checks
cover saved-state ordering, Results ad placement and recreation during motion.

On physical phones/tablets, complete each family with motion on and reduced,
including the final move, undo/redo during a move, pause/background interruptions,
rapid input, photo tiles and a magnified viewport. Check that overview and detail
remain understandable together. Measure frame distributions on the named 2019
device and completion/action sound latency with the existing physical audio rig.
Check Android's animation-scale setting separately. With human TalkBack users,
verify focus and descriptions during the brief input pause and on Results, and
confirm that decorative confetti creates no announcement or extra focus target.
Recreation tests do not substitute for physical process termination and restoration.

## Locale and RTL qualification

[Locale and layout behavior](LOCALE_LAYOUTS.md) separates spatial puzzle
coordinates from interface direction. Native API 26/36 regression cases cover
Traffic Jam/Tiles/Hanoi/triangle-peg pointer drags, named river banks, all twelve
Expert renderers at 320 × 640 dp and 200% text, and persistent overview/pan controls.
Resource-locale fixtures verify Arabic digits, German separators, daily and backup
dates, 12/24-hour reminder display, live locale changes and English fallback
paragraph direction. Their screenshots and failures preceding the fixes are in
the local locale-rtl evidence packet.

Installed debug and minified API 36 checks also use the actual app-locale service:
English to Arabic retains a committed move, undo/redo and cold resume; completion
returns to the level list and a German locale retains the eight-move best. Native
campaign/Daily tests verify their return destinations through recreation. These
passes supplement the resource fixtures, with APK and report hashes retained.
The API 36 emulator clips or omits system-bar icons after an Arabic app-locale
change; the same finding reproduces in stock Android Settings at native display
size. Preserve that comparison and recheck it during physical locale qualification.

Finish physical locale changes during saved campaign/daily play, tablet rotation,
fold/unfold and service dialogs. Have human readers review mixed-direction text
and TalkBack reading/focus order, including long dates and counts. Renderer
semantics and emulator screenshots do not establish that human qualification.
Launch remains English; there is no translated resource catalog in this increment.

## Crash reporting qualification

[Crash reporting](CRASH_REPORTING.md) documents the separate internal package,
owner Firebase configuration, mapping upload and explicit test-crash procedure.
Development builds keep Firebase uninitialized. A passing local startup test,
configuration parser or compilation does not establish SDK delivery. Record a
real internal crash after reopening, its exact APK/build ID, uploaded mapping and
readable console trace. Qualify offline recovery, network destinations and SDK
background work on the physical matrix; check privacy/Data Safety against the
actual configuration. The 99.5% crash-free-session target requires the full first
14 days of production traffic and cannot be proved by synthetic crashes.

## Tablet play qualification

[Adaptive play](TABLET_PLAY.md) describes the landscape board/control panes and
portrait/narrow-window fallback. Native emulator cases on API 26/36 cover twelve
Expert renderers at 200% text, representative 48-dp targets, independent scrolling,
and an actual Traffic Jam rotation/recreation/solve flow. The evidence packet
separates renderer fixtures from actual Activity navigation and earned completions.
Complete every puzzle/tutorial with human TalkBack on physical tablets in both
orientations. Include 600-dp windows, short split-screen windows, cutouts, hinges,
fold/unfold, magnified overviews, focus restoration and blocking system surfaces.
Verify saved selection, history, hint state and active time after interruption;
an emulator display override does not establish physical usability or performance.

## Level selection qualification

[Level selection](LEVEL_SELECTION.md) describes the lazy tier list, fixed
navigation, difficulty jumps, saved bests and current access states. The native
API 26/36 fixtures use 80 synthetic cards at 320 × 640 dp and 200% text. They
verify bounded composition and navigation, not physical scrolling performance or
the quality of 80 authored levels. Qualify the completed catalog on the named
2019 phone, tablets and folds with human TalkBack, localized numbers and device
RTL. Check access after backgrounding across a temporary expiry, Pro revocation,
return from a board/Pro and process recreation; retain each earned best.

## Settings qualification

[Settings behavior](SETTINGS.md) describes scrollable progress-reset confirmations,
failure/retry and labelled audio controls. The native API 26/36 fixtures in
the local settings-accessibility evidence packet cover 200% text, cancellation/recreation, busy
states, SQL rollback, scoped/global reset with retained claims, persisted SFX volume
and RTL controls. Installed debug/minified checks only open and cancel reset.
Complete the same tasks with human TalkBack on physical compact phones and tablets;
listen for silent-mode behavior and measure actual SFX latency separately.

## Progress and history qualification

[Progress and history](PROGRESS.md) documents the new lazy lists, free/Pro split,
filters and bounded history reads. Qualify every tab with human TalkBack, 200% text,
device RTL, long localized dates/numbers, tablets and physical large-history data.
Complete a Daily on a later local date and verify both dates and its saved target.
Exercise page/filter restoration, return from Pro, revocation while viewing a page,
backgrounding, resets/imports and a failed read followed by retry. Native compact
fixtures and injected ownership do not substitute for these physical/live checks.

## Storage failure qualification

[STORAGE_RECOVERY.md](STORAGE_RECOVERY.md) defines the read-failure gate, preserved
database behavior and confirmed settings-only reset. Emulator fixtures inject
corrupt/future-version files, but physical low-storage, process termination during
preservation/write, automatic restore and human TalkBack checks remain required.
Keep original test files before injection, verify committed progress and reward
claims afterward, and distinguish settings reset from full database restoration.
Campaign best checks also inject syntactically valid JSON with invalid score/date
fields or a mismatched row ID. These values must open recovery without replacement
progress; retry while still invalid must preserve them. After repairing a fixture,
verify normal navigation and exactly one completion reward. This does not claim
that every field of every stored record has received equivalent validation.
Confirmed Pro restoration now prepares a separate database without opening damaged
Room and atomically selects it; physical interruption/transfer qualification remains open.

## Reminder qualification

See [REMINDERS.md](REMINDERS.md) for the device-private configuration, shared date
limit, inexact scheduling and failure contract. On each supported physical device:

1. Open Settings → Reminders with a fresh install. Confirm it is off and no
   permission prompt appears until enabling. Deny, allow and later revoke Android
   permission; test a blocked channel and the return from notification settings.
2. Change kind and local time while off, then enable. Exercise 12/24-hour selection,
   200% text and TalkBack. Cancel must preserve the previously saved time.
3. Let an actual inexact alarm arrive while awake and backgrounded. Repeat under
   idle/battery saver, documenting deferral rather than expecting an exact minute.
   Check the low-importance channel, complete copy and a tap into today's Daily.
4. Complete a puzzle before delivery; verify suppression. Keep the app foreground
   across the target time. Streak mode requires yesterday's credit; both modes
   share one date claim even after toggling off/on or importing old progress.
5. Reboot, cross midnight, change timezone and cross DST boundaries. Restore an
   install and confirm no reminder opt-in transfers. Record force-stop and
   manufacturer battery restrictions separately from normal delivery.

JVM and emulator fixtures use explicit callbacks and injected clocks for boundary
coverage. They cannot establish physical idle delivery, manufacturer behavior or
human TalkBack usability.

## Personal photo qualification

[PHOTOS.md](PHOTOS.md) documents selection, crop/orientation, private storage,
deletion and backup limits. Exercise both the native picker and the older system
document fallback with representative camera/library providers. Revoke source
access or delete the source after confirmation, then force-stop and reopen every
Tiles size. Check tile identity, image orientation, number labels, overview/zoom,
TalkBack actions and image preview at 200% text. Daily must retain bundled art.

Repeat with a large photo, transparency, animation and mirrored orientation.
Observe physical heap/frame behavior on the reference phone. Kill the process
during copying, before confirmation and around committed storage, distinguishing
an intentionally discarded preview from loss of a committed image. Test Pro loss
and recovery, missing/corrupt local bytes, full storage and cancellation.

Export a photo board, change it, import the original and recover the previous
copy. Missing/invalid image payloads must leave current progress intact. Delete
photos while a backup preview exists and confirm current/history/recovery/staged
copies are gone. Check Android automatic restore separately, including its 25 MB
quota. These physical/provider observations remain outstanding.

## Startup and frames

The `:benchmark` module installs a separate `com.parlor.puzzles.benchmark` app,
resets only that package, and drives real onboarding/tutorial controls. Its app
variant uses R8/resource shrinking and is profileable, with debug signing.
`ReportDrawnWhen` reports usable content after preferences and verified content
load. The benchmark measures cold startup and repeated Traffic Jam moves/undo.
It does not yet cover an 80-level selector, every engine or theme changes.

On a connected physical device, use JDK 17 and run:

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Default runs warm compilation three times, then retain ten cold starts and five
frame iterations. Keep raw JSON and Perfetto traces from
`benchmark/build/outputs/connected_android_test_additional_output/benchmark/`.
Record model, OS/build, refresh rate, thermal/battery state, compilation mode,
source/content hashes and APK hash. Report distributions and jank, not only the
best value. The requirements call for startup under two seconds and 60-fps play
on a named 2019 mid-range reference phone; that phone is not yet selected.

A one-iteration emulator **harness check only** can be run explicitly:

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.parlor.smoke=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

This mode uses no ahead-of-time warmup and cannot qualify performance. No emulator
suppression is built into the normal task. The benchmark setup follows the
[Android macrobenchmark guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
The pinned [Benchmark 1.4.1](https://developer.android.com/jetpack/androidx/releases/benchmark#1.4.1)
works with the project's Kotlin/AGP set; it is not presented as the newest release.

## Physical sound and interaction

Select the reference phone plus the smallest supported phone, a 6.7-inch phone,
and a tablet. For each built-in speaker route, measure request-to-acoustic-onset
latency using synchronized trigger/audio capture; log rig calibration and timing
uncertainty. Capture repeated warm and first-use samples for all 24 assets and their interaction/reward event paths. Keep
raw recordings, trigger timestamps and analysis alongside device/build settings.
The under-50-ms requirement needs physical measurements; SoundPool's API choice
or an emulator trace does not prove it. Report Bluetooth separately.

Check silent mode, independent effect volume, disabled music, paired haptics,
illegal moves and foreground/background audio. Have representative players
complete short sessions; record clarity, reach and whether replay feels worthwhile.
Complete Traffic Jam with TalkBack, at 200% text and on the smallest phone, then
repeat for every additional board interaction before expanding its full catalog.

## Remaining external evidence

Physical devices, observed play sessions and owner approval of the concrete brand
samples remain outstanding. Brand/title/domain clearance and Play/AdMob/crash
reporting accounts are also outstanding. No account has been created, service
configured, asset commissioned or app published by this implementation work.
The later gates still require 20 testers for 14 days and 14 production days with
at least 99.5% crash-free sessions, plus actual purchase/ad/restore verification.

## Wave A layout evidence

Automated fixtures in `WaveABoardTest` constrain content to 320×640 dp with
200% type. They complete the seven-disk/four-peg Hanoi sample, the 8×8 Switch
Grid, three-jug Expert board and twelve-pancake Expert stack through semantic
actions or reachable buttons. Switch targets remain at least 48 dp beside a
persistent overview, and overview taps do not count as moves. These are Compose
semantics checks, not TalkBack service/user observations. The required physical
small-phone, tablet orientations and human task-completion gates remain open.


## Wave B layout evidence

`WaveBBoardTest` completes the 5×5 image Tiles board, 72-move Block Escape board
and six-box Expert board within a 320×640-dp fixture at 200% text. Tile controls
include optional number labels and a complete-picture preview. Block custom
accessibility actions retain piece IDs across every translation. Box walking and
pushes are explicit, with a full overview beside at least 48-dp interaction cells;
overview taps change only the view. Fixed-size direction drawings keep arrows
legible without wrapping at large text sizes. Separate touch checks cover tile
and block drags across a timer-state update.

`tools/wave_b_device_smoke.py --reset` exercises all three first-open tutorials,
a campaign action and force-stop/resume on the isolated normal-size emulator.
These checks supplement the full tutorials/results, storage and demonstration
suite. They still do not establish human TalkBack completion, physical reach,
sound latency or device performance; the same outstanding gates apply.


## Themes and appearance qualification

[THEMES.md](THEMES.md) defines the six rooms, light/dark/device choices and access
rules. The deterministic fixtures cover durable settings, queued Pro revocation,
retained premium choices, backup/recovery and save errors. Native compact selector
fixtures use a 320×640-dp display and Android's actual 200% font setting. Separate
board render fixtures visit all twelve families in every room and appearance;
they supplement the existing completion fixtures, not human playtests.

For physical qualification, choose every room in both appearances and exercise
selection, illegal moves, hints, undo/redo, overview/pan controls, Results and
dialogs. Include a paused personal-photo board and the bundled Daily picture.
Change Android's night setting with the app foregrounded and backgrounded;
explicit Light/Dark must override it while Use device setting follows it. Restart
the process and confirm the saved choice. Reconcile Pro loss and restoration:
Linen displays while Free, the saved premium choice returns with Pro, and neither
transition changes progress or photos. Test all of this with TalkBack, large text,
tablet orientations and fold/cutout transitions.

The palette tests assert 4.5:1 for named foreground/container pairs, board labels
and decorative-overlay worst points under normal vision and full-severity
protanopia/deuteranopia simulations. These do not qualify every rendered component
state. Audit disabled Material controls, focus/selection indicators, transient
surfaces and image labels separately; do not infer whole-app accessibility from
the palette test. Measure repeated theme transitions for frame/heap regressions
on the physical reference device. Owner approval of final identity remains open.

## Minimum/current API runtime evidence

The current twelve-family/56-level build passes the same 131 deterministic Android storage, UI and SDK
checks on separate API 26 (Android 8.0) and API 36 (Android 16) emulators. See
the local themes evidence packet, the archived XML reports and both sets of
theme/SDK screenshots. Earlier increments retain photo, reminder, backup and other compact captures.
Source and installed APK hashes match across the runs.
This is runtime compatibility evidence for implemented functionality; it does not
qualify physical performance, actual Play transactions, regional UMP forms or the complete launch catalog.
Use `ANDROID_SERIAL` to select one emulator when reproducing the Gradle test run.

## Wave C layout evidence

`WaveCBoardTest` completes diamond and triangular Peg Solitaire, constrained 8×8
Queens, the closed 8×8 Knight’s Tour, and all three multi-traveller crossing
scenarios in 320×640-dp/200% fixtures. The triangle uses physical drags along both
axes; dense peg/chess boards keep a full overview beside at least 48-dp targets.
The knight viewport follows accepted steps and restored undo/redo positions.
Crossing actors remain visible while the explicit crossing button is in view.
Fixed queens cannot move; bridge scoring remains separate from travelled minutes.

`tools/wave_c_device_smoke.py --reset` checks the installed APK hash before driving
all four first-open tutorials and verifying actual force-stop restoration. These
checks supplement Activity recreation, database reopen and all-family read-only
demonstrations. Human TalkBack and physical reach/latency gates remain open.

## Billing qualification

Billing now uses the real Play SDK, with signed receipt validation, durable
acknowledgement retry and a purchase/restore screen. Follow
[BILLING_SETUP.md](BILLING_SETUP.md) for owner configuration, the production-package
storeTest variant and required license-test/test-track observations. Local tests
exercise synthetic RSA receipts, write/query failures, pending and duplicate
updates, offline restarts, revocation without lost progress, corrupt private
storage, localized price changes and the 200% Pro layout. The unconfigured SDK
smoke makes read-only queries and never launches a purchase.
The same source passes 39 portable checks, 11 Billing JVM checks and 64 Android
checks on each API; the local billing evidence packet archives reports, screenshots and build hashes.

These tests do not establish real receipt interoperability, checkout success,
platform retry after reboot, account restoration, promo redemption or production
Billing operation. All remain explicit P6/P8/P9 service gates. Ads startup tests
now inspect the real SDK's initialization state with injected unknown/Pro/pending
ownership; full cached/restored Play-to-Ads qualification still requires live purchases.

## Consent and ad qualification

the local ads evidence packet archives 146 distinct deterministic checks and one separate
real-AdMob demo-unit case passed on both APIs. The SDK fixture covers banner
display, fullscreen show/close, activity pause and Pro disposal/preloader removal;
its ownership and consent inputs are injected. Production unit configuration,
regional consent/withdrawal, production reward delivery, first-launch order and physical
device/lifecycle/latency coverage remain P6/P8 gates. See [ADS_SETUP.md](ADS_SETUP.md).

The API 26 emulator's Chrome WebView 69 visibly crops the demo interstitial image;
its close remains available. API 36's WebView 133 renders the portrait creative
fully. Investigate on updated WebViews and physical devices before visual
acceptance. The SDK's image-demo X required a physical tap in UIAutomation;
human TalkBack through SDK-owned surfaces is not qualified by these tests.

## Rewarded offers and recovery

The app now exposes all three explicit rewards: two earned hints, 24-hour access
to one Hard/Expert level and a scored Daily retry. Deterministic checks cover
offer/load/show cancellation and timeouts, entitlement changes, the shared dated
cap, duplicate callbacks, failed writes, recovery after database commit, corrupt
journals, actual-earned-time expiry and an offered Daily ad spanning midnight.
Storage checks also cover an explicit scored restart replacing only a practice
attempt, and rollback when no credit is available. Queued gameplay input cannot
cross a pause/background generation boundary.

`RewardSdkQualificationTest` uses the real Google demo earned callback and real
Room/private journal on API 26 and API 36. It verifies exactly two extra hints,
one view/claim, an unchanged paused board and active time, cleared pending storage,
and removal of new requests/offers after injected Pro. The fixture waits for the
visible SDK close control, since earning can precede the end-card animation.
It never opens the advertisement destination. Ownership and consent permission
are injected; regional consent and real Play interoperability remain untested.

`RewardLayoutTest` changes Android's actual font scale to 2.0 and the display to
320×640 dp before the Activity opens, then restores both settings. All three
offers retain at least 48-dp Watch/Close controls, complete scrollable explanations
and the daily count; the failed-save retry is reachable. This covers native dialog
windows as well as Compose content. Captures include the initial and scrolled
body. It does not certify human TalkBack or the physical-device matrix.

Private earned-reward storage recovers after a durable journal write. A device
process killed before that write, or an SDK callback never delivered, cannot be
recovered by this client-only design. Corrupt pending storage prevents another
rewarded view and exposes retry rather than silently discarding the receipt.
See the local rewards evidence packet for that increment, the local database-recovery evidence packet for the current
regression run, and [ADS_SETUP.md](ADS_SETUP.md) for production/physical gates.

## Manual backup and interruption recovery

The manual file contract and retained reward safeguards are documented in
[BACKUPS.md](BACKUPS.md), with its original reports in the local backups evidence packet and
the preceding full regression reports in the local database-recovery evidence packet and the
latest affected suite in legacy settings.
Test the entire path through Settings → Progress backups:
save with the system picker, make a committed move, choose the saved file,
inspect the preview, cancel without changes, then select again and confirm.
Verify the restored board/history/settings and the previous-progress recovery
copy. Test Free/Pro changes and ensure importing never grants ownership.

Automated storage fixtures cover whole-file validation, SQL rollback, repeated
old imports, retained hint flags and spending, exhausted Daily retries, a database
reopen after the progress commit, and a failed settings write followed by retry.
The large-history fixture stores 5,000 completed attempts and a recovery snapshot
above 2 MiB, then reopens and recovers it on each API. Snapshot reads use chunks
to avoid the older database cursor limit. Individual attempt/player rows are
bounded before import. The database-reopen fixture simulates the interrupted
commit boundary; it does not physically kill the process mid-import.

Pending-settings completion also accepts omitted default fields and reordered or
whitespace-formatted JSON. Fixtures verify that changed/invalid markers and failed
commits remain pending. Cold launches exercise three compatible encodings on
API 26/36 debug builds and the API 36 minified build. Physical interruptions during
DataStore application and the subsequent marker commit still require qualification.

`BackupDocumentTest` uses real local DocumentsUI with injected Pro and no purchase.
The UI Automator driver scopes Downloads to the roots drawer, dismisses the drawer
through its scrim and waits for native controls. `BackupScreenTest` checks scrollable confirmation/cancel
controls at 320×640 dp, 200% Compose text and a 48-dp minimum. Neither establishes
human TalkBack completion or arbitrary document-provider behavior.

Physical low-storage/process-kill runs, provider cancellation/revoked access,
third-party document providers, automatic photo transfer, physical database
corruption recovery and full production history/device coverage remain open.

## Unreadable storage and independent safeguards

The recovery screen preserves corrupt SQLite files, refuses newer schemas and
offers Retry/Close and a Pro file restore path for damaged progress. Settings corruption has a separate confirmation that retains
the original preferences before resetting settings. Compact native 200% controls
and cold-process fixtures supplement the data-layer tests; human TalkBack and
physical storage failures remain separate checks. See [STORAGE_RECOVERY.md](STORAGE_RECOVERY.md).

An independent safeguard file now precedes Room commits for reward/completion
claims. Device fixtures cover successful reopen, file-write failure with SQL
rollback, a later SQL failure with conservative retained claims, old-file imports,
legacy hint flags, malformed/future versions and retry after a failed file commit.
The final file and its directories are synced; matching read-back bytes are
required before Room may commit. These injected errors are not evidence of
physical power-loss behavior.

Before release, interrupt the physical device before the safeguard write, after
that write, during the Room commit and during pending-settings completion. Verify
committed boards/claims, the stated conservative treatment of uncommitted claims,
and no duplicate hint/retry grants. Exercise full storage and transient I/O errors,
then retry after repair. Transfer both the database and safeguard file through
Android backup, including recovery from an older AtomicFile `.bak` after an
interrupted write. Confirm purchase/reminder/consent exclusions.

The damaged-database restore path prepares a complete inactive database before
switching an atomic selector. Native tests exercise validation without opening
damaged Room, confirmation/cancel, Pro loss, safeguard/candidate/selector failure,
the before/after-switch boundary, pending settings, repeated old files, live
repository ownership and photo deletion of previous storage. Physical testing
must interrupt both sides of the selector switch, verify the kept damaged file,
resume pending settings and test recovery followed by deletion. Transfer both
slots and the selector, including the legacy `.bak`, and check the backup quota
with a large retained database. These remain device qualification requirements.
