<div align="center">
  <img src="docs/screenshots/parlor-icon.svg" alt="Parlor Puzzles icon" width="112" height="112">
  <h1>Parlor Puzzles</h1>
  <p><strong>A little room to think.</strong> Twelve classic logic puzzles in one calm Android app: settle into a board, stay for a moment, keep your progress on your own device.</p>
  <p>Kotlin · Jetpack Compose · Room · Android 8.0+ · Phones, tablets and foldables</p>

  ![Kotlin 2.2](https://img.shields.io/badge/kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
  ![Jetpack Compose](https://img.shields.io/badge/ui-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
  ![Min SDK 26](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
  ![Target SDK 36](https://img.shields.io/badge/targetSdk-36-3DDC84?logo=android&logoColor=white)
  ![No backend](https://img.shields.io/badge/backend-none-brightgreen)
  ![Status: development prototype](https://img.shields.io/badge/status-development%20prototype-f59e0b)
  ![MIT license](https://img.shields.io/badge/license-MIT-blue)
</div>

Parlor Puzzles gathers Traffic Jam, Tower of Hanoi, Switch Grid, Water Jugs, Pancake Flip, Sliding Tiles, Block Escape, Box Pusher, Peg Solitaire, Queens, Knight's Tour and Crossings into one warm, unhurried interface. Every level ships with a verified solution, every move is saved the moment you make it, and nothing about your play leaves the phone.

**This is a development prototype, not a release.** Development builds contain 56 verified sample levels across all twelve families; the full launch catalog is 556 levels. Google Play purchasing, production ads, crash reporting and physical-device qualification are documented but not yet certified. See [Implementation status](docs/IMPLEMENTATION_STATUS.md) and [Qualification](docs/QUALIFICATION.md) for exactly where things stand.

**Explore:** [Screenshots](#screenshots) · [The puzzles](#the-puzzles) · [Features](#what-it-does) · [Build](#build-and-run) · [Architecture](#architecture) · [Content and verification](#content-and-verification) · [Privacy](#privacy-and-local-data) · [Documentation](#documentation) · [Contributing](#development-and-contributions)

## Screenshots

Captured from the development build on Android emulators with fresh, empty saves.

**Home** — a tabletop for Traffic Jam, illustrated shelves for the other eleven collections, and today's Daily Challenge. Six room palettes, each with light and dark appearance.

<table>
<tr>
<td><img src="docs/screenshots/home-room.png" alt="Parlor home screen: 'A little room to think', a Traffic Jam side table and the Daily Challenge button" width="300"></td>
<td><img src="docs/screenshots/home-shelves-light.png" alt="Home shelves in the light Linen palette showing puzzle collections with illustrated boards" width="300"></td>
<td><img src="docs/screenshots/home-shelves-dark.png" alt="Home shelves in a dark palette showing Box Pusher, Peg Solitaire, Queens and Knight's Tour" width="300"></td>
</tr>
</table>

**Boards** — code-drawn skins, large touch targets, a magnifier for small pieces, and the same physical layout under right-to-left locales.

<table>
<tr>
<td><img src="docs/screenshots/board-traffic-jam-linen.png" alt="Traffic Jam board in the Linen palette with lettered sliding pieces and the red A piece" width="300"></td>
<td><img src="docs/screenshots/board-queens-walnut.png" alt="Queens board in the dark Walnut palette with one fixed underlined queen" width="300"></td>
<td><img src="docs/screenshots/board-pegs-walnut.png" alt="Peg Solitaire board in the dark Walnut palette" width="300"></td>
</tr>
</table>

**Tablets and foldables** — in wide windows the board sits beside its instructions and controls; on hinged devices the two panes avoid the fold.

<img src="docs/screenshots/tablet-play-landscape.png" alt="Traffic Jam on a landscape tablet with the board on the left and instructions, move count, hints, undo, redo and restart on the right" width="760">

## The puzzles

| Family | Goal | Scored by |
| --- | --- | --- |
| **Traffic Jam** | Slide the red piece through the exit | Moves |
| **Tower of Hanoi** | Move the whole stack, never a larger disc on a smaller one (three- and four-peg boards) | Moves |
| **Switch Grid** | Turn every light on when each switch flips its neighbours | Moves |
| **Water Jugs** | Measure an exact amount with jugs of fixed sizes | Moves |
| **Pancake Flip** | Sort the stack using only flips from the top | Moves |
| **Sliding Tiles** | Settle the picture into place (3×3 to 5×5, with your own photos in Pro) | Moves |
| **Block Escape** | Clear a path until the large square can leave the board | Moves |
| **Box Pusher** | Walk every box onto a target without trapping one | Pushes |
| **Peg Solitaire** | Jump pegs until one remains | Moves |
| **Queens** | Place every queen with none attacking another, around fixed queens | Time |
| **Knight's Tour** | Visit every square with L-shaped steps | Time and backtracks |
| **Crossings** | Ferry everyone across under the river rules (couples, missionaries, bridges) | Moves, or minutes for the bridge |

Each family has its own interactive tutorial and a worked demonstration. Undo and redo are always available and count against your score; hints come from the real solver, so they are never wrong.

## What it does

- **Play without an account.** No sign-in, no server. Progress, personal bests, attempt history and settings live in a local Room database and survive force-stop, rotation and reinstall over the same package.
- **Save every move.** Each accepted move, score counter and completion reward commits through one database transaction before the animation finishes. Leaving mid-level and coming back resumes exactly where you were.
- **Daily Challenge.** One scheduled level a day drawn from the bundled schedule, with its own history, streaks and a scored retry.
- **Stars, points and 40 achievements.** Deterministic scoring with per-level bests, difficulty tiers that unlock as you go, and a full attempt history with puzzle and play filters.
- **Six room palettes.** Linen is free; Amber, Rose, Sage, Slate and Walnut come with Pro. Light, dark or follow-the-device, applied across all twelve boards.
- **Adaptive layout.** Compact phones, 200% system text, landscape tablets, hinge-aware foldables with book and tabletop postures, display cutouts and right-to-left locales (English, Arabic and German are exercised in tests).
- **Gentle motion.** 250 ms board transitions, verified walking paths for Box Pusher, immediate rendering under reduced motion, and a single soft celebration when you set a new result.
- **Original sound.** Short synthesized effects and haptics, with separate sound and haptic switches. No third-party audio samples.
- **Optional reminders.** Opt-in Daily or streak reminders at a chosen local time, suppressed once the day's puzzle is done.
- **Pro, once.** A one-time purchase through Google Play Billing unlocks themes, photo tiles, manual backup files and the full attempt detail. Pending or unverified purchases never grant Pro; confirmed ownership survives offline queries.
- **Ads only when eligible.** Banners, a Results interstitial and explicit rewarded offers (two hints, a 24-hour Hard or Expert level, or a scored Daily retry) run behind confirmed Free status and consent. A separate `adTest` build previews Google's demo units with no account setup.
- **Recovery screens.** Unreadable saves, future database versions and damaged settings open a recovery flow that preserves the original files and reward records instead of silently resetting.

## Build and run

### Requirements

- JDK **17**
- Android SDK **platform 36**, build tools **35.0.0** and platform tools
- Android 8.0 (API 26) or newer on the device or emulator
- Python 3 for the content generators and device smoke scripts (optional)

Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 and all dependencies are pinned in `gradle/libs.versions.toml`. The wrapper distribution carries a SHA-256 check and Maven artifacts are verified against `gradle/verification-metadata.xml`, so update that file deliberately when adding a dependency.

### Build the development APK

Clone, point Gradle at your SDK (set `ANDROID_HOME`, or write `sdk.dir=/path/to/sdk` to an untracked `local.properties`), and build:

```sh
git clone https://github.com/VincentAZ/parlor-puzzles.git
cd parlor-puzzles
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio with JDK 17, pick the `app` module's `debug` variant and run it. The debug package is `com.parlor.puzzles.debug`, so it coexists with other variants. No account credentials, API keys or service files are needed for an ordinary debug build.

Other variants: `adTest` (demo ads, no purchases), `storeTest` (real Play Billing against a configured Play Console listing), `crashTest` (an isolated minified build with a manual crash screen) and `benchmark`. Each is described in [Local testing](docs/LOCAL_TESTING.md), [Ad setup](docs/ADS_SETUP.md), [Billing setup](docs/BILLING_SETUP.md) and [Crash reporting](docs/CRASH_REPORTING.md).

### Run the tests

```sh
# Portable JVM tests plus the platform-import boundary check.
./gradlew :core:model:test :core:game:test :core:progress:test :core:data:testDebugUnitTest :core:platform:testDebugUnitTest :core:design:testDebugUnitTest verifyPortableBoundaries

# Replay every bundled level and check each optimality claim.
./gradlew :tools:levels:verifyPrototype

# Lint and build.
./gradlew :app:lintDebug :app:assembleDebug

# Instrumented storage and UI tests on a connected device or emulator.
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-parallel
```

Instrumented suites are run on API 26 and API 36 emulators. The latest full matrix passes 593 executions across 365 distinct cases with no failures or skips.

## Architecture

Eleven Gradle modules, split so that the puzzle rules never touch Android:

| Module | Responsibility |
| --- | --- |
| `core:model` | Portable serialized levels, attempts, settings and progress contracts |
| `core:game` | Pure rules for all twelve families, reversible sessions, bounded search and verified hints |
| `core:progress` | Deterministic stars and points, personal bests, tier gates, achievements, local-date and reward rules |
| `core:data` | Room database with v1 to v4 migrations, durable daily and campaign transactions, private photos, backups and recovery |
| `core:design` | Six room palettes, light and dark colors, code-drawn board skins and fold-aware layout helpers |
| `core:platform` | Sound and haptics, Play Billing, UMP consent, gated AdMob, rewards, reminders and gated Crashlytics |
| `feature:play` | Shared board and results screens, twelve renderers, tutorials and demonstrations |
| `feature:library` | Onboarding, Home, level lists, Daily history, stats, achievements, settings, backups and Pro |
| `app` | Android composition root, navigation and build variants |
| `tools:levels` | Offline catalog generation and the independent optimality verifier |
| `benchmark` | Minified startup and frame-measurement scenarios |

A Gradle check rejects Android and Java platform imports in the portable modules. UI state is always derived from durable storage rather than held in memory, which is what makes force-stop, recreation and fold changes uneventful. Design notes behind these choices are in [Decisions](docs/DECISIONS.md).

## Content and verification

Every committed board is original, has a complete solution and carries a provenance record ([Asset provenance](docs/ASSET_PROVENANCE.md)). To regenerate the catalog and sound effects from their fixed seeds:

```sh
python3 tools/levels/generate_traffic.py
./gradlew :tools:levels:generateWaveA :tools:levels:generateWaveB :tools:levels:generateWaveC
python3 tools/generate_sfx.py
./gradlew :tools:levels:verifyPrototype
```

Generation is offline authoring tooling and can take a few minutes. The Kotlin verifier checks legality, goal, cost and each optimality claim; the solver-backed families are cross-checked with independent exhaustive or breadth-first searches in engine tests. For a deterministic regeneration check, run `./gradlew :tools:levels:installDist` followed by `python3 tools/check_content_regeneration.py`.

The `tools/` scripts also drive emulator smoke runs (`device_smoke.py`, `wave_a_device_smoke.py`, `wave_b_device_smoke.py`, `wave_c_device_smoke.py`) and record test reports with hashes (`record_verification.py`). They write to `docs/evidence/`, which is ignored by Git so logs, captures and reports stay on the machine that produced them.

## Privacy and local data

Parlor has no backend, no analytics SDK and no account. What it keeps, it keeps on the device:

- **Progress and settings** in the app's private Room database and preferences. Android auto-backup is allowlisted to progress and preferences only.
- **Purchase state** in device-only, no-backup storage. Purchase tokens, entitlement and consent choices never enter the database, exports or backups.
- **Photos** chosen for Sliding Tiles are copied privately, cropped square and deleted from the app on request. Themes never recolor them.
- **Manual backups** (Pro) are written through the system file picker to a location you choose, validated on import and never uploaded.
- **Crash reporting** is compiled only into the `crashTest` and `release` variants and needs the owner's own Firebase configuration; debug, benchmark and purchase-test builds never initialize Firebase.
- **Ads and consent** go through Google's UMP and AdMob SDKs only after Free status and consent are confirmed. The debug build keeps ads suppressed.

Signing keys, purchase tokens and service credentials stay outside Git. `local.properties`, `google-services.json`, `signing.properties` and keystores are ignored.

## Documentation

- [Local testing](docs/LOCAL_TESTING.md), [Qualification](docs/QUALIFICATION.md), [Implementation status](docs/IMPLEMENTATION_STATUS.md)
- [Screen map](docs/SCREEN_MAP.md), [Home](docs/HOME.md), [Level selection](docs/LEVEL_SELECTION.md), [Progress](docs/PROGRESS.md), [Settings](docs/SETTINGS.md)
- [Themes](docs/THEMES.md), [Motion](docs/MOTION.md), [Fold layouts](docs/FOLD_LAYOUTS.md), [Tablet play](docs/TABLET_PLAY.md), [Locale layouts](docs/LOCALE_LAYOUTS.md)
- [Photos](docs/PHOTOS.md), [Backups](docs/BACKUPS.md), [Storage recovery](docs/STORAGE_RECOVERY.md), [Reminders](docs/REMINDERS.md)
- [Ad setup](docs/ADS_SETUP.md), [Billing setup](docs/BILLING_SETUP.md), [Crash reporting](docs/CRASH_REPORTING.md)
- [Decisions](docs/DECISIONS.md), [Asset provenance](docs/ASSET_PROVENANCE.md), the original [requirements](requirements/) and [third-party licenses](docs/licenses/)

## Development and contributions

Bug reports and focused pull requests are welcome. For a useful report, include the device and Android version, the puzzle and level, the steps, what you expected and what happened; keep the original save if you can. Redact anything personal from logs and screenshots before posting them in [GitHub issues](https://github.com/VincentAZ/parlor-puzzles/issues).

Before opening a pull request, run the JVM tests and the level verifier above. Changes to the portable modules must pass `verifyPortableBoundaries`; changes to dependencies must update `gradle/verification-metadata.xml` in the same commit. Content changes must regenerate deterministically and keep every optimality claim verifiable.

## Acknowledgements

- [Nunito](https://fonts.google.com/specimen/Nunito), under the SIL Open Font License (`docs/licenses/Nunito-OFL.txt`).
- Jetpack Compose, Room, Play Billing, Google Mobile Ads, User Messaging Platform and Firebase Crashlytics, pinned in the version catalog.

## License

Parlor Puzzles is licensed under the [MIT License](LICENSE). Nunito is distributed under its own SIL Open Font License (`docs/licenses/Nunito-OFL.txt`).
