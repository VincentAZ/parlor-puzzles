# Implementation decisions

2026-09-28 · foundation and Traffic Jam prototype

- Follow the requirements and P0/P1/P2 sequence; do not expand the full catalog
  until the prototype passes interaction, storage and sound gates.
- Retain the generic engine and versioned level/attempt contracts from the plan.
  Traffic Jam translations count once regardless of distance. Sliding the target
  completely through the right edge is an explicit final action; a single action
  can slide it from any clear position through the exit. Generator, verifier,
  hints, controls and par use exactly that convention.
- Each accepted move, history update, score counter, time checkpoint and completion
  reward commits through one Room transaction. UI state comes from durable storage.
  Invalid moves still save elapsed active time. Navigation saves before leaving.
- Undo increases scored cost; redo increases it again. Hint use remains attached
  to an attempt after undo, pause and reload. A restart creates a new attempt and
  retains spent hints and previous personal bests.
- Warm linen/terracotta Compose tokens and code-drawn tabletop art are provisional
  identity samples. The source requirements still require Vincent's approval of
  final identity before store asset production. No final store assets exist.
- Use the documented compatible AGP 8.13.2 / Gradle 8.13 / JDK 17 combination and
  Kotlin/Compose compiler 2.2.21. Library upgrade warnings do not justify changing
  the tested set during the first prototype. Recheck submission requirements at
  the release gate. Compatibility source:
  https://developer.android.com/build/releases/agp-8-13-0-release-notes
- The baseline SDK and emulator are installed outside the repository. Standard
  Gradle/JDK/SDK setup is sufficient on another machine; `local.properties` and
  downloaded tools are not committed.
- Bundled prototype levels occupy the first three IDs in each eventual 20-level
  Traffic Jam tier. Additional levels can be inserted without renumbering IDs.
- Feature resources use `library_` and `play_` prefixes. Android merges resources
  across modules; non-transitive R classes alone do not prevent duplicate names
  from replacing different copies, as the installed resume screen revealed.
- The full release verifier intentionally rejects the prototype catalog. Passing
  `verifyPrototype` never establishes 556-level completion or release readiness.

Pending P0 decisions/evidence: smallest supported physical phone and named 2019
reference device, end-to-end TalkBack review, physical audio latency rig, identity
approval, brand/domain clearance and owner-controlled service accounts.

2026-09-28 · daily play and shared progression

- Bundled daily schedules are distinct from campaign file order. Each epoch freezes
  an ordered pool and effective date; assignment is unbounded and cycles through
  the whole pool. Opening a date saves its entire level definition and revision.
- Daily eligibility belongs to the attempt. Merely opening, pausing or crossing
  midnight cannot consume or replace it. Restarting a practice attempt may use a
  newly earned retry. Pro retries preserve free rewarded credits.
- Thirty-day history pruning removes detail, not permanent claim protection or
  aggregate daily bests. A foreground daily retains its saved row long enough to
  finish across midnight; it cannot be reopened outside the history window.
- All three rewarded choices share one ten-per-local-date allowance and unique
  callback receipts. No development button simulates an earned ad reward.
- Reset clears boards, bests and play time for the chosen scope. Streaks, earned
  achievements, hints, reward/claim history and entitlement/consent remain. The
  dialog explains this behavior; resetting does not renew daily scoring access.
- Room v1→v2 adds daily rows and per-puzzle profile data without destructive fallback.
  The v1 prototype contained only Traffic Jam, so its play time is attributed there.
- The separate Pro demonstration has no repository write or reward path. It starts
  from the reference board after the player's timer has been checkpointed/paused.
- Benchmark targets use `.benchmark`, debug signing and release minification. They
  cannot overwrite debug/production saves. Release catalog verification remains
  mandatory for actual release builds; prototype measurements use this distinct
  benchmark variant. Emulator smoke runs are not physical performance evidence.

- Access is rechecked on warm Activity returns as well as cold starts. Input queued
  during a failed return check cannot move the expired board. Timer checkpoints
  may still save time from the previously authorized foreground session.
- A schedule assignment is frozen when its Daily screen is opened, not merely
  because the player launched Home or a campaign puzzle.

2026-09-28 · four Wave A families

- Add sixteen representative levels, one per tier/family; preserve the final tier
  ID slots. This is sample integration before the full 556-level content gate.
- A versioned catalog index loads separate content files. Keep the first daily
  epoch unchanged; append version 2 effective 2026-10-01 with all 28 references.
  Opening older dates retains their original assignments and saved revisions.
- Three-peg Hanoi uses the formula; four pegs use Frame–Stewart reference routes.
  No general four-peg optimum claim is made. Long ten-disk boards explain that
  play can span several visits, while sharing normal durable moves/history.
