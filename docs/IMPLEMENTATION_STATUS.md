# Android implementation status

The full objective is the supplied Android v1 requirements, including all 12
puzzles and 556 campaign levels. This record describes implemented code and
remaining work; it does not replace the requirements or narrow the release scope.

## Current development increment

This checkpoint is prepared for the owner's local/APK testing. Further feature
work waits for that testing; the complete Android v1 objective remains unfinished.

- Fold/hinge-aware screen and dialog placement is implemented. Play, tutorials and
  demonstrations use separate board/action panes when both regions are large
  enough. The latest checkpoint's focused evidence and limitations are in
  [Fold layouts](FOLD_LAYOUTS.md) and the local fold-layout evidence packet; the full regression
  matrix below belongs to the preceding motion source, not this checkpoint.

- All twelve board families now use gentle 250 ms transitions, with reduced-motion
  rendering in play, tutorials and demonstrations. Moving targets reject input;
  Box Pusher walks around verified corners. Final moves remain on the board after
  their durable commit before Results appears, with a single soft confetti fall
  for a new scored result. Recreated attempts start at the committed position.
  See [Board motion and completion](MOTION.md).
- Locale-aware board labels, dates, backup previews, fractional stats and reminder
  times now share the active resource locale. Puzzle geometry keeps its physical
  coordinates under RTL, while surrounding controls follow the interface direction.
  English fallback paragraphs retain punctuation order. See
  [Locale and layout behavior](LOCALE_LAYOUTS.md).
- Completed games resumed from Home now return to their named collection or Daily
  screen. A one-shot saved navigation request survives recreation without reopening
  that destination during subsequent navigation.
- Eleven Gradle modules, wrapper/version catalog and dependency checksums; JDK 17,
  API 26 minimum and API 36 target. Local Git repository initialized.
- Portable, serialized level/attempt/progress contracts and a platform-import check.
- Crashlytics 20.1.1 integration with explicit application startup, no Analytics
  dependency or custom events, and disabled Firebase initialization in debug,
  benchmark and storeTest. An isolated minified crashTest variant contains the
  manual crash screen. Reporting variants require their own owner configuration;
  mapping upload is enabled only for crashTest/release. See
  [Crash reporting](CRASH_REPORTING.md). Live delivery and console evidence remain open.
- Traffic Jam engine, collision/exit rules, reversible history, unit-cost solver,
  verified route/recovery hints and an independent reference/optimality verifier.
- Twelve original Traffic Jam levels: three in each tier, optimal pars 8–29 moves.
- Hanoi (3/4 pegs, through 10 disks), Switch Grid (3×3–8×8), Water Jugs
  (2/3 jugs) and Pancake Flip (4–12) engines, solvers/verified continuations and
  sixteen fixed-seed sample levels, one per family/tier.
  Four-peg Hanoi uses a reference par; the other Wave A pars have proof checks.
- Sliding Tiles (3×3–5×5, numbers/bundled image), Block Escape (whole-rectangle
  translations) and Box Pusher (1–6 boxes, scored pushes and separate walking)
  engines/renderers/tutorials, bounded solvers and twelve original tier samples.
  Expert tiles/boxes retain
  reference pars; the other new samples have optimality checks. Box deadlocks
  produce a warning and verified recovery hints, without rejecting legal pushes.
- Peg Solitaire (four geometries), constrained Queens (4×4–8×8), open/closed
  Knight’s Tour (5×5–8×8) and all four Crossings scenarios now have engines,
  renderers, tutorials, verified hints, demonstrations and sixteen tier samples.
  Total: 56 verified boards across all twelve families. Bridge optimization uses
  weighted duration, while Queens/tours use active-time targets and tours retain
  undo backtracks. Full catalog counts and physical qualification remain open.
- Room storage with atomic board/history/time/hint/completion commits, DataStore
  preferences, automatic backup allowlists, and saved selection/magnification.
- A storage recovery gate catches unreadable settings/progress, preserves corrupt
  SQLite files, and refuses a newer database version without downgrading. A
  confirmed settings-only reset preserves its exact previous file privately first;
  progress, rewards and ownership stay separate. Confirmed Pro restoration now
  prepares an inactive database and atomically selects it while retaining the
  damaged file. Pending settings finish before Home. See [Storage recovery](STORAGE_RECOVERY.md).
