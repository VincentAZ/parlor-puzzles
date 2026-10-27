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
