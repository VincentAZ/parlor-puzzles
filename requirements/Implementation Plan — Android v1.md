# Parlor Puzzles — Android v1 implementation plan

Prepared September 28, 2026.

## Scope and starting point

Build the complete Android v1 described in [the requirements](<Puzzle Collection — Android App Requirements (v1).md>): 12 puzzles, 556 campaign levels, all shared gameplay and progression, original sound, six themes, ads, the USD 2.99 Pro purchase, and production qualification. Traffic Jam is the first playable milestone, not a reduced launch scope.

Workspace inspection found only the requirements document. There is no app, Gradle project, level pipeline, test suite or Git repository yet. Everything below is planned work; no app functionality or release gate is claimed complete by this plan.

Retain the settled decisions: no app backend or Play Integrity; offline play; portable Kotlin engines; fair verified par targets; hints cap stars at two; totals count personal bests; Daily Challenges can include premium content for free players; local-date resets; Queens capped at 8x8; comfortable controls take priority over large boards. No accounts, analytics SDK, cloud save, multiplayer, subscriptions or iOS app in v1.

Execution starts with P0 and P1, then a polished Traffic Jam prototype in P2. Do not produce the remaining hundreds of levels until that prototype establishes the interaction, sound, persistence and content formats.

## Technical approach

Use Kotlin, Jetpack Compose and a single Android application with ViewModels exposing immutable screen state. Keep state transitions, puzzle rules and scoring separate from rendering. Use dependency injection through constructors and an explicit application composition root initially; avoid a framework dependency unless the project demonstrably needs one.

Proposed Gradle modules:

- `:app`: Android entry point, navigation, app lifecycle and dependency wiring.
- `:core:model`: portable level definitions, IDs, moves, serialized progress and versioned schema contracts.
- `:core:game`: engine interfaces, all 12 engine implementations, replay, completion validation and bounded hint search. No Android or Java platform imports; use Kotlin and portable libraries so a later KMP move is straightforward.
- `:core:progress`: deterministic scoring, personal bests, achievements, progression, hint/reward accounting and daily rules. Inject clocks and dates; never read Android state directly.
- `:core:data`: Room persistence and migrations, DataStore settings, content loading, export/import and Android backup integration. Put related progress/reward changes in one Room transaction; settings belong in DataStore. This division follows [Android's persistence guidance](https://developer.android.com/topic/libraries/architecture/datastore).
- `:core:platform`: Billing, UMP, AdMob, crash reporting, audio, haptics, photo/file pickers and notification adapters behind testable interfaces.
- `:feature:play`: shared Play/Results flow and puzzle-specific board renderers, including TalkBack semantics and magnified interaction.
- `:feature:library`: Home, level selection, daily entry/history, Stats/Achievements, Settings, Pro, onboarding and puzzle information.
- `:tools:levels`: offline generator, solver, importer and reference-solution verifier using the same engine rules as the app.
- `:benchmark`: release-like startup, frame timing and audio measurement scenarios; never shipped in the app.

Keep separate engine packages within `:core:game`; twelve independent Gradle modules are unnecessary initially. Module dependencies point from Android features/adapters into portable code, never the reverse. Bundle generated level data and reference solutions; do not bundle expensive generation tooling.

Choose and lock mutually compatible stable Kotlin, AGP, Gradle, Compose, Room, Billing, UMP and Mobile Ads versions in P1. Use a version catalog, Gradle wrapper and dependency verification. Minimum SDK remains 26. The currently published submission requirement is target API 36; recheck it and Billing-library deadlines at submission rather than relying on this document's date. [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk).

## Contracts to establish before content production

### Engine, moves and assistance

Each engine supplies initial state, legal actions, deterministic action application, goal validation, score cost, serializable state, and a reference-solution replay adapter. UI selection, animation and audio cannot mutate puzzle rules. A hint returns a verified continuation or an explicit recovery instruction; a legal move alone is not a sufficient hint.

Use these move conventions consistently in generators, HUDs and scoring:

- Hanoi: one disk transfer. Sliding Tiles: one adjacent tile slide.
- Traffic Jam and Block Escape: one uninterrupted legal translation of one piece, regardless of distance. The solver must use the same action definition.
- Box Pusher: one box push is one scored move; walking is recorded separately. Clearly label the board counter as pushes. Automatic walking may follow a legal shortest walking path, but must never decide a push for the player.
- Switch Grid: one toggle. Peg Solitaire: one jump. Water Jugs: one fill, empty or pour-until-source-empty/destination-full action. Pancake Flip: one prefix reversal.
- Ferry scenarios: one legal crossing. Bridge and torch: score the sum of the scenario's crossing durations, not real-world thinking time. Use the move-based star ratios against a reference duration and label the unit as minutes. Keep actual active play time separate for the time bonus.
- Queens: a valid complete arrangement, including any fixed queens, is a win even when different from the reference solution. Knight's Tour: every square is visited exactly once, with the final-to-first knight edge additionally required for closed tours.

For ordinary move-based puzzles, accepted forward moves increase scored cost; undo adds one penalty rather than subtracting the previous move; redo adds the reapplied move's cost. Box Pusher walking has zero forward score cost, but undo still incurs the shared penalty. For bridge and torch, undo adds the reversed crossing's duration and redo adds its duration again; display the accumulated scored duration separately from the board's current scenario state. Knight's Tour undo is a backtrack, including when immediately redone. Restart begins a new attempt and resets that attempt's counters.

Maintain reversible history even for engines whose forward actions are irreversible. Off-route hints first try bounded local search; otherwise identify a saved earlier state on a verified route and explicitly guide undo to it. Persist the hint-use flag with the attempt. Demonstrations use a separate session, preserve the player's attempt and never grant progress, points, streaks or achievements.

### Durable progress and rewards

- `LevelDefinition`: immutable ID/revision, puzzle/variant, tier/order, initial state, constraints, goal, reference solution, scoring unit/par classification, target time and provenance.
- `Attempt`: unique ID, level revision, campaign/daily/practice/demo mode, board state, undo/redo history, score counters, hint flag, elapsed active time, completion state and viewport/selection.
- `LevelBest`: independent best stars, best single-attempt points, best time, best moves and completion timestamp. Sum bests for campaign totals; never sum every completion.
- `DailyRecord`: challenge date, schedule version, level revision, eligibility/consumed attempts and best result. Retain detailed daily history for 30 days; preserve aggregate streak/achievement state separately.
- `RewardLedger`: unique reward/completion IDs, daily and earned hints, daily ad counts, temporary unlock expiries, reset high-water date and dated streak credits. Duplicate callbacks and imports cannot create a second award.
- `ProgressEnvelope`: one portable, versioned export model covering progress/settings with schema version and content version. Android database storage is an implementation detail. Exclude purchase tokens, entitlement cache and device consent from portable exports.

Save an accepted action and its counters/history transactionally before it becomes a committed board state. Perform storage on a background dispatcher and serialize inputs during the small commit window; animation can hide latency but cannot acknowledge an unsaved move. On write failure retain the last durable state and offer retry. On process death restore the last committed action without duplicate completion rewards. Save timer checkpoints during foreground play and on lifecycle transitions; the process-death test must cover timing as well as the board.

Use a monotonic clock for active elapsed time and ad spacing; wall-clock/local date for daily features and 24-hour unlock expiry. Pause timing on background, explicit pause, blocking system/ad surfaces and demonstrations. Local-clock safeguards are best effort and cannot make a backend-free app tamper-proof.

Import policy: validate the entire file, schema, level IDs, legal states and bounded sizes before mutation; stage it and replace local progress atomically after a clear confirmation. Never add two backups' balances together. Preserve the local reward/reset high-water records to prevent ordinary repeated-import farming, then reconcile entitlement separately with Play. Keep a recoverable pre-import snapshot. Imported or restored consent must be refreshed through UMP on that device.

### Content access and daily boundaries

Represent purchase entitlement, campaign progression, daily access and temporary rewarded access separately. A daily grant cannot become campaign ownership. Use `ceil(0.70 * eligible previous-tier levels)` for tier progression, excluding variant-only Pro locks from a free player's denominator so Hanoi cannot trap free players. A rewarded one-level unlock bypasses progression for that level only and never expands the progression denominator.

When a 24-hour unlock expires, preserve the board and previously earned bests. A foreground attempt already in progress may finish; returning later requires renewed access or Pro. Other free play remains available. Handle a Pro revocation the same way for an active board: preserve progress, remove future restricted access on reconciliation and explain the access state outside active play.

Generate daily assignments deterministically from local date, a frozen schedule version and an ordered pool of verified levels; include all tiers/variants, using bundled art for image puzzles. Persist the chosen level revision when opened. Define an unbounded date-to-assignment function, not a finite calendar that silently runs out. An update keeps any opened challenge unchanged and does not rewrite previously assigned dates. New pools begin on a documented future date. Offline versions can differ only when their schedule versions differ, as allowed by the requirements.

At first activity on a new local date, atomically reset the daily hint allowance to 3 and the rewarded-ad count, without discarding earned hints. Skip missed-day accumulation. Spend daily hints before earned hints. Do not award again when dates move backward. Do not prune reset/claim protection just because a 30-day daily detail record expires. Test midnight, timezone changes, daylight-saving boundaries, clock rollback and an active level spanning midnight.

## Delivery sequence

### P0 — Resolve practical risks and prepare the product foundation

Depends on: nothing. Engineering and design can start immediately; account and identity tasks can proceed alongside them.

- Record these implementation contracts and build the missing screen map: onboarding, Home, level select, daily entry/history, Play, Results, Stats with Achievements, Settings and Pro; puzzle information/tutorial replay is a sheet. Preserve the standard Android back stack, paused exit confirmation and one-tap resume.
- Prototype a 6x6 Traffic Jam board and magnified 8x8 interaction on the smallest phone layout before committing to board art. Check reach, overview legibility, 48 dp controls, TalkBack and 200% text.
- Prototype original pickup/slide/place/illegal/undo sounds with `SoundPool`; measure actual output latency early. [SoundPool is intended for short, low-latency effects](https://developer.android.com/reference/android/media/SoundPool). Establish a reproducible measurement rig for the under-50-ms target on built-in speakers across the supported-device test matrix, including the reference device; Bluetooth latency is device/transport dependent and must be reported separately.
- Prepare icon, palette, typography and Traffic Jam material/sound samples in the Cozy parlor direction. Maintain original source files and license/provenance records. Obtain Vincent's approval of concrete identity samples before producing store assets, as required by the source document.
- Start owner tasks: brand/domain/title clearance, Play developer and AdMob accounts, crash-reporting project, developer-site ownership, support contact, app signing/upload-key custody and billing configuration. Do not purchase, commission or publish anything merely because it appears in this plan.
- Omit optional music and the optional store video from initial v1 production. Keep the specified separate audio settings, with music visibly unavailable until a track exists; original SFX remain mandatory.

Exit evidence: screen map, device/audio feasibility notes, selected interaction approach, identity sample packet and account/dependency checklist. Outstanding brand clearance blocks final identity/publication, not engine work.

### P1 — Create the Android project and shared foundations

Depends on: P0's engine/data/interaction contracts. Brand clearance is not a prerequisite.

- Initialize local Git, Gradle modules, wrapper/version catalog, debug/release variants, API 26 support, current target SDK, lint and a repeatable local build. Add CI configuration once a repository host is selected; a remote is not required for local work.
- Implement portable models, engine/action contracts, fake clock, repository interfaces and test doubles for ads, Billing, sound and entitlements.
- Add Room schemas/migrations, DataStore settings, durable attempt transactions, versioned progress serialization and startup resume lookup.
- Scaffold original level manifests, stable IDs, reference replay and build-time schema checks. Seed with a small Traffic Jam pack only.
- Create theme/typography tokens, basic shared Play/Results components, semantics and insets/adaptive layout infrastructure. All strings/resources are externalized from the start.
- Add crash reporting without Analytics or custom behavioral event collection; disable crash uploads in normal debug runs and verify a controlled test crash in the internal build. Modern Crashlytics derives crash-free session metrics from its own SDK data. [Crash-free metrics documentation](https://firebase.google.com/docs/crashlytics/crash-free-metrics).

Exit evidence: clean-checkout debug build, JVM contract tests, Android lint, engine dependency check, save/restore migration smoke test and a CI-ready command list. Dependency inspection must show no Analytics SDK and no Android imports in portable modules.

### P2 — Deliver the complete Traffic Jam play loop

Depends on: P1 and the P0 phone/audio prototype.

- Implement Traffic Jam legality, exit/goal validation, action-cost convention, bounded BFS tooling and original board renderer.
- Author 12 representative original levels spanning all four tiers, drawn from the eventual 80; implement its 3–5-step interactive tutorial and replay entry point.
- Deliver Home → level select → Play → Results → next level, with selection/drag, illegal feedback, unlimited undo/redo, restart, pause, hidden timer, hint/recovery, solution demonstration and stars.
- Include original plastic effects and haptics in this milestone, with silent mode and reduced motion. Validate a one-thumb phone layout and tablet rotation.
- Kill/restart the process at action, undo, hint and completion boundaries. Exercise returning hours later and starting without network.
- Conduct observed short-session tests with intended players to evaluate clarity, comfort and whether improving a solution is satisfying. Treat these as product validation, not an assumption about every woman in the target age range.

Exit evidence: installable prototype; 12 verified boards; tutorial-to-results recording; tested resume/score behavior; measured startup, frame and sound baselines. Do not expand the full catalog until the core loop passes this gate.

### P3 — Finish shared progression, daily play and app navigation

Depends on: P2.

- Implement best-result scoring and target-time bonus, including floor/boundary cases; campaign/daily separation; sequential unlocks; 70% threshold; free tier and Pro skip rules.
- Implement the daily schedule/attempt rules, local-date accounting, daily hint allowance and reward ledger using fake providers before real SDKs.
- Add summary Stats plus the Pro detailed-history view, bests, average moves over par, total play time, streaks and the achievement evaluator. Define exactly 40 initial achievements, including the required examples; use stable IDs and idempotent awards.
- Complete onboarding, Home resume priority, Daily entry/history, puzzle information/tutorial replay and navigation/error/empty states. Home should work before Billing or ads return.
- Expose settings for timer, sound, haptics, motion, layout/theme, notifications, privacy, restore and per-puzzle/global reset; connect their domain behavior now.

Exit evidence: scoring examples and automated boundary tests; no points from repeated identical solves or demonstrations; no double daily reward after reload/import; viable free Hanoi progression; a complete navigable shell with fake service adapters.

### P4 — Implement the remaining engines and board renderers

Depends on: P3's stable shared behavior. Build each wave to its exit gate before the next.

- Wave A: Tower of Hanoi, Switch Grid, Water Jugs and Pancake Flip. These exercise stacking, toggles, discrete state search and reversal without the hardest board/search interactions.
- Wave B: Sliding Tiles, Block Escape and Box Pusher. Reuse only genuinely shared selection/rendering code; keep distinct rules, move costs, solvability and deadlock handling.
- Wave C: Peg Solitaire, Queens, Knight's Tour and Crossings, including every listed crossing scenario and bridge-and-torch scoring.

For each puzzle deliver its engine, renderer, accessible actions, material sound mapping, hint/recovery, demo, tutorial, representative tier levels, state round-trip and integration with every shared control. A puzzle is incomplete if only its engine exists. All engine-specific win rules must accept any valid solution rather than compare only with a reference layout.

Exit evidence per wave: engine/property tests, verified sample levels, full Play/Results integration, TalkBack completion, resume/undo/redo round-trip and a small-phone usability recording. Final phase exit requires all 12 integrated puzzle types.

### P5 — Produce and verify the complete 556-level catalog and assets

Depends on: each engine as it passes P4; final exit requires all of P4. Content and audio/art production may begin earlier for completed engines.

- Produce the exact puzzle totals in the catalog section below. Allocate four ordered tiers per puzzle: 16 as 4/4/4/4; 60 as 15/15/15/15; 50 as 13/12/13/12; 80 as 20/20/20/20; 40 as 10/10/10/10; 30 as 8/7/8/7. Track variant locks separately; confirm the resulting free-access counts rather than assuming all Easy/Medium rows are free.
- Generate/curate levels offline with fixed seeds, explicit resource budgets, canonical duplicate detection and original/licensed provenance. Never copy commercial level layouts.
- Store reference solutions, achievable pars/classification and positive target times. Verify every action, rule invariant, final goal and measured solution cost using the shipping engine.
- Require evidence for every proven-optimal label; search-budget exhaustion becomes reference par or a replacement level, not a false optimal claim. Calibrate target times and tier difficulty through observed playtesting without an analytics SDK.
- Finish six coherent themes/board skins, original illustrations/material cues, icons and every interaction/reward sound. Cover pickup, place, slide, illegal move, undo, hint, star, level completion and tier unlock; retain wood for Hanoi/pegs, plastic for tiles/blocks, metal for Switch Grid and water for jugs. Confirm font-embedding rights, 4.5:1 required contrast pairs, colour-blind readability and reduced-motion variants.
- Bundle tutorial data, bundled daily image art and a stable daily pool. Compress/package assets and inspect delivered AAB size throughout production.

Exit evidence: 556/556 legal reference replays; unique IDs and target times; tier/access report; optimality/provenance report; 12 completed tutorials; six tested themes; complete audio inventory; no missing assets or duplicate filler levels.

### P6 — Integrate purchases, consent and advertising

Depends on: P3; requires owner-configured test products/accounts. Can run alongside P4/P5.

- Implement one non-consumable Pro product at USD 2.99 base price; display the localized price returned by Play. Query/reconcile asynchronously on cold start, reconnect and foreground. Validate purchase data, process pending states, acknowledge with persisted retry and grant only after PURCHASED plus acknowledgement. Handle restore, promo flow, refund/revocation and offline cached access.
- Model entitlement as unresolved/free/Pro. Cached Pro skips Mobile Ads initialization while Billing reconciles. On a new installation, defer ad SDK initialization until Billing establishes free status; failure or uncertainty means ad-free navigation while the app remains playable. Test this with restored purchases, not only a local Pro switch.
- Gate ad requests on UMP's current permission to request ads; refresh consent status, expose Privacy Options when applicable and handle failure without stalling play. [Google's UMP flow](https://developers.google.com/admob/android/privacy).
- Lazy-initialize ads only after the free/consent gates; audit manifest providers and library startup behavior. After a free user purchases Pro, destroy ads, cancel requests and prevent all future ad activity. Already-initialized code cannot be retroactively uninitialized; subsequent Pro launches must not initialize it.
- Add adaptive banners on Home, level select, Results, Stats and Settings only. Interstitials preload after level start and show only on an eligible Next/Back action after completion: first eligible at lifetime completion 4, at least 3 completions since the last, at least 120 seconds apart, and at least 60 seconds since launch. Missed/failed ads never queue a later interruption or block navigation.
- Add explicitly initiated rewards: +2 hints, one Hard/Expert level for 24 hours, or one additional scored completion of today's daily. Persist each reward once; cap all rewarded formats together at 10 completed rewarded views per local date. Cancellation or failed load gives no reward and consumes no completed-view allowance. Recheck entitlement before display.
- Enforce no app-open ads, no ads on Play, teen-or-lower rating/category controls, debug-only test units and release-only production IDs. Add the exact allowed upsell placements, including every fifth dismissed interstitial and one prompt after completion 10, outside active play.

Exit evidence: instrumented state-machine tests plus Play license-tester/device evidence for pending/acknowledged/restored/revoked/offline purchases; consent-region tests; zero ad initialization on Pro cold starts; all frequency-boundary and duplicate-reward cases; normal play during provider failures. No real ad clicking as a test strategy.

### P7 — Complete the product experience and data portability

Depends on: P3–P6 and approved visual identity.

- Finish the Cozy parlor room Home, Traffic Jam prominence and accessible grid alternative; finalize all screen states and warm, plain copy without exclamation marks or gamer jargon. Validate first-launch consent/onboarding and the under-20-second first-move target under documented network conditions, with a usable offline path.
- Implement Pro photo mode through the Android photo picker, copying necessary image data into private storage so a revoked source URI does not break a saved board. Define crop/orientation, memory limits and deletion. No photo uploads or photo content in crash reports. Include selected puzzle images in versioned manual backups with limits and validation; Android automatic-backup capacity is a separate constraint.
- Finish Pro export/import, migration/recovery UI and explicit backup inclusion/exclusion rules. Reconcile Play entitlement after device transfer; importing a file cannot unlock Pro. Test unsupported future schemas, truncated files, repeated import and missing images without partially replacing progress.
- Complete per-puzzle/global reset confirmations. Reset only the requested progress scope; preserve purchase entitlement, consent and reward/anti-duplication records. Explain whether streaks/achievements will reset in the confirmation.
- Implement opt-in local notifications for the daily or a streak reminder, never both on one day and never promotions. Use best-effort platform scheduling, no exact-alarm permission or app-held wake locks; suppress after qualifying completion and handle denied permission/timezone changes.
- Finish tablet portrait/landscape, compact phones, cutouts, fold/unfold, all six themes, 200% text, TalkBack end-to-end, focus restoration, no colour-only state and reduced-motion celebrations. Launch in English with externalized strings, locale-formatted numbers/dates and tested RTL-safe layouts; preserve the 16 sp body minimum and 200–300 ms gentle motion, with warm charcoal dark mode and no flashing or screen shake.
- Reconcile the shared screen map and complete Settings, Privacy Options, restore, puzzle info, all tutorial replays, achievements and free-summary/Pro-detail presentation.

Exit evidence: every free/Pro capability exercised on a clean install, offline restart and restored install; all 12 puzzles pass accessibility/compact-phone checks; a full serialized progress round-trip preserves bests and attempts without granting entitlement.

### P8 — Qualify the release and run the closed beta

Depends on: P7 and all 556 verified levels.

- Run the release-build test/measurement matrix below, including obfuscation/mapping upload, release dependency/manifest audit and a network inspection showing only approved SDK traffic. Audit Data Safety disclosures against the actual dependencies and settings.
- Run a closed-track beta with at least 20 testers for at least 14 days; retain this project standard even though the current policy for applicable new personal accounts specifies 12 testers continuously opted in for 14 days. [Google's account-specific testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
- Recruit people representative of short-break play, including TalkBack users and small-phone/tablet owners. Capture structured observations and tester feedback; add no behavioral analytics SDK. Recalibrate unfair pars/target times while preserving level IDs/revisions appropriately.
- Resolve launch-blocking crashes, ANRs, lost progress, purchase/consent errors and inaccessible interactions. Re-run checks affected by fixes and repeat beta coverage for material changes.
- Complete trademark/title/domain resolution, privacy policy and developer site with `app-ads.txt`, IARC questionnaire, teen audience/Families settings, ads/IAP disclosure, Data Safety and support contact.
- Produce final icon, feature graphic, full listing and eight screenshots per supported store form factor, with Traffic Jam in the first three. The requirements request a 30-character short description; deliver that concise version, but document that Play's actual limits are title 30, short description 80 and full description 4,000. The chosen tagline can be used elsewhere if it exceeds the project's short-copy limit. [Play listing fields](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).
- Assemble the release evidence packet, signed AAB, reproducible source/tag, versioned content manifest, store configuration and recovery runbook. Record every outstanding launch dependency explicitly.

Exit evidence: closed-beta participation/date record, reproducible release build, full qualification report, ready store/account configuration, approved assets and no unresolved release blockers. The production crash-free target is not yet proved at this gate.

### P9 — Publish and verify production operation

Depends on: P8, successful Play review and owner readiness to publish the concrete release candidate. Publication is future work, not authorized by this planning task.

- Google Play does not offer a percentage-based staged rollout for an app's first publication. Use the completed closed beta to control prelaunch exposure; publish initially to explicitly chosen countries when ready. Use the specified 10% staged rollout for subsequent production updates, increasing only after evidence review. Do not label a first publication a 10% rollout. [Google's rollout limitation](https://support.google.com/googleplay/android-developer/answer/6346149?hl=en-IN).
- Verify the real production product, restore path, privacy options and ad serving configuration with compliant production checks. Record outcomes; test ads and fake purchases alone do not satisfy production verification.
- Maintain a release operations view linking Crashlytics/Android Vitals, AdMob and Play Console. Use their native reports and record the available sales/conversion metrics and definitions; do not invent a paywall funnel when no analytics events exist.
- Monitor for the first 14 production days: crash-free sessions at least 99.5%, no observed ANRs in the top-20 device report, purchase/ad operational health and reports of lost state. Report counts, device coverage and data delays; a percentage with no traffic is not proof.
- On a severe problem, halt a staged update if applicable, pause expansion/unpublish new availability as appropriate, and ship a corrected higher-version build. Already installed users are not automatically downgraded. Keep forward-compatible migrations and a tested recovery build/runbook rather than assuming a store rollback restores old code.

Exit evidence: approved live listing, documented production ads/purchase/restore checks, 14-day stability/ANR report and completed release-operations record. Only then is the app's production definition of done met.

## Catalog and algorithm work packages

All counts below are launch requirements, not estimates. Total: 556 campaign levels, with up to 1,668 campaign stars.

- **Traffic Jam — 80:** original 6x6 layouts, 8–50+ moves; BFS using whole-piece translations; collision/exit tests; reusable plastic movement interaction. First 12 built in P2, remaining 68 in P5.
- **Tower of Hanoi — 16:** 3–10 disks across three- and four-peg modes; three-peg formula and four-peg Frame–Stewart reference generation. Replay and classify every par; do not label a general algorithm optimal without evidence. Balance variant locks with the free progression denominator. The 10-disk achievement remains; explain that long Expert puzzles can span many short sessions.
- **Sliding Tiles — 60:** 3x3, 4x4 and 5x5, numbers and Pro photos; generate by legal reverse moves and verify goal/parity; bounded IDA* for optimal claims. Use verified reference routes when a large board exceeds solver budget. Test tile identity, orientation and image persistence.
- **Block Escape — 50:** original layouts covering roughly 10–200+ moves; BFS where tractable and verified reference routes otherwise; canonicalize indistinguishable pieces for solver search while maintaining stable display IDs.
- **Box Pusher — 60:** original boards with 1–6 boxes; curated reference pushes/paths, wall/box/goal invariants, deadlock detection and recoverable undo. Verify worker reachability between reference pushes and bounded hint fallback.
- **Switch Grid — 60:** solvable 3x3–8x8 systems; solve over GF(2), with a stored toggle sequence. Linear solvability alone does not prove minimum toggles: examine null-space alternatives when tractable or classify par as a reference target.
- **Peg Solitaire — 40:** English, European, triangle and diamond layouts with explicitly configured start/goal holes; store complete legal jump sequences. Count jumps and validate final peg/goal; no assumption that every visually plausible start is solvable.
- **Queens — 30:** 4x4–8x8 with solvable fixed starting queens/constraints; validate row/column/diagonal attacks. Preserve meaningful variety within the smaller sizes. A complete valid alternative solution is accepted.
- **Knight's Tour — 30:** 5x5–8x8 open tours, then closed tours on suitable boards; a closed tour on an odd-cell unmodified square board is impossible because every knight step changes square colour, so exclude closed 5x5/7x7. Verify every edge, square count and closure; track backtracks independently from hints.
- **Water Jugs — 50:** two or three jugs, legal fill/empty/pour actions, exact capacity/target predicates and BFS routes covering roughly 3–20 actions. Check integer arithmetic and conservation for pours.
- **Crossings — 40:** wolf/goat/cabbage, missionaries/cannibals, jealous couples and bridge/torch; encode scenario-specific safety/capacity constraints and actor positions explicitly. BFS works for unit-cost crossings; bridge/torch needs weighted shortest-path search, such as Dijkstra, to claim minimum duration. Preserve generic/original presentation and verified scenario solutions.
- **Pancake Flip — 40:** distinct stacks of 4–12, prefix reversals and A* under offline memory/time limits; reject trivial no-op flips from move generation; reference routes allowed where optimal proof exceeds budget.

Set generator budgets and seed manifests in source control. Every build verifies committed content; expensive regeneration is an explicit tooling task. When a board fails phone usability, replace its content with a smaller variant of the same puzzle/tier and maintain its required count. Revisit any specific achievement that depends on a rejected board through an explicit requirement change; do not silently remove the required closed 8x8 Knight's Tour achievement.

## Dependency order, ownership and first work items

Critical dependency sequence: P0 contracts → P1 foundations → P2 Traffic Jam → P3 shared rules → P4 engines → P5 full catalog → P7 integration → P8 beta → P9 publication/observation. P6 monetization runs after P3 alongside P4/P5. Original art/SFX and account/site preparations start in P0 and must land before their gates.

Engineering owns implementation, automated verification and technical evidence. Content/design/audio owns original level/asset production and difficulty/interaction validation. Vincent owns account access, brand/identity decisions, spending/commissioning and the final concrete publication decision. These are responsibilities, not an assumption that multiple people or parallel agents are available.

Do not promise a calendar launch date before P2 measures implementation throughput and the real-device interaction/audio risks. After P2, estimate the remaining engine work and content production separately and schedule external dependencies. Reserve the mandatory 14-day closed beta and subsequent 14-day production observation as elapsed calendar gates, in addition to development and Play review time.

First five implementation changes, each reviewable independently:

1. **Foundation:** initialize Git and the Gradle/modules/version catalog; prove a clean build and pure-engine dependency boundary.
2. **Engine/content contract:** define Traffic Jam state/actions, stable level schema and a reference replay verifier; test illegal actions and goal detection.
3. **Durable play session:** transactional moves/undo/redo, serialized restore and idempotent completion; inject process-death/failure cases.
4. **Playable board:** Compose renderer, tap/drag/TalkBack, responsive targets, original SFX/haptics and measured latency.
5. **Complete first loop:** tutorial, 12 reference levels, score/results/next/resume and observed short-session playtest.

## Verification and release evidence

Define these verification tasks during P1; they are proposed interfaces, not currently available commands:

- `./gradlew :core:model:test :core:game:test :core:progress:test`: serialization, rule/score/date/reward invariants and regression fixtures for every engine.
- `./gradlew :tools:levels:verifyCatalog`: 556 count, IDs, tiers, reference replay/goal/cost, par classification, positive times, provenance and required variants. Fail the release build if any check fails.
- `./gradlew :app:lintDebug :app:assembleDebug`: architecture/resources/manifest checks and build smoke test.
- `./gradlew :app:connectedDebugAndroidTest`: save/restore, lifecycle, UI semantics, consent/Billing adapter and reward flows on the device matrix. Supplement with actual Play test-track transactions and manual TalkBack/device checks; mocks cannot establish service integration.
- `./gradlew :benchmark:connectedCheck`: release-like startup/frame scenarios and benchmark artifacts. Audio latency has its own recorded physical measurement procedure.
- `./gradlew :app:bundleRelease`: signed candidate through the configured release environment; upload keys and credentials stay outside source control.

Every merge runs portable tests, lint and changed-content verification. Before a release candidate, run the complete catalog verifier and the full affected Android/device suite. Archive evidence against the exact source revision, content checksum and build version.

Device coverage must include API 26, a current API phone, a selected physical 2019 mid-range reference device, the smallest supported phone layout, a 6.7-inch phone, 600-dp/large tablets in both orientations and fold/cutout transitions. Use physical devices for performance, sound and real Billing/ads checks; emulators supplement layout/API coverage.

Acceptance measurements:

- **Play and storage:** no lost committed move/history/hint flag after process death; no duplicated results; offline access; successful migration and backup import/recovery; no gameplay wake locks.
- **Performance:** cold start below 2 seconds and 60-fps play on the named reference device; record distributions/traces rather than a single best run. Check the 80-level selector and repeated scene/theme transitions for jank/leaks.
- **Audio:** all required action/reward cues and family materials present, under-50-ms output measured across the supported-device test matrix, paired haptics, silent mode and independent volume settings. Record measurement method and output route with each result.
- **Accessibility:** all 12 tutorials/boards, navigation, dialogs and services usable with TalkBack; 48 dp targets; 200% text without clipping; required 4.5:1 contrasts, deuteranopia/protanopia checks and non-colour state; reduced-motion behavior.
- **Packaging:** delivered AAB download under 40 MB on representative device configurations, no generator/test assets in release, obfuscation enabled, no embedded private credentials, no production ad units in debug.
- **Business rules:** verify both sides of every star/time/par threshold, hint cap, undo/redo cost, skip/tier gates, best totals, streak milestones, all reward caps, daily access/eligibility, temporary expiry and no Pro ad startup.
- **Store/production:** acceptance evidence from P8 plus live checks and the full 14-day P9 measurement window; distinguish crash-free sessions from crash-free users.

## Requirement coverage audit for the plan

Each entry identifies where implementation is scheduled and what will demonstrate app completion. This is traceability for future work, not a claim those tests have run.

- Overview/platform/offline/audience → P0–P3, P7–P9; portable-module audit, observed short sessions, offline install/resume, API/device and production reports.
- Twelve puzzles and exact ladders → P2, P4, P5; catalog package counts sum to 556 and 556 reference replays pass.
- Shared controls/tutorials/hints/demo/pause → P1–P4; every-puzzle integration checklist, process-death tests, hint-route validation and 12 tutorial recordings.
- Audio as a headline feature → P0, P2, P5, P7; original inventory/provenance, sound/haptic settings and measured latency.
- Scoring/progression/stats/40 achievements → P3–P5; deterministic boundary fixtures, best-score totals and unlock/achievement records.
- Daily/local-day rules → P3, P6, P7; premium daily access, best-only retries, rollover/clock/history tests and persistent claim protection.
- Every free/Pro distinction → P3, P6, P7; capability matrix exercised for free, Pro, daily grants, temporary access and revoked entitlements.
- Purchases/restore/pending/refunds/promo/upsells → P6, P9; actual test-track lifecycle evidence and compliant production checks.
- Ads/consent/caps/privacy options → P6, P8, P9; state-machine boundary tests, UMP device evidence, SDK startup audit and developer-site verification.
- All screens/first launch/tablets/navigation → P0, P2, P3, P7; screen map and end-to-end navigation/adaptive-layout recordings, including first-move timing.
- Persistence/backup/reset/no analytics → P1, P3, P7; migration/process-death/round-trip tests, reward-safe resets and dependency/network audit.
- Performance/size/stability/battery → P0, P2, P5, P8, P9; named-device benchmarks, bundle size, wake-lock audit and production monitoring.
- Accessibility/localization-ready layouts → P1, P2, P4, P7, P8; TalkBack task completion, font/contrast/RTL tests and externalized-string inspection.
- Security/privacy/IP/rating/listing → P0, P5, P6, P8; SDK/disclosure audit, provenance licenses, account configuration and store asset packet.
- Beta/release/rollback/monitoring → P8, P9; 20-tester/14-day record, first-publication procedure, subsequent 10% update rollout and tested recovery runbook.
- Post-launch compatibility → P1, P3, P5, P7; portable engine interfaces, stable content IDs, extensible product/access model and one serialized progress envelope. Roadmap features themselves remain outside v1.
- Name clearance and visual approval → P0, P8; owner clearance/approval records before final store asset production/publication.

## Planning corrections and dependencies to carry forward

This plan leaves the product requirements file intact. Record these reconciliations when implementation begins:

- First production publication cannot use a 10% staged rollout; apply that control to updates and use the beta/selected-country launch procedure for first publication.
- A 30-character short description is a project copy constraint; Play's short-description capacity is 80, while the title capacity is 30.
- Bridge-and-torch optimization uses weighted duration, so plain unweighted BFS is insufficient for a minimum-time claim.
- Pro cannot undo SDK initialization that occurred while a user was free. The enforceable behavior is no ad activity after upgrade and no ad SDK initialization on subsequent Pro launches, including restored entitlement resolution.
- The 99.5% first-two-weeks production target is a post-publication gate. It cannot honestly be certified before the app ships.
- Final trademark/domain clearance, approved identity, service accounts, tester availability and release authorization remain external dependencies. None blocks completing this plan or starting the portable code and prototype.

The implementation plan is complete when its scope matches the current requirements, every required capability has a scheduled work package and evidence gate, dependencies are explicit, and the first implementation changes are actionable. Building and publishing the app is the next objective.
