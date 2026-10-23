# Parlor Puzzles — Android App Requirements (v1)

Sep 28, 2026 · @Vincent Serpico

## Overview

One Android app bundling 12 classic move-sequence puzzles under a shared progression, scoring and monetization system. The free tier is ad-supported with a subset of levels per puzzle; a one-time Pro purchase removes all ads and unlocks everything. Everything is local: no accounts, no backend, no leaderboards in v1.

- Platform: Android phones and tablets via Google Play. iOS is out of scope for v1, but the game logic must be portable (pure Kotlin, no Android dependencies in puzzle engines).
- Audience: primary is women 25 to 50 playing in short breaks between family and household demands; secondary is any casual or logic-puzzle player. Rated 13+, not directed at children.
- Business model: AdMob on the free tier; one one-time in-app purchase (Pro) through Google Play Billing.
- Connectivity: fully playable offline. Network is used only for ads, purchases and crash reporting.
- Out of scope for v1: accounts, cloud sync, leaderboards, multiplayer, subscriptions, user-created levels, level sharing.
- Definition of done: passes Google Play review, crash-free rate at or above 99.5% over the first two weeks, all 12 puzzles complete with full level ladders, ads and purchase verified in production.

What the audience changes:

- Sessions of 1 to 5 minutes. Any level can be left mid-move and resumed hours later; one tap from launch gets back to the board.
- No pressure mechanics: no lives, energy meters or countdowns. The timer runs for scoring but is hidden by default.
- One-thumb portrait play with 48 dp targets; boards sized for one-handed reach on phones up to 6.7 in.
- Calm reward: completion moments are satisfying and soft; progress is visible at a glance on Home.
- Repeat appeal comes from solving a satisfying problem, discovering a new mechanic and improving a personal best. Easy controls do not mean automatic wins: early levels teach, later levels require planning, and repeating an unchanged result never farms points.
- Notifications: opt-in only, at most one per day, for the daily puzzle or a streak about to lapse. Never for promotions.
- Copy tone: plain and warm, no exclamation marks, no gamer jargon.

## Puzzle roster (12 at launch)

