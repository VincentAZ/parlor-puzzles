# Free edition — October 27, 2026

All existing features are free in debug and release: every bundled puzzle and difficulty, six palettes, custom photo tiles, manual backup/restore, damaged-database recovery, attempt details, demonstrations, unlimited hints, and unlimited scored Daily retries.

The app does not initialize an ad, consent, or billing SDK. Their dependencies, Android adapters, ad test build, purchase screens, banner placements, interstitials, rewarded offers, and upgrade navigation are removed. No Play key, product ID, or AdMob configuration is required.

## Existing saves

Keep the application ID and signing identity when updating an installed copy. Room tables and serialized IDs remain compatible. Older fields such as `proVariant`, `DailyEligibility.PRO`, earned credits and ad-history records remain readable so an update does not discard saves or backups. They no longer grant or restrict current feature access. The legacy `PRO` Daily value means an unlimited scored retry in saved data, available to everyone.

Several internal function signatures retain a legacy `pro` argument for existing tests and callers. The current access, theme, hint and Daily policies ignore it: even `false`, an exhausted balance, an expired temporary unlock or a missing Play installation cannot lock a feature. No purchase data is needed to restore progress.

Normal puzzle rules remain: hints affect scoring, photo storage is bounded, history retains its documented window, and reference solutions and save validation still apply.

## Release readiness

The repository contains 56 verified prototype levels across twelve puzzle families. The earlier planned 556-level launch catalog is unfinished. Release still requires catalog completion, physical-device/accessibility qualification, signing, and the existing configured crash reporter. These are unrelated to payment; do not bypass safety or content checks to claim a production release.

Source changes do not change Google Play pricing or publish an update. Before a store launch, choose free distribution, no ads, and no in-app products; align the listing, privacy policy and Data safety answers with the final signed binary. Run upgrade tests without clearing app data.

### Release signing

The release build reads the Git-ignored root `signing.properties` file with
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword` properties. `storeFile`
may be an absolute path to a privately stored upload keystore. Keep the keystore
and passwords outside version control, restrict access to their files, and back
them up securely. Debug and benchmark builds continue to use debug signing.
Release builds fail when signing configuration is absent; the Android signing
task validates the key and passwords when producing the bundle.

After catalog and Firebase configuration checks pass, build with
`./gradlew :app:bundleRelease`. Upload the signed bundle from
`app/build/outputs/bundle/release/app-release.aab` to the Parlor app in Play Console.
Creating a local upload key does not enroll the app in Play App Signing or publish
a release; those steps still happen in Play Console.

## Verification

Run JVM tests for model/game/progress, the prototype verifier, app unit tests and a debug build. On a device, run the retained instrumentation tests and verify all themes, photos, history, backup/restore, repeated hints and scored Daily retries. `FreeAccessTest` covers exhausted legacy allowances and formerly paid tiers; `FreeEditionTest` covers app navigation and absence of billing/advertising permissions.

Earlier documents describing Pro purchases, ad consent or reward limits are historical design records. This file and the root README define the current product behavior.

Local results are recorded in [Free edition validation](FREE_EDITION_VALIDATION.md).