- Interrupted imports compare decoded settings and conditionally clear the exact
  stored marker within one transaction. Legacy default fields and JSON formatting
  are accepted; different settings, invalid data and failed commits remain pending.
- Campaign best reads now share backup score/date validation and verify their
  stored level identity. Invalid values enter damaged-save recovery without an
  empty replacement; completion transactions roll back rather than use a corrupt
  best. Valid independent bests retain their values.
- Independent recovery safeguards now persist reward/completion/hint claims and
  achievements before Room can commit their changes. Confirmed imports retain
  that protection even when the readable database is older. A failed safeguard
  write rolls back the SQL transaction; a later SQL failure can leave conservative
  protection ahead. Damaged-database recovery validates without opening the damaged
  file, retains these claims and serializes with privacy deletion. Photo deletion
  also removes the previous damaged database and both staged previews.
- Stars, time bonus, best-result accounting, local-date hint reset/claim logic,
  streak calculation and free tier/sequential access rules. Shared rewarded-view
  limits cover hint credits, 24-hour campaign access and scored daily retries.
- Frozen daily schedule epochs and saved level revisions; premium daily access,
  30-day detail history, scored/practice retries, Pro eligibility, best-only daily
  aggregates, rollback/reset claim protection and midnight-safe active boards.
- Exactly 40 stable achievement definitions; per-puzzle/overall summary stats,
  Pro history gate and per-puzzle/global reset confirmations. Hints, reward claims,
  streaks and earned achievements survive progress resets.
- Settings reset explanations now scroll at native 200% text. The confirmation
  retains its scope during failure/retry and disables dismissal while saving.
  Labelled sound/music controls expose 48 dp bounds and explain unavailable music.
  See [Settings behavior](SETTINGS.md).
- Progress now uses lazy lists for Summary, Achievements, Pro Best results and Pro
  History. History loads 20 attempts per page, with puzzle/play filters, stable
  cursor ordering, completion dates, scoring-unit-aware results and saved Daily
  targets. Startup no longer collects all completed attempts. Filters/pages
  survive recreation; backgrounding and Pro loss stop the page collection.
  Best results explain independent saved bests. See [Progress behavior](PROGRESS.md).
- Compose onboarding, Home/resume, tier/level selection, board, results, summary
  stats, daily entry/history, achievements and initial settings. Four-step interactive
  Traffic Jam tutorial and replay, plus a separate 3–4-step tutorial per new family.
- Room Home now shows Traffic Jam on a side table and eleven original puzzle
  objects on adaptive shelves. Simple Home uses a plain card grid. Both show
  overall/per-puzzle campaign completion counts and preserve Home position across
  collection navigation and recreation. See [Home behavior](HOME.md).
- Level selection now uses stable rows in a lazy list, fixed Back/Difficulty
  controls and separate banner space. Difficulty jumps preserve all four tiers
  in the list; recreation and return from Pro retain its position. Saved bests
  and earned stars remain visible with access state after Pro loss. Card selection
  rechecks temporary expiry. See [Level selection](LEVEL_SELECTION.md).
- Tap, drag, destination buttons and accessibility actions; compact-board
  magnification with overview/pan controls. A real-touch regression test covers
  timer saves during a gesture.
- Twenty-four original synthesized sound assets across plastic, wood, metal and
  water palettes, shared reward chimes, SoundPool/haptics, silent-mode checks and
  separate SFX/music settings. No music is bundled. Nunito font and OFL license.
- Pro reference demonstration with playback/step controls; a read-only replay
  preserves the paused player board and earns no progress. Entitlement now comes
  from the Billing coordinator; injected providers remain confined to tests.
- Play Billing 9.1.0 adapter, signed client-side receipt validation, localized
  checkout and restore/redemption, cached offline Pro and full-query revocation.
  Atomic entitlement writes are excluded from backup/progress; persisted platform
  jobs plus foreground/reconnect reconciliation retry acknowledgement. Pending or
  unacknowledged purchases cannot grant access. Unknown ownership gates future ads.
