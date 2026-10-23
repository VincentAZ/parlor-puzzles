# Crash reporting

Crashlytics reports fatal errors, supported Android ANRs and its own session data.
The app adds no Analytics SDK, gameplay events, custom logs, user IDs, custom keys,
nonfatal reports or photo attachments. The SDK's session data supports crash-free
metrics without Analytics; see [Firebase's metric definitions](https://firebase.google.com/docs/crashlytics/crash-free-metrics).
Crash-free sessions and crash-free users are different measures. The release
requirement concerns sessions over the first 14 production days.

## Build isolation

- `debug`, `benchmark` and `storeTest` do not initialize Firebase. Their manifest
  collection flags are false, Google Services processing is disabled and mapping
  upload is disabled. Ordinary development and purchase testing need no Firebase
  account or configuration.
- `crashTest` is a separate, debug-signed, minified application:
  `com.parlor.puzzles.crashtest`. It uses the sample catalog and AdMob demo units.
  Its manual test screen and deliberate crash are absent from other variants.
- `release` uses `com.parlor.puzzles`, reporting enabled and mapping upload enabled.
  Its existing full-catalog, production-ad and Billing configuration gates remain.

Firebase's automatic initialization provider is removed. `ParlorApplication`
starts it explicitly only in the two reporting variants. SDK initialization
failure does not prevent offline play. A started SDK does not prove delivery.
Crashlytics uploads and session metrics need a valid project and working network.

The build pins Firebase BoM 34.19.0, Crashlytics SDK 20.1.1, Crashlytics Gradle
plugin 3.0.8 and Google Services plugin 4.5.0, with verified dependency hashes.
`:app:verifyNoAnalytics` rejects Firebase Analytics and Google measurement SDKs
in every application variant and runs before app builds. Dependency updates still
require review of the complete resolved tree and merged manifest.
Firebase's measurement-connector and config-interop interfaces are transitive
dependencies; the Analytics, Play Services measurement and Remote Config SDK
implementations are absent. The app calls no connector/event APIs.

## Owner configuration

Register each Android package in the owner's Firebase project. Use an internal
test app (preferably a separate test project) for qualification, with Analytics
disabled. Download each Android app's actual `google-services.json` to:

- Internal: `app/src/crashTest/google-services.json`
- Production: `app/src/release/google-services.json`

These files are ignored by Git. No service-account private key belongs in the app.
Only these exact paths are consumed: a root-level or other variant's JSON cannot
configure either app. Build checks require exactly one matching package registration,
valid-looking Android app/project/API-key fields and consistent project numbers.
Those checks validate structure, not ownership, key restrictions or service availability.
Do not substitute a made-up project to bypass the live qualification gate.

```sh
./gradlew :app:verifyCrashTestCrashReporting :app:assembleCrashTest
adb install -r app/build/outputs/apk/crashTest/app-crashTest.apk
adb shell am start -n com.parlor.puzzles.crashtest/com.parlor.puzzles.CrashTestActivity
```

Assembly runs the configured Crashlytics mapping upload for this minified variant.
Keep the resulting `app/build/outputs/mapping/crashTest/mapping.txt`, APK, source
hash, build ID and mapping-task log together. Release uses its own mapping and
Firebase app. Source/line attributes are retained for readable stack traces.

## Live qualification

1. On the named physical test device, install the exact internal APK without an
   attached debugger. Open the test screen and confirm the reporter started.
2. Tap **Create test crash** once, then reopen the screen using the command above.
   It should report a crash in the previous execution. Reopening permits upload;
   a local previous-crash flag alone does not establish successful delivery.
3. In the configured Firebase console, verify that exact internal app ID and
   version, the deliberate exception, and a readable `CrashTestActivity` stack
   frame/line after mapping upload. Record the console issue link, UTC times,
   device/API, APK/source hashes and screenshot or exported report.
4. Exercise normal play, background/resume, offline restart and later reconnection.
   Inspect device/network behavior and ensure only the approved SDK endpoints are
   used. Confirm persisted SDK work does not introduce an app-held wake lock.
   Verify production packaging and mapping separately when its release gates pass.
5. Update the privacy policy and Play Data Safety disclosures for the actual SDK
   data/settings. Crashlytics reports and installation/session identifiers are
   outside the app's explicit Android backup/transfer allowlist and portable
   progress export. Personal puzzle image files are never attached to reports.

Follow [Firebase's Android setup and test-crash procedure](https://firebase.google.com/docs/crashlytics/android/get-started)
and [deobfuscation guidance](https://firebase.google.com/docs/crashlytics/android/get-deobfuscated-reports).
Do not add the optional Analytics dependency or breadcrumb collection from sample
instructions. The internal crash button requires a tap and is never invoked on
ordinary launch or included in production.

## Qualification status

The local evidence packet records 543 passing
regression executions, eleven configuration checks, debug/minified installed
play/resume checks, and zero lint errors in debug and the internal variant. Four
Google demo-ad executions also pass, with four Firebase-isolation repeats after
real ad SDK startup. These checks use disabled reporting and injected ad consent;
they do not establish live Crashlytics delivery or real regional consent.

No owner Firebase configuration has been supplied; no SDK upload, mapping delivery, console report, physical ANR
check or production crash-free rate is claimed. These remain explicit service and
release gates. Local checks cannot substitute for the configured test crash and
14-day production observation.