- Switch Grid solves GF(2) systems and enumerates null-space alternatives within
  an explicit work budget before claiming minimum toggles. Pancake A* uses an
  admissible gap heuristic and a bounded discovered-state budget. Jug BFS checks
  exact goals; pours end at an empty source or full destination.
- Off-reference hints use a replay-verified continuation (recursive Hanoi, switch
  algebra, constructive pancake sorting, or bounded jug BFS). A legal move alone
  is never presented as a solution hint.
- Compact switches retain a full overview beside magnified targets. Hanoi uses
  whole-peg controls and accessible transfers; jugs expose current/capacity and
  fill/empty/pour actions. Pancake stacks remain wholly visible with a large
  cut-position selector and explicit Flip confirmation. All retain view/selection.
- New family interaction audio uses original wood, metal and water synthesis.
  Placement/toggle/fill and sliding/pouring/flipping select distinct cues. Acoustic
  latency, subjective audio approval and the full reward-cue audit remain open.


2026-09-28 · three Wave B families

- Add twelve representative tile/block/box boards, preserving eventual tier ID
  slots. The development catalog now contains 40 levels across eight families.
  Full content expansion and physical/player qualification remain separate gates.
- Tile IDA* is bounded and checks parity. Block BFS canonicalizes interchangeable
  pieces for search while retaining display identities in the reconstructed route.
  Box search explores pushes, canonicalizes reachable worker regions, and supplies
  explicit shortest walks between pushes. It never chooses a push during walking.
- A box push costs one; walking is counted separately at zero forward score cost.
  Undo, including undo of a walk, adds one scored push. Redo reapplies the action's
  push/walking cost. These counters and the viewport survive disk reopen.
- Deadlock detection covers reverse-pull dead squares and frozen 2×2 groups.
  Deadlock pushes remain legal; the board warns and verified recovery hints guide
  undo. Bounded search exhaustion never establishes impossibility or optimality.
- Expert tiles and boxes use reference pars when solver proof exceeds the budget.
  All block samples and the other new tile/box samples have proven-cost checks.
- Original bundled garden art enables image Daily challenges without personal
  photos. Free Daily players can toggle number labels and inspect the full image.
  Own-photo selection and image backup remain P7 work.
- Append schedule epoch 3 effective 2026-10-04 with all 40 references; epochs 1
  and 2 remain immutable. Regenerating an earlier wave preserves later content.
- Dense boards use the shared 48-dp planning grid with persistent full overview,
  labelled pan buttons and distinct worker controls. Overview taps only pan.
  Block edges join into rectangles; tile crops preserve fixed image coordinates.

2026-09-28 · four Wave C families

- Add sixteen tier samples for Peg Solitaire, Queens, Knight’s Tour and Crossings,
  bringing sample integration to all twelve families and 56 levels. The full
  556-level catalog and physical/player qualification gates remain unchanged.
- Peg layouts encode English, European, triangular and diamond topology and a
  final goal hole. Every successful jump removes one peg, proving the stored jump
  cost. Triangle rows retain full-size targets and support both-axis drag gestures.
- Queens accepts any valid complete arrangement respecting fixed/blocked cells.
  A bounded continuation solver may explicitly recommend removing a movable
  queen when the current placement cannot be extended; it never silently resets.
- Knight tours verify all edges, unique coverage and closed-tour closure. The
  sample ladder progresses through open 5×5/6×6/7×7 to closed 8×8. Closed odd-size
  square boards are rejected. Undo increments backtracks even when redone; moves,
  undo and redo follow the current knight in the magnified view.
- Crossing rules validate both banks and the travelling party. The farmer is an
  implicit pilot in the food ferry. Bridge search uses Dijkstra and scored minutes;
  undo/redo add crossing duration independently of the restored scenario clock.
- Dense peg/chess boards use full overviews, 48-dp targets and accessible actions.
  Crossing parties require explicit selection and confirmation. Time-based results
  show active time and target even when the live timer is hidden.
- Append frozen daily epoch 4 effective 2026-10-07 with all 56 references. Earlier
  epochs and opened challenge revisions remain unchanged.

2026-09-28 · client-only Google Play Billing

- Pin Billing 9.1.0 and configure one non-consumable Pro SKU through Gradle public
  properties. Pricing/offer tokens come from Play. Unconfigured development builds
  remain playable and explicitly show unavailable purchasing; no fake purchase path.
- Keep unknown/free/Pro ownership distinct. Local cache loading precedes Home,
  while network reconciliation is asynchronous. Only confirmed free status permits
  future ad initialization; Ads/UMP integration remains separate P6 work.