- A dedicated debug-signed storeTest variant uses the Play package name and sample
  catalog for configured license testers. Release still requires all 556 levels.
  Owner product/public-key configuration and live purchase evidence remain open;
  [Billing setup](BILLING_SETUP.md) records the concrete procedure.
- UMP 4.0.0 consent/Privacy options and GMA Next-Gen 1.5.0 banner/interstitial
  adapters. Known Free and current consent gate all GMA startup/requests; unknown,
  pending and Pro states suppress them. Required forms defer past active boards.
  WorkManager startup and persisted SDK workers are gated; no wake-lock permission
  is merged. Debug/storeTest/benchmark use demo IDs; release requires production IDs.
- Bottom banner slots on the five allowed surfaces; interstitial preload after
  level start and ready-only presentation on Results Next/Back. Durable lifetime
  counts, departure claims, monotonic cooldowns, boot/transfer identity and periodic
  Pro prompts survive resets. Missing ads and slow local claims continue navigation.
  [Ad setup](ADS_SETUP.md) records remaining consent/production/visual qualification.
- Explicit rewarded offers for two hints, 24-hour access to one Hard/Expert level,
  and a scored Daily retry. Opening an offer makes no ad request. Checkpointed
  boards and timers pause; queued input cannot run across the pause boundary.
  Only an earned callback supplies a receipt; cancellation/no-fill grants nothing.
  A private AtomicFile journal precedes an idempotent Room grant, shared dated cap
  and permanent claim. Failed storage can retry after foreground return/restart;
  hint balance, original earned-time expiry and promised Daily date are preserved.
  All three offers recheck ownership and share ten completed views per local date.
- Pro manual file export/import through the system picker, bounded schema/content
  validation and explicit preview/confirmation. All-family board/history validation,
  cumulative hint spending, sticky attempt hint flags and retained dated claims
  prevent ordinary old-backup reward farming. Room v4 saves previous progress and
  replacement atomically; a durable settings marker finishes DataStore before play.
  Recovery of the previous copy remains available after Pro loss. See
  [Backup behavior](BACKUPS.md); wider provider/device qualification remains open.
- Pro own-photo Sliding Tiles with the native picker/document fallback, orientation
  handling, a confirmed square crop, bounded private copies and preserved tile identity.
  Image/reference commits are atomic. Schema 4 backups include validated photos;
  schema 2/3 files remain supported. Pro loss keeps images/progress and uses numbers;
  Daily always uses bundled art. Free-access privacy deletion covers current/history,
  local recovery and staged backup copies. See [Photo behavior](PHOTOS.md).
- Six original room/board skins, saved Light/Dark/device appearance, shared Compose
  palette tokens and opaque image labels. Pro loss preserves the selected room
  while displaying free Linen. Theme writes recheck Pro inside DataStore; manual
  schema-4 files retain settings and accept legacy schema-2/3 files. Palette
  foreground checks include protanopia/deuteranopia simulation. See [Themes](THEMES.md).
- Adaptive Play/tutorial/demo layout with separate board and control scroll areas
  in wide landscape windows, a centered portrait/narrow fallback and a results
  banner outside the scroll area. Traffic Jam's magnified overview moves beside
  the interaction when space permits. Fixed-queen underlines remain visible at
  200% text. [Tablet play behavior](TABLET_PLAY.md) records the tested scope.
- Isolated `.benchmark` app and macrobenchmark module, minified/profileable,
  with cold startup and Traffic Jam move/undo frame scenarios.
- Opt-in local Daily/streak reminders, a chosen local time, contextual Android
  permission, a quiet channel and inexact non-waking alarms. Device-private dated
  claims enforce one posting attempt per local date across kinds and resets.
  Completion/foreground checks suppress delivery; boot/timezone changes reschedule.
  Notification taps open the current Daily through onboarding and recreation.
  [Reminder behavior](REMINDERS.md) records failures and physical delivery limits.

## Evidence produced

