# Free edition validation — October 27, 2026

The free edition removes advertising and payments from the application, rather than waiting for a purchase or an ad reward to unlock features.

## Completed checks

- `:core:model:test`, `:core:game:test`, `:core:progress:test`: **67 tests, no failures or errors**. Includes all difficulty/variant access without ownership, unlimited hints with exhausted legacy balances, scored Daily retries, all six palettes, scoring and backup compatibility.
- `:tools:levels:verifyPrototype`: **56 bundled levels verified** across all twelve puzzle families.
- `:app:assembleDebug`: **passed**, producing an Android APK.
- `:app:testDebugUnitTest`: no app-specific JVM test sources; core tests above provide the JVM coverage.
- `:app:compileDebugAndroidTestKotlin`, `:core:data:compileDebugAndroidTestKotlin`: **passed**. Retained storage/UI tests were adapted for the free policy; obsolete ad/billing SDK tests were retired.
- Runtime dependency guard: **passed** for every configured application build type, rejecting ad, billing, UMP and analytics/measurement SDK dependencies.
- Built APK DEX inspection: **no** BillingClient, Google ads, mobile ads or UMP package classes.
- Merged application manifest: **no** Play Billing or advertising-ID permission.
- README local links and image paths: all resolve.
- Current files and historical text blobs: no matches for the checked common API-token/private-key patterns, and no tracked signing/Firebase credential files. This is a focused pattern check, not an exhaustive security audit.

Removing monetization changes transitive annotation dependency selection. The app constrains `error_prone_annotations` to 2.26.1, a version already present in the repository's strict checksum metadata. Dependency verification remains enabled and no SDK checksum exceptions were added.

## Not performed

Instrumentation tests were compiled, not executed on an Android device or emulator. Physical-device, accessibility and store release qualification are still required. No signing secrets, Play Console settings, Firebase project configuration or published store release were changed. The planned 556-level production catalog remains unfinished.

## November 5, 2026 follow-up

- Re-ran model, game, and progress JVM tests after removing remaining monetization
  text: **67 tests passed**, zero failures or errors.
- Re-ran `:tools:levels:verifyPrototype`: **56 levels passed**.
- Installed local Java 17 and Android SDK tooling; no Android device was connected.
- Created a new local upload key for the first release and configured release
  signing through ignored `signing.properties`. No private signing material is
  tracked. `:app:verifyReleaseSigning` passed.
- The production catalog verifier still fails: `Release requires 80 TRAFFIC_JAM
  levels`. The full 556-level catalog remains incomplete.
- The owner selected the **Plan 9 from Outer Space** Play developer account.
  Searching its app list found no Parlor listing. No listing, store pricing,
  Firebase project, or published release has been created or changed in this
  follow-up.

## Approved 56-puzzle first release

The owner subsequently approved shipping the existing 56 puzzles and deferring
its former 556-puzzle launch plan. The release verifier now checks the approved
per-family and per-tier counts while retaining solution replay, canonical
uniqueness, provenance, scoring and frozen Daily-reference checks. Puzzle IDs,
revisions and Daily epochs have not changed.

- Model/game/progress: **68 JVM tests passed**, zero failures or errors, including
  reachable achievement targets and complete-tier awards for this release.
- `:tools:levels:verifyCatalog` and `:tools:levels:verifyPrototype`: **passed**.
- Debug APK assembly and Android test compilation: **passed**. This is build
  evidence, not yet a device-test result.
- Production Firebase project `parlor-puzzles` and Android registration
  `com.parlor.puzzles` were created on the Spark plan without Analytics or Gemini.
  The owner downloaded the configuration, which is kept in the ignored
  `app/src/release/google-services.json` path.
- Signed release bundle assembly, release signing validation, release lint,
  no-ad/no-billing dependency audit and Crashlytics mapping upload: **passed**.
  This does not yet prove crash-report delivery from a device or Play publication.
- Release lint resolved an older annotation dependency in two library modules;
  those modules now use the same already-checksummed 2.26.1 annotation constraint
  as the application. Checksum verification remains enabled.

Store copy and vector-derived icon/feature graphic are in
`fastlane/metadata/android/en-US`. An offline privacy screen and corresponding
`docs/privacy.html` describe actual local storage and crash diagnostics, with the
owner-provided public support address. Store publication, device qualification
and Play review remain pending.

### API 36 emulator evidence

On the isolated `emulator-5556` device, `FreeEditionTest` passed: all six themes
were selected and persisted through recreation, the offline privacy screen opened,
and Billing/advertising-ID/AdServices permissions were absent. The gameplay smoke
script also passed onboarding, interactive tutorial steps, one committed move,
and force-stop/resume persistence. These are emulator results, not physical-device
or human accessibility qualification. The published privacy page was fetched and
compared byte-for-byte with `docs/privacy.html`.