- Validate signed receipt fields locally, serialize updates by purchase token and
  grant only paid, acknowledged, durably saved ownership. Only successful complete
  purchase queries revoke; offline failures retain Pro. Full signature/receipt
  interoperability awaits configured Play transactions.
- Keep a bounded no-backup journal of revoked token hashes so delayed purchase
  callbacks cannot undo an authoritative refund reconciliation, including after
  process death. A subsequent full ownership query can restore a removed token.
- Store receipts in AtomicFile under noBackupFilesDir, outside progress. Queue a
  persisted JobScheduler retry before writing unacknowledged receipts, with
  foreground/reconnect fallback if scheduling fails. No app wake lock is used.
- The storeTest variant is debug-signed but has the production package required by
  license testers. It verifies sample content and requires a public key. Release
  continues to require the full catalog and Billing configuration. Neither variant
  authorizes store publication. See BILLING_SETUP.md for the device test procedure.

2026-09-28 · consent, banners and Results interstitials

- Use stable GMA Next-Gen 1.5.0 and UMP 4.0.0. Explicit initialization runs only
  after known Free and current UMP permission. Construction, cached Pro and
  unresolved purchases do not initialize GMA. Non-release variants use Google
  demo units; release gates require production app and format IDs.
- Separate consent-form loading from presentation so late callbacks cannot
  interrupt an active board. UMP owns the stored consent decision. Privacy options
  may be explicitly reopened after Pro without permitting ads.
- Reserve WorkManager for the SDK's offline work. Remove its startup initializer
  and use a permission-gated worker factory before GMA startup. Suppress persisted
  SDK work on an ineligible cold start; Billing keeps its native JobScheduler.
  Remove wake-lock/foreground-worker permissions and unused services on API 26+.
- Keep banners in bottom slots outside scrolling content. Invalidate resource
  leases on ownership, consent, foreground and screen changes; stop interstitial
  preloaders when leaving the play/results flow or losing eligibility.
- Record Results departures transactionally, keep frequency/completion claims
  through reset, and use monotonic time. Anchor the 120-second interval at close;
  unsettled records conservatively restart it after process death. A no-backup
  installation ID distinguishes boot clocks after progress transfer. If its
  durable clock identity is unavailable, use a process-specific marker.
- Marshal Results navigation explicitly onto Android's main dispatcher after
  Room work. Missing ads and failed/slow claims must continue navigation.
- SDK callback tests use injected consent/ownership and real Google demo ads,
  independently of real regional consent and Play qualification. The API 26
  emulator's old WebView crops the demo image; record that visual limitation
  instead of treating callback success as release acceptance. See ADS_SETUP.md.

2026-09-28 · explicit rewarded offers and durable delivery

- Keep the three rewards behind a separate Watch an ad action. Opening an offer
  performs no SDK load; loading is cancellable and bounded to 20 seconds, with
  bounded entitlement/cap checks before loading and presentation. A missing ad
  never queues a later interruption. Pro and consent loss invalidate ad resources.
- Checkpoint and pause the active board before offering hint rewards. Increment
  the input generation at pause/background boundaries so already queued input
  cannot mutate the board on rapid return. Earning hints does not use a hint.
- Capture an immutable receipt only from the SDK earned callback. Application-owned
  work writes a bounded AtomicFile journal under noBackupFilesDir, applies the
  reward/count/claim in one Room transaction, then clears the journal. Replaying
  a receipt after a crash cannot duplicate its reward. A failed first write remains
  in memory with an explicit retry; process death before durable write is not
  recoverable without a server. Malformed journals fail closed without replacement.
- Retain counts by actual earned local date, migrating the prior reset-date count.
  Recovery after midnight cannot issue another daily allowance or move a view to
  a different day's cap. Twenty-four-hour expiry starts at earning, not recovery.
  A reward already earned remains owed after a Pro upgrade.
- Treat the Daily target as frozen when the ad starts. If the earned callback
  crosses midnight, honor that offered challenge's retry and count the view on
  the callback date. This interpretation of an unspecified edge was presented
  for owner steering; no new offer is allowed for a past date. The requirement
  files remain unchanged. Starting the retry is explicit and may replace an
  unfinished practice board; earning alone preserves that board.
- Qualify the real Google demo callback separately from deterministic tests and
  actual regional consent/Play production checks. The earned callback can precede
  the end card; the fixture waits for a visible close control before closing.
  Compact reward-dialog checks use Android's actual 200% font setting and a
  320×640-dp display, including scrolling to the full explanation and cap.

2026-09-28 · manual backup, replacement and recovery

- Use the system document picker for Pro's single UTF-8 JSON file. Schema 2 is
  the first user-facing export; the earlier schema 1 model was never a released
  file contract. Validate schema, content, all twelve board/history types,
  claims, counters and bounded sizes before offering a replacement preview.
  Copy selected bytes into private AtomicFile staging so a provider change
  cannot alter the file after the player reviews it.