The preceding motion source’s deterministic evidence set contains 365 distinct checks: 65 portable,
64 platform JVM, six data JVM, two design JVM and 228 Android cases (92 storage
and 136 app). The Android set passes on API 26 and API 36, for 593 passing
executions with no failures or skips. Reports retain exact source, catalog and APK
hashes. Unchanged JVM Gradle tasks reuse their reports with original timestamps.
The motion increment adds eleven cases per API covering all twelve families'
intermediate/reduced frames, joined pieces, verified corridor walking, touch
suppression, durable final moves, Results/ad/callback ordering and recreation.
Separate native runs retain forty captures per API. See the local motion evidence packet and
[motion behavior](MOTION.md).
The minified build passes, and lint has zero errors with 27 existing dependency
advisories. Installed debug/minified API 36 checks pass the real motion switch,
cold restart, retained board/history, three-star solve and eight-move best.
Source/report/APK/catalog and capture hashes are audited together.

The preceding locale/RTL increment covers physical drags and spatial ordering, all twelve
Expert renderers at native 320 × 640 dp and 200% text, overview/pan behavior,
Arabic/German formatting and English fallback paragraph direction. Two actual
Activity cases verify campaign/Daily results returns after Home resume, recreation
and consumption of the saved destination. Fourteen standalone cases also pass on
both APIs with nineteen captures per API; their original test APK hash remains
separate from the final suite's test APK after the unrelated Daily fixture correction.
Installed debug and minified checks pass actual Android app-locale changes,
localized controls, undo/redo, cold resume, the eight-move Traffic Jam completion,
return to level selection and a German-formatted saved best. The minified build
passes; lint has zero errors and 27 existing dependency advisories.
See the local locale-rtl evidence packet and [locale behavior](LOCALE_LAYOUTS.md).

The preceding crash-reporting increment adds native checks that ordinary launch/recreation
leave Firebase uninitialized, all collection flags are false, and the provider and
internal crash screen are absent. Missing configuration returns an unavailable
reporter without preventing launch. Eleven Gradle configuration cases pass;
variant/dependency/manifest audits and seven official Maven artifact comparisons
are recorded in the local crash-reporting evidence packet. The internal screen compiles with
configuration processing explicitly disabled; no reporting-enabled APK, SDK upload,
mapping delivery or console crash has been qualified without the owner's project.
Final debug/minified installed smokes retain a move through rotation/cold restart,
solve the reference and retain its best after another restart. Debug and internal
lint have zero errors (27 and one dependency advisories respectively). The two
real Google demo-ad cases pass on both APIs, plus four Firebase-isolation repeats
after actual ad SDK startup. API 26's cropped demo image remains a visual finding;
injected consent does not qualify regional forms. The final source, reports,
packaged content and installed APKs are tied together in the artifact audit.

The preceding tablet increment adds native 960 × 600-dp landscape and 600 × 960-dp portrait
coverage. Twelve Expert renderer fixtures verify representative 48-dp targets,
200% text and independent control scrolling. Actual Traffic Jam navigation retains
board/history/selection/view/hints/time across rotation and recreation, then
exercises undo, redo, restart and three-star completion. Tutorial/demo fixtures
verify interaction and retained rewards. See [adaptive play](TABLET_PLAY.md).

The tablet packet's full matrix precedes a fixed-queen underline correction discovered during
visual review. A rendered-pixel assertion reproduces the hidden marker before the
fix. The final affected set passes sixteen cases per API (32 executions), covering
the tablet cases, seven compact Wave C board completions, four tutorial/play/recreation
flows and two theme/photo-state cases. The correction changes two board UI files
and one test file; the packet retains both source hashes, all source-file hashes
and the exact patch. Unchanged reports are not relabelled as executions against
the final revision. Evidence is in the local tablet-play evidence packet.
The final minified/resource-shrunk build passes; lint has zero errors and 27 existing
dependency advisories. Installed debug and minified tablet checks retain a committed move
through native orientation changes and cold restart, complete the reference route,
and retain three stars and the eight-move best after a second cold restart.
Source, report, capture, installed-APK and packaged-content hashes are audited.