Twelve puzzles ship in v1, chosen for touch-friendliness, verified solutions and a variety of mechanics. Trademarked names (Rush Hour, Sokoban, Lights Out, Rubik's) are not used; in-app names below are generic and final art must be original.

| # | In-app name | Basis | Mechanic family | Difficulty scaling | Par / solution source | Levels |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Tower of Hanoi | Tower of Hanoi | Recursive stacking | 3 to 10 disks; 4-peg variant (Reve's) is Pro-only in the campaign and free when featured in the Daily Challenge | Formula (2^n - 1); Frame-Stewart for 4 pegs | 16 |
| 2 | Sliding Tiles | 15-puzzle | Sliding | 3x3, 4x4, 5x5; number or image mode | IDA\* solver at generation time | 60 |
| 3 | Block Escape | Klotski | Sliding | Curated boards, 10 to 200+ moves | BFS at generation time | 50 |
| 4 | Traffic Jam | Rush Hour-style | Sliding | 6x6 curated boards, 8 to 50+ moves | BFS at generation time | 80 |
| 5 | Box Pusher | Sokoban-style | Push | Curated boards, 1 to 6 boxes | Curated par (solver too slow for hard boards) | 60 |
| 6 | Switch Grid | Lights Out-style | Toggle | 3x3 to 8x8, solvable boards only | Linear algebra over GF(2) | 60 |
| 7 | Peg Solitaire | Peg solitaire | Jump-and-remove | English, European, triangle and diamond boards | Curated par | 40 |
| 8 | Queens | N-Queens | Placement | 4x4 to 8x8; varied fixed starting queens and constraints supply the level ladder | Not move-based; scored on time and hints | 30 |
| 9 | Knight's Tour | Knight's tour | Path | 5x5 to 8x8; open then closed tours | Not move-based; scored on time and backtracks | 30 |
| 10 | Water Jugs | Water jug problem | State search | 2 or 3 jugs, targets requiring 3 to 20 pours | BFS at generation time | 50 |
| 11 | Crossings | Wolf-goat-cabbage, missionaries and cannibals, jealous couples, bridge and torch | Ferry / state search | Scenario packs with rising party sizes; bridge-and-torch scored on total time | BFS at generation time | 40 |
| 12 | Pancake Flip | Pancake sorting | Prefix reversal | Stacks of 4 to 12 | A\* at generation time | 40 |

Levels are grouped into four tiers per puzzle: Easy, Medium, Hard, Expert. Every level must be verified solvable by the generator or a stored solution before shipping.

Par is an achievable target demonstrated by a stored, verified solution, not a promise of the mathematical minimum. Use a proven optimum when practical; a carefully curated reference solution is acceptable otherwise. Every level records whether its par is proven optimal or a reference target. Only proven optima may be described as "optimal" in player-facing copy. Curated targets must be checked for fairness during playtesting; beating par earns three stars under the same no-hint rule.

Keep all 12 puzzle types. Queens boards above 8x8 are deferred; difficulty should come from the puzzle rather than tiny controls. Larger boards in the other puzzles must meet the compact-phone interaction requirements below. Replace any level that fails those requirements with a smaller playable level in the same puzzle and tier, preserving the launch level counts.

Deferred from the earlier list: Rubik's Cube (3D rendering, large scope), Chinese Rings and Hungarian Rings (obscure, awkward on touch). See Post-launch roadmap.

## Core gameplay (all puzzles)

Every puzzle shares one play screen, one control set and one results flow so a player who learns one learns them all.

- Controls: tap-to-select then tap-to-place, plus drag where natural (sliding, pegs, tiles). Illegal moves are rejected with a short visual and haptic cue, never a dialog.
- HUD: move counter, par (target moves) once the level has been completed once, hints remaining. The elapsed timer is hidden by default and can be shown from Settings; it always runs for scoring.
- Undo and redo: unlimited on free and Pro, but each undo counts toward the move total for scoring.
- Restart: resets the level; timer restarts; no penalty beyond the star calculation.
- Hints: suggest the next move or placement on a verified continuation from the current board; a merely legal move is not sufficient. If no continuation is available, offer a recovery hint identifying an earlier position on a known solution route and the undos needed to return there. Hints never silently reset the board. Free tier: 3 daily hints, plus 2 earned per rewarded ad. Pro: unlimited. Using any hint caps that attempt at 2 stars; it does not guarantee 2 stars. Undoing or reopening the attempt does not clear hint use.
- Solve for me: animates a verified reference solution from the starting board, clearly labelled as a demonstration. Pro-only; awards no stars, points, completion credit, streak credit or achievements. Preserve the player's unfinished attempt for returning to play.
- Tutorial: a 3 to 5 step interactive walkthrough on first open of each puzzle, replayable from the puzzle's info screen.
- Pause: automatic on backgrounding; timer stops; state survives process death.
- Level progression: levels unlock sequentially within a tier; a tier unlocks after 70% of the previous tier is completed with any stars. Pro can skip ahead freely.
- Daily Challenge: one level per local calendar date, selected deterministically from a bundled, versioned schedule shared by all players. For the same date and schedule version, everyone gets the same challenge. Free players have full access to that day's challenge, including Hard/Expert content and Pro-only puzzle variants; this does not unlock the corresponding campaign content or other Pro features. Image challenges use a bundled original image so no personal photo is required. Daily access includes every control needed to play the selected variant.
- Daily scoring: the first completion is scored; further completions are practice unless a free player earns another scored attempt through a rewarded ad. Pro includes unlimited scored retries without ads. Retain only the best daily result for each challenge date. Daily stars and points are recorded separately from campaign totals and never unlock campaign tiers. An unfinished daily may be resumed within the 30-day history window, retaining its original challenge date and attempt eligibility; only today's challenge can receive new rewarded retries.
- Accessibility inside play: every board element has a content description; all interactions work with TalkBack; a reduce-motion setting disables slide animations.
- Audio: original sound effects are a headline feature, not a polish pass. Every interaction has its own sound (pick up, place, slide, illegal move, undo, hint, star earned, level complete, tier unlocked); each puzzle family has a distinct material palette (wood for Hanoi and pegs, plastic for tiles and blocks, metal for Switch Grid, water for jugs); playback latency under 50 ms; haptics paired with sound; separate SFX and music sliders; silent mode respected. Music is optional; if shipped it is original and off by default.

**Compact-phone board interaction**

- Show the entire board for planning. Direct interaction is available only where distinct, non-overlapping targets can each be at least 48 dp; do not shrink targets to fit more cells.
- When the board cannot fit those targets, use a magnified interaction view with a persistent full-board overview. Tapping the overview positions the view without making a move. Actual selection and placement happen on targets of at least 48 dp in the magnified view.
- Provide reachable, labelled pan and zoom buttons so play never depends on pinch gestures or two hands. TalkBack supports row/column descriptions, moving focus and confirming actions without dragging. Return to the same view and selection after resuming a level.
- Verify completion of each affected puzzle on the smallest supported phone layout, with TalkBack and at 200% font scaling. If the interaction is cumbersome or hides information needed to solve the puzzle, replace that board with a smaller variant before release.

## Scoring and progression

Stars are the primary currency of progress; points and streaks give long-term goals. All scoring is deterministic from moves, time and hints, so it can be recomputed and never needs a server.

Evaluate the highest qualifying star tier, then cap it at 2 if any hint was used during the attempt. Every player-completed solution earns at least 1 star. Hints provide help, not an automatic score upgrade.

**Stars per level (move-based puzzles)**

| Stars | Condition |
| --- | --- |
| 3 | Solved at or below par, no hints |
| 2 | Solved within 150% of par, including hinted solves; integer move counts use the threshold rounded down |
| 1 | Solved at any move count |

**Stars per level (Queens, Knight's Tour)**

| Stars | Condition |
| --- | --- |
| 3 | Solved at or under the level's target time, no hints, no backtracks (Knight's Tour) |
| 2 | Solved at or under 2x target time, including hinted solves or Knight's Tour solves with backtracks |
| 1 | Solved |

**Points**: 100 per star, plus a time bonus of up to 50 points per completed attempt. With active play time `t` and the level's positive target time `T`, the bonus is `floor(50 * clamp((3*T - t) / (2*T), 0, 1))`: 50 at or below target time, decreasing linearly to zero at 3x target time. Background or explicitly paused time is excluded.

**Personal bests, not repeat rewards**: keep each campaign level's highest stars and highest single-attempt points. Per-puzzle and overall totals sum those saved bests, never all completions. Replaying for a better result adds only the improvement; an equal or worse result adds nothing and never reduces a best. Best time and best move count are tracked independently. Repeated delivery of the same completion, reloading and backup import must not duplicate rewards. Daily retries likewise replace the best result for that date rather than adding scores together.

**Streaks**: consecutive days with at least one level or the Daily Challenge completed. Streak milestones at 7, 30, 100 and 365 days each unlock an achievement.

**Local day rules**: use the device's local calendar date for the daily challenge, hint allowance, rewarded-ad cap and streak. Days change at local midnight, including daylight-saving transitions; an active level is not interrupted. On first use of a new date, replace the daily hint allowance with 3 (unused daily hints do not accumulate), and reset the rewarded-ad count. Hints earned from ads are a separate saved balance that does not expire; spend daily hints first. Streak credit uses the actual completion date, including replays and resumed older challenges, at most once per date.

Persist dated claims and the latest processed reset date. Revisiting an already processed or earlier date must not issue another allowance, reopen an exhausted ad allowance or create duplicate daily rewards. A timezone change must not erase earned progress. These are local safeguards; v1 has no trusted server clock and does not claim to prevent deliberate device-clock or save-file tampering.

**Achievements** (local, about 40 at launch), for example: first 3-star in each puzzle; all 3 stars in a tier; 7-day streak; solve Hanoi with 10 disks; complete a Sokoban-style level with no undos; finish a closed Knight's Tour on 8x8. Achievements are stored locally; Google Play Games integration is a roadmap item.

**Stats screen**: per puzzle and overall: levels completed, stars earned out of possible, best time per level, average moves over par, total play time, longest streak, achievements unlocked.

## Free vs Pro

Pro is a single one-time purchase through Google Play Billing that removes ads permanently and unlocks all content. There is one SKU at USD 2.99.

| Feature | Free | Pro |
| --- | --- | --- |
| Puzzles | All 12 | All 12 |
| Levels per puzzle | Easy and Medium tiers | All four tiers |
| Daily Challenge | All tiers and puzzle variants for that day's challenge; rewarded scored retries | Same access; unlimited scored retries |
| Ads | Banner, interstitial, rewarded | None |
| Hints | 3 per day, +2 per rewarded ad | Unlimited |
| Solve for me | No | Yes |
| Hanoi 4-peg mode | Only when featured in the Daily Challenge | Yes |
| Sliding Tiles image mode (own photos) | Daily image challenges use bundled art; own photos unavailable | Yes |
| Themes and board skins | 1 default | All (6 at launch) |
| Detailed stats and history | Summary only | Full |
| Backup export and import (file) | No | Yes |
| Skip any level | No | Yes |

**Purchase requirements**

- V1 remains client-only: no app-operated purchase server and no Play Integrity integration. Use Google Play Billing in the app to query purchases, validate returned purchase data and acknowledge the non-consumable Pro purchase. This accepts weaker resistance to client tampering than server verification in exchange for keeping the offline, backend-free scope. See [Google's client-only acknowledgement guidance](https://developer.android.com/google/play/billing/integrate) and [billing security recommendations](https://developer.android.com/google/play/billing/security). Play Integrity verdict verification uses a backend and is excluded from v1 ([Google documentation](https://developer.android.com/google/play/integrity/standard)).
- Reconcile entitlement with Google Play Billing asynchronously on every cold start, reconnection and return to the foreground; cache confirmed entitlement locally. Offline operation or a failed query must not remove cached Pro access or block play.
- Restore purchases: automatic on reinstall or new device with the same Google account; a manual Restore button on the Pro screen.
- Pending and deferred transactions are handled (do not grant until the purchase state is PURCHASED and acknowledged). Persist acknowledgement work for retry after transient failures, and process purchase updates idempotently.
- Refund or revocation removes entitlement when the next successful Billing reconciliation reflects it; progress and stars are never removed. Immediate revocation detection while offline is not promised.
- Promo codes are supported through Play's standard promo-code flow.
- Pro upsell appears on: the paywall for Hard and Expert levels, the ad-close prompt every 5th interstitial, the Settings screen, and one soft prompt after the 10th completed level. No upsell during active play.

## Ads (free tier)

Ads run through Google AdMob with mediation left for later. The rule that governs every placement: ads never cover the board or interrupt an in-progress level.

| Format | Placement | Frequency and caps |
| --- | --- | --- |
| Anchored adaptive banner | Home, level select, results, stats, settings. Never on the play screen. | Always shown on those screens; refresh per AdMob default |
| Interstitial | After a level is completed, on tapping Next or Back from the results screen | Not before the 4th completed level in the app's lifetime; at most 1 per 3 completed levels; at least 120 seconds between interstitials; none in the 60 seconds after app launch |
| Rewarded video | Opt-in only: +2 hints, unlock one Hard or Expert level for 24 hours, or retry a Daily Challenge for stars | Player-initiated; max 10 per day |
| App open ad | Not used in v1 | - |

**Requirements**

- Consent: Google User Messaging Platform for GDPR, UK, and US state privacy regulations; consent gathered before the first ad request; a Privacy Options entry in Settings to change it.
- Ad content rating set to T (teen) or lower; the app is not in the Families program but must not serve mature ad categories.
- Pro entitlement disables all ad SDK initialization, not just ad display.
- Ad load failures fail silently; gameplay and navigation never wait on an ad.
- Interstitials are preloaded after level start so the results screen does not stall.
- app-ads.txt is published on the developer site listed in the Play listing.
- Test ad units are used in all debug builds; production unit IDs only in release builds.

## App structure and screens

&#91;embedded content: screen map · 9 screens\]

Home fans out to four screens; Level select and Daily puzzle both feed the single Play screen, and Results loops back into Play for the next level. Locked tiers, Settings and the periodic upsell all open the Pro screen.

- Navigation: standard Android back stack. Back from Play pauses; it confirms exit only if the level has moves.
- Orientation: portrait on phones; both orientations on tablets (600 dp and wider), with the board scaled to the shorter axis.
- First launch: consent form if required, then a 3-screen onboarding, then Home. Target under 20 seconds from install-open to first move.
- Level select shows per-level stars, best move count and lock state. Stats holds an Achievements tab.
- Every screen has a loading state, an empty state and an error state where relevant (ads, billing).
- Traffic Jam is the lead puzzle: first and largest tile on Home, the tutorial suggested to new players, and the subject of the first three store screenshots.

## Data, persistence and offline

All player data lives on the device. There is no backend, no account and no personal data collected beyond what the ad SDK and crash reporter require and disclose.

- Stored locally: level progress, best stars and best points, best times and move counts, in-progress board state and undo/redo history per level, attempt hint-use flags, streak, achievements, settings, consent state, cached Pro entitlement, separate daily and earned hint balances, and dated reset/reward records.
- Level definitions ship in the APK as data files (JSON or a compact binary), with a stored verified reference solution, a par classification (proven optimal or reference target) where applicable, and a positive scoring target time. The daily schedule is also bundled and versioned; selecting a daily does not require a network request.
- In-progress state is saved on every move so a crash, process kill or phone call never loses a level.
- Android Auto Backup is enabled so progress follows the Google account to a new device without any app-side code.
- Pro: manual backup export to a single file and import from it, with a version field and schema validation on import.
- Purchase tokens and the cached Pro entitlement are excluded from manual exports and Android backup/device transfer. Importing progress never grants Pro; purchases are restored through Google Play Billing.
- Reset progress (per puzzle and global) lives in Settings behind a confirmation.
- Daily puzzle history is retained for 30 days; older entries are pruned.
- Crash reporting only. No analytics SDK and no event tracking; ad fill and purchase conversion are read from the AdMob and Play Console dashboards.

## Production readiness

The app ships only when every row below is met. These are acceptance criteria, not aspirations.

| Area | Requirement |
| --- | --- |
| Android versions | Minimum Android 8.0 (API 26); target the API level Google Play requires at submission time |
| Devices | Phones and tablets, 4.7 in to 13 in; no cutout, notch or foldable layout breakage |
| Performance | 60 fps during play on a 2019 mid-range device; cold start under 2 s; no jank on level select with 80 levels listed |
| App size | Under 40 MB download via Android App Bundle |
| Stability | Crash-free sessions at or above 99.5%; no ANRs in the top-20 device report |
| Battery | No wake locks; timers pause when backgrounded |
| Accessibility | TalkBack usable end to end; 48 dp touch targets; colour-blind safe palettes (no colour-only state); font scaling to 200% without clipping; reduce-motion respected |
| Localization | English at launch; all strings externalized; RTL-safe layouts; number and date formatting via locale |
| Security | Release builds obfuscated; client-side Google Play Billing purchase handling as specified above; no Play Integrity or app backend in v1; no server credentials or private keys in the APK |
| Compliance | Privacy policy hosted and linked in-app and on Play; Data Safety form accurate; UMP consent flow; IARC content rating completed; ads and IAP disclosed in the listing |
| IP | No trademarked puzzle names, logos or level layouts copied from commercial products; all art, sound and level data original or properly licensed |
| Store listing | Name, 30-character short description, full description, feature graphic, 8 screenshots per form factor, a 30-second video is optional |
| Testing | Unit tests for every puzzle engine and solver; automated replay of every shipped reference solution to verify legality, completion and stated par, with an optimality check only for pars labelled proven optimal; scoring tests cover hint caps, best-result totals, daily retries and date rollover; compact-phone board checks as specified above; instrumented tests for billing and consent flows; closed-track beta of at least 20 testers for 14 days before production |
| Release | Staged rollout starting at 10%; monitoring dashboard for crashes, ad fill and purchase conversion; rollback plan |

## Post-launch roadmap

Nothing below is in v1 scope, but v1 architecture must not block it.

1. Additional puzzles, in priority order: Ice Slide (slide until you hit a wall), Rolling Block, Chinese Rings, Rubik's-style cube (3D, largest effort).
2. Google Play Games sign-in for cloud save, leaderboards and achievements sync. Progress data model must be serializable to a single blob from day one.
3. iOS release from the shared puzzle engines (Kotlin Multiplatform is the intended path).
4. Ad mediation once daily active users justify it.
5. Level packs as additional one-time purchases, and a weekly challenge alongside the daily.
6. Level editor and sharing via codes (no server needed).

## Assumptions and open items

The assumptions below were made to keep the doc complete; each one is cheap to change now and expensive after level data and store assets exist.

**Assumed**

- AdMob is the ad network; Google Play Billing for the one Pro SKU.
- Free tier gets the Easy and Medium tiers of every puzzle rather than a fixed number of levels. This puts roughly half of all content behind Pro.
- Kotlin with Jetpack Compose; puzzle engines as pure Kotlin modules with no Android imports.
- Levels are generated or curated offline and shipped as data, not generated on device, so every par is achievable and every level has a verified solution.
- Stats and Achievements share one screen with tabs.
- The app is rated for 13+ and not enrolled in the Play Families program.

**Decided**

- Name: Parlor in-app; the Play listing title is Parlor Puzzles (see Name below).
- Audience: women 25 to 50 playing in short breaks between family and household demands.
- Visual direction: Cozy parlor (see below).
- Pro price: USD 2.99, one SKU.
- Hints: rewarded-ad currency as specified (3 per day free, +2 per rewarded ad, unlimited on Pro).
- Audio: original throughout; sound effects are a headline requirement (see Core gameplay). Music optional.
- Lead puzzle: Traffic Jam on Home and in store screenshots.
- Analytics: none. Crash reporting only.
- Backend: none in v1, including purchase verification; client-side Google Play Billing with cached offline entitlement. Play Integrity is excluded.
- Challenge and scoring: achievable par targets, skill-based progression, hints cap an attempt at 2 stars, and totals count personal bests rather than repeated completions.
- Daily access: free players can receive every tier and puzzle variant through the daily challenge; campaign access remains as specified in Free vs Pro.
- Day boundaries: local midnight, with saved dated claims to prevent duplicate resets and rewards.
- Phone usability: all 12 puzzle types remain; Queens is capped at 8x8, and dense boards use a magnified interaction view or are replaced with smaller variants if they fail usability checks.

**Open**

- [ ] Trademark clearance for "Parlor Puzzles" (USPTO and EUIPO, classes 9 and 41), Play title availability, and the parlorpuzzles.com domain. Fallback name if blocked: Knack.
- [ ] Commission icon, palette and type samples in the Cozy parlor direction and approve them before any store asset is produced.

**Name**

The in-app brand is Parlor; the store title is Parlor Puzzles. A search found an existing social chat app named Parlor that has been on Google Play and the App Store for over ten years and briefly reached the number-two free download spot in January 2021 when users confused it with Parler ([source](https://tecnoblog.net/400648/parlor-lidera-downloads-apos-parler-ser-banido-por-google-e-apple/)). The bare name is therefore not usable as a store title and would split search traffic; the two-word title also puts the category keyword in the name, which helps Play search.

Tagline for the listing: "Twelve classic puzzles, one quiet room."

**Visual identity: Cozy parlor**

| Element | Specification |
| --- | --- |
| Mood | Calm, warm, tactile: a quiet living room, not an arcade. Opening the app should feel like a small treat in a two-minute break. |
| Palette | Warm linen and cream backgrounds; piece colours sage, terracotta, dusty rose, mustard and slate blue; terracotta as the single call-to-action accent; dark theme is warm charcoal, never pure black |
| Shapes | Rounded corners throughout, soft shadows, pieces with subtle material cues (wood grain, felt, ceramic) that match the SFX material palettes |
| Type | One friendly humanist sans with rounded terminals, three weights, licensed for app embedding; body text 16 sp minimum |
| Motion | Gentle easing, 200 to 300 ms; the completion moment is a soft chime plus palette-coloured confetti; no screen shake, no flashing |
| Home | Puzzles shown as objects on shelves and side tables in a stylized room, Traffic Jam front and centre; a plain tile grid is the accessible fallback layout |
| Icon | Original mark: a rounded tile sliding out of a 2x2 grid, terracotta on linen, no text |
| Accessibility | Every colour pair passes 4.5:1 contrast and deuteranopia and protanopia checks; piece colour is never the only state signal |

Avoid:

- Neon or high-saturation gamer palettes, dark mode as the default, HUD clutter and currency counters on Home.
- Red countdown timers, energy meters, lives, or guilt messaging when a streak lapses.
- Cartoon mascots and characters; the pieces are the characters.