- Commit replacement and the previous-progress snapshot in one Room transaction.
  Room v3 also records pending settings. Finish that DataStore write and clear
  the marker before returning to play; startup retries an interrupted finish.
  Recovery of the app's own previous snapshot remains available after Pro loss.
  A second ordinary import replaces that single recovery copy.
- Read a large recovery snapshot in bounded SQL substrings, avoiding the API 26
  cursor limit. Ordinary serialized attempts and the combined ledger/profile
  have a 1 MiB row limit; the full file has a 32 MiB limit. Reject oversized input
  without partial replacement. Keep recovery/settings operations independent
  of Activity lifetime, while a slow document read/export does not block Home.
- Preserve completion and reward claims, latest reset date, dated view/retry
  counts, achievements, cumulative earned-hint spending and sticky hint use.
  Repeated import replaces balances conservatively and never adds them.
  Recheck Daily eligibility against retained local claims. Preserve original
  temporary-access expiry and current device notification opt-in.
- Exclude purchase tokens, ownership and device consent. Reconcile Billing
  separately after import. Pending earned rewards must finish before backup
  work starts. Own-photo payloads must extend the format when photo mode ships;
  this increment does not complete P7 or generic database-corruption recovery.

2026-09-28 · optional local reminders

- Offer one selected kind (Daily or streak), one local time and an explicit opt-in.
  Both kinds share a device-private latest-delivery date. A completion that day
  suppresses either; streak mode also needs yesterday's credit. Foreground use
  suppresses delivery, with a short grace period across Activity recreation.
- Use one inexact, non-waking RTC alarm and a low-importance notification channel.
  Enable the private boot/time-change receiver only for opted-in reminders. No
  exact-alarm permission, app wake lock, background ad initialization or network
  dependency is introduced. Arrival is best effort and expired dates are skipped.
- Claim the date durably before posting, accepting a possible missed reminder at
  a crash boundary to prevent duplicates. Hold the final completion check/post
  within the Room transaction; recheck permission, visibility and time after the
  private write. Opt-out/time/kind changes invalidate a waiting delivery.
- Keep reminder preferences and claims outside every backup/transfer path.
  Preserve the old settings wire field without using it for delivery. Corrupt
  records fail closed and remain available for diagnosis; failed opt-out writes
  expose retry and Android notification controls.
- Retain the Activity launch intent. A separate saved pending request tracks
  notification navigation through onboarding/recreation and is consumed after
  opening the current Daily. Warm notification intents do not replace task identity.

2026-09-28 · personal puzzle photos

- Keep photo IDs on attempts, outside immutable puzzle states/history. Campaign
  Tiles may use private photos; Daily always uses the bundled image. Pro loss
  keeps the copy/progress and displays numbers. A restart keeps its selected image.
- Use the native photo-picker contract with its document fallback, no broad media
  permission and no persisted source URI. Confirm a center crop after orientation
  correction. Store a bounded sRGB JPEG without source metadata; show a still frame
  for animation. Temporary input copies are private and removed on finish/startup.
- Store bounded images in Room v4 so image/reference/import/recovery commits are
  atomic. An 8 MiB current-image budget and 512 KiB row limit protect storage and
  older cursor windows. Schema 3 manual files include bounded image chunks; schema
  2 remains accepted without images. No image or URI is sent to service SDKs.
- Make privacy deletion available without Pro. Remove current/history/recovery
  references and staged import copies, serializing it with backup operations.
  External originals and exported files remain under the user's control.
- Keep Android's automatic-backup quota separate from manual/image limits and
  explain the limit in Settings. Physical heap/performance, providers, permission
  revocation, automatic transfer and human accessibility remain qualification work.

2026-09-28 · room themes and appearance

- Keep exactly six stable room IDs with Linen as the free default. Light is the
  initial appearance; Free and Pro may choose Dark or follow Android. Theme and
  appearance are independent, backed-up settings rather than engine state.
- Preserve a premium theme through Pro loss but display Linen until confirmed
  ownership returns. Check selection inside the DataStore transaction. An explicit
  Linen choice replaces the retained premium selection.
- Centralize Material and board colors in `:core:design`; preserve family materials,
  readable labels/non-colour state and unmodified photo/bundled-picture pixels.
  Keep the action accent terracotta and dark backgrounds warm charcoal.
- Advance manual files to schema 4 for the new appearance/theme contract; continue
  accepting schema 2/3 as Linen/light. Keep Room at schema 4. Never import ownership.
- Treat palette contrast and color-vision simulation as automated evidence with
  defined scope. Disabled/default component states, human accessibility, physical
  transition performance and final identity approval remain qualification work.