The preceding Home increment covers all twelve collection routes in both layouts, campaign
completion counts, duplicate/unknown IDs, reset updates, 48-dp targets, compact
200% text and RTL reading order. Actual Activity checks cover layout persistence,
return position and recreation while retaining progress. Captures show all
puzzle objects, the simple grid and light/dark rooms. The final minified build
passes, and lint retains zero errors with 27 existing dependency advisories.
Installed debug and minified native smokes verify both Home layouts, last-collection
navigation, return position and cold-restart persistence without changing campaign
counts. Artifact/source hashes and packaged content are audited.
See [Home behavior](HOME.md).

The preceding saved-best validation increment adds two portable, three Room and one actual
Activity case. They cover score/date/identity bounds, retained invalid rows,
observer termination, disk reopen/retry, completion rollback and repair without
duplicate rewards. Actual recovery, recreation and retry retain attempts,
receipts, bests and player data at native 200% text. Exact evidence is in
the local saved-best-validation evidence packet. Debug and minified builds also pass installed
compact navigation and an actual eight-move Traffic Jam solve, with three stars
retained after cold restart. Source, report, packaged-content and artifact hashes
are audited; lint has zero errors and 27 existing dependency advisories.

The Level selection increment adds four compact UI cases and one actual Activity
navigation case. They cover the lazy 80-card fixture, difficulty jumps, fixed
navigation/banner separation, progression, temporary expiry, Pro revocation,
independent best units, restored position, empty groups and RTL/200% text. The
existing reward-navigation case was updated to click the fixed Back button.
Exact evidence is in the local level-selection evidence packet. The 80 fixture cards are not
shipped content and do not qualify physical scrolling performance.

The preceding Settings and Progress packets remain historical. Their coverage
includes reset cancellation/recreation, busy/failure/retry, isolated Room rollback,
scoped/global reset with retained claims, persisted audio settings, bounded history,
independent bests, filters, historical Daily targets and lifecycle failures.
The optional real-AdMob network cases were not rerun; their preceding evidence in
`database-recovery` remains bound to its earlier source and APK. Earlier increments
also retain two emulator benchmark harness checks; those measurements predate Wave A.

- Portable unit tests for serialization, traffic invariants/replay/search, history,
  score thresholds, time bonuses, bests, dates, reward claims and progression.
- Storage recovery checks preserve corrupt database bytes and future-schema records,
  retry repaired rows and keep the last durable board when its observer fails.
  Corrupt protobuf/JSON settings require confirmation and a verified private copy
  before replacement; archive creation/rename failures prevent reset. Native compact
  200% controls cover cancellation, busy state, failure and 48-dp actions. Whole-database
  restoration prepares a separate file without opening damaged Room and switches
  only after confirmation and successful preparation. Nine independent
  protection fixtures and three portable cases additionally verify high-water
  claims, rollback/retry, separate file reopen, legacy hints and directory sync.
- Billing tests cover synthetic RSA receipt validation, pending/acknowledged states,
  durable acknowledgement retries, failed writes/queries/scheduling, offline cache,
  duplicate/concurrent and delayed post-refund callbacks, restoration and price
  changes. Android tests add no-backup storage/corruption, progress through
  revocation, actual Pro navigation, 200% text and a read-only real SDK smoke.
  No actual Play purchase has been performed; these do not certify service operation.
- Ad/consent tests cover safe delayed forms, failed responses, privacy after Pro,
  ownership races, stale resources, persistent worker suppression, frequency and
  reset/reboot/transfer rules, missed ads, repeated callbacks and main-thread
  Results navigation. A real Google demo banner/interstitial loads and closes on
  both APIs with injected ownership/consent. API 26's old WebView crops the demo
  image; visual/physical/TalkBack and actual regional consent remain unqualified.
- Reward checks cover all three offers, shared caps and dated recovery, duplicate
  callbacks, explicit scored retry, failed transactions/journal writes, corrupt
  storage, rapid pause/resume input and Pro changes. A real Google rewarded demo
  delivers exactly two hints and one view while the board/time stay unchanged.
  Compact dialog checks use a 320×640-dp display and Android's actual 200% font
  setting; all body text and the daily cap can scroll while actions stay reachable.
