# Local testing checkpoint

This is version `0.1.0-dev`, package `com.parlor.puzzles.debug`, for development
and device testing. It contains all twelve puzzle families and 56 verified sample
levels. It is not the complete 556-level release catalog. Google Play purchasing,
production ads/crash reporting and the external release gates are not configured
or certified by this checkpoint.

## Install the APK

The handoff copy is `dist/Parlor-Puzzles-0.1.0-dev.apk`; its SHA-256 is in
`dist/SHA256SUMS`. On Android, open that APK and allow installation from the app
used to open it when prompted. With an attached, authorized Android device:

```sh
adb install -r dist/Parlor-Puzzles-0.1.0-dev.apk
```

Android 8.0 / API 26 or newer is required. Installing with `-r` preserves the
existing debug package's local progress. If another build with the same package
was signed by a different key, Android rejects the update; keep its data until
you decide whether to replace it. The release and benchmark packages are separate.

## Build or run locally

Open this repository in Android Studio, use JDK 17, let Gradle sync, select the
`app` module's `debug` variant and run it on a device or emulator. The SDK needs
platform 36 and build tools 35.0.0. `local.properties` points to the SDK on this
workspace; another machine needs its own SDK path.

From this workspace:

```sh
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleDebug
```

The rebuilt APK is `app/build/outputs/apk/debug/app-debug.apk`. Rebuilding does
not refresh the handoff copy in `dist` automatically. See the repository README
for the portable tests and connected instrumentation commands.

For an account-free ad preview, build `:app:assembleAdTest` and install
`app/build/outputs/apk/adTest/app-adTest.apk`. It appears as **Parlor Ad Test**,
with separate saves and Google demo ads enabled. See [Ad setup](ADS_SETUP.md)
for placements, rewards and interstitial frequency. The ordinary debug APK
keeps ads suppressed until real Billing and consent establish eligibility.
The local preview handoff is `dist/Parlor-Puzzles-0.1.0-ad-test.apk`, with its
checksum in the adjacent `.apk.sha256` file. Rebuilds do not refresh that copy.

## Suggested first pass

- Finish onboarding and the Traffic Jam tutorial, then play the first level.
  Try illegal moves, undo/redo, pause, restart and save-and-leave.
- Close and reopen the app with an unfinished level, then finish it and return to
  its level list. Check saved stars, moves and resume behavior.
- Open each collection's tutorial and first free level, and try today's Daily.
- Try sound/haptic controls, reduced motion, dark appearance and large system text.
  On a tablet or foldable, rotate/fold during a saved level and while a dialog is open.

Pro features remain gated by real ownership; this build has no fake purchase or
reward switch. The Pro screen explains unavailable purchasing when store setup
is absent. For a useful bug report, include device/Android version, the level,
steps, expected behavior and what happened; keep the original save when possible.