- Backup checks validate all twelve board/history families, malformed/oversized
  UTF-8 files, unsupported schemas/content, retained reward claims and spent hints,
  SQL rollback, migration to Room v4, settings-write recovery and no ownership or
  notification grant. A 5,000-attempt recovery snapshot above 2 MiB survives database
  reopen and settings completion on both APIs. The real system picker saves,
  selects, previews without mutation and restores a file with injected Pro.
  Compact 200% confirmation/cancel controls remain reachable and at least 48 dp.
- Photo checks cover normalization, all eight EXIF transforms, source/allocation
  limits, metadata removal, retained copies after source deletion, disk reopen,
  move/undo/restart, deduplication and quota rollback. Schema-2 legacy, schema-3
  photo and schema-4 imports, recovery, corrupt payloads and SQL rollback pass on both APIs.
  Confirmed privacy deletion survives leaving Settings and removes staged imports.
  Six app cases cover tile identity at every size, preview/input pause, Pro loss,
  Daily isolation, real native/document selection and compact 200% dialogs. Actual
  debug and minified Settings smokes verify Free deletion navigation; minified Pro
  decoding, physical heap and arbitrary cloud-provider coverage remain open.
- Theme checks cover twelve palettes and color-vision simulations, real DataStore reopen,
  queued Pro loss, schema-4 round trips and legacy schema-2/3 imports. Native compact
  selectors retain 48-dp controls and scrollable text at 200%; actual Activity tests
  cover light/dark/device appearance, recreation, Home card colors and Pro Back navigation.
  All twelve families render in all six rooms and appearances. A paused photo attempt
  keeps history, hints, selection, viewport and image bytes across theme/Pro changes.
  Debug/minified appearance smokes supplement these injected ownership fixtures.
  Full component-state contrast, human TalkBack and physical transitions remain open.
- Reminder tests cover defaults, denied/blocked permission, concurrent callbacks,
  clock rollback, timezone/DST boundaries, durable claim failures, foreground
  changes and Room completion ordering. Private AtomicFile recovery and import/reset
  isolation pass on both APIs. The actual private receiver suppresses foreground
  delivery, posts once in background, and its real notification opens Daily with
  normal Back behavior. Compact settings use Android's actual 200% font setting;
  native picker, permission and reboot smoke checks supplement these fixtures.
  Explicit due callbacks do not qualify physical elapsed-alarm/idle delivery.
- `:tools:levels:verifyPrototype`: all 56 references replay legally, reach their
  goals and match stored costs. Proven-optimal claims have solver/formula checks;
  four-peg Hanoi and Expert tile/box samples are explicit reference targets.
  Small-state exhaustive/BFS tests independently cross-check the algebra, A*, Frame–Stewart construction, tile
  IDA*, and push-based box search/deadlock pruning. Duplicate content detection
  ignores interchangeable block labels and free worker movement within a region.
- Android debug build, minified/resource-shrunk benchmark build and lint, with
  the pinned dependency set. Lint has no errors and retains 27 dependency-update
  advisories; these are tracked separately from functional failures.
  Explicit content generation precedes verification and app asset merging; both
  APKs contain JSON matching the source. Development builds verify committed
  sample content automatically, and release assembly rejects the incomplete catalog.
- Room instrumentation covers disk reopen/history/selection/timing/hint flags,
  duplicate completion callbacks/replays, injected failed completion transactions,
  invalid moves and restart accounting. Version-1-to-4 migration preserves saved state,
  hints, score bests and receipts. Daily tests cover concurrent starts/completion,
  calendar rollback, midnight, access expiry, content updates, reset/pruning,
  newly earned retries and transaction rollback.
- Compose instrumentation covers a full authored solution through tap controls,
  tutorial interaction, magnified-board targets at 200% font scale and accessible
  custom actions; a full daily scored/practice/resume flow and a demonstration that
  preserves board/time/hints/rewards. A warm-return access regression rejects
  expired grants and queued input. Wave A adds all four first-visit/tutorial/solve
  flows, Activity recreation, 320×640-dp/200% board completion through large targets
  and accessibility actions, database reopen with histories/hints, free four-peg
  Daily access without campaign ownership, and read-only demonstrations. These
  tests do not substitute for human TalkBack testing. Wave B adds all three
  first-visit/tutorial/results flows, all three expert compact-board completions,
  bundled-image Daily access, disk restoration of walking/view/history and read-only
  demonstrations. Real touch tile/block drags also cross a timer-state update.
  Wave C adds four tutorial/results flows, disk reopen of all new Expert boards,
  free closed-tour Daily access, backtrack/minute persistence and all-family demos.
  Seven compact fixtures cover triangle drags, peg/chess overviews and full
  crossing routes with all travellers visible alongside confirmation at 200% text.
- The macrobenchmark harness passes both cold-start and move/undo smoke scenarios
  on the emulator. Physical performance thresholds are not qualified.
- `:tools:levels:verifyCatalog` correctly rejects the incomplete launch catalog;
  release builds depend on this task.

Exact command outcomes, device screenshots and archived test reports for each
increment are kept in local evidence packets outside this repository.
Build reports remain under each module's ignored `build/` directory.

## Remaining work in delivery order

1. Finish P0/P1/P2 qualification: actual smallest-phone/tablet/fold
   interaction checks, physical SFX latency/performance baselines, observed short
   sessions, approved identity, configured Crashlytics delivery/console qualification, physical benchmark
   evidence and physical restoration qualification. Database file restoration,
   the read-failure recovery UI and confirmed settings-only reset are implemented; the prototype
   gate is still open.
2. P3: finish remaining settings/service states and qualification of the shared screens. Daily rules,
   reward ledger, 40 achievements, summary/history gates and resets are implemented.
   Detailed best results and paged, filterable attempt history are implemented;
   human TalkBack, native-speaker locale review and physical performance
   qualification remain open.
   The Settings progress-reset explanation and labelled audio controls now pass
   native 320×640-dp/200% checks on API 26/36. The level selector now passes lazy
   80-card and saved-best/access fixtures on the same layouts. Native locale/RTL
   regressions now cover all twelve renderer families, dates/numbers and shared
   dialogs. Continue service-state and physical adaptive-layout qualification;
   human TalkBack remains open.
   Saved campaign bests now use the same score/date bounds as backup imports;
   invalid fields and mismatched row IDs are preserved and routed to recovery.
   Manual-import duplicate protection and photo payloads are implemented;
   physical/provider qualification remains P7 work.
3. P4: qualify all additional families on physical devices and with players.
   All twelve now have sample integration;
   this is not full P4 qualification.
4. P5: expand to exactly 556 nonduplicate original levels, calibrate target times,
   qualify the six implemented themes and finish puzzle-specific art/sound/tutorial assets.
5. P6: configure and qualify real Play purchases/restore/acknowledgement/refunds;
   Billing, UMP, all three ad formats, earned-reward recovery, privacy options,
   frequency rules and periodic upsells are connected. Qualify real regional
   forms, first-launch ordering, service/device
   lifecycle and the old-WebView visual finding. Unconfigured builds explain
   unavailable purchasing. There are no production fake purchases or rewards.
6. P7: wider photo/backup/provider qualification, physical reminder delivery,
   tablets/theme transitions/RTL/accessibility and final room Home qualification.
   Room/shelf art, the simple grid and visible campaign progress are implemented;
   native tablet renderer/rotation checks now pass in both orientations. Physical
   tablet/fold, final identity approval and human checks remain open.
   Gentle board movement and soft completion confetti are implemented. Native
   frame and Activity checks cover reduced motion, input suppression, committed
   state, results/ad ordering and recreation. Qualify their comfort, focus behavior
   and frame timing with players and on physical phones/tablets/folds.
7. P8/P9: full device/performance/security/size qualification, brand clearance,
   account/store/site configuration, assets, 20-tester/14-day beta, publication
   decision, live purchase/ad verification and 14 production days of stability.

Brand/account approvals, physical devices and beta/production evidence are external
dependencies. Substantial implementation remains available locally, so the goal
is active rather than blocked or complete.
