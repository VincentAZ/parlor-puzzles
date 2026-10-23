# AdMob and consent setup

Parlor integrates GMA Next-Gen 1.5.0 and UMP 4.0.0. Banner, completed-Results
interstitial and explicitly requested rewarded adapters are connected. Production
reward buttons use the real SDK. Synthetic eligibility is confined to automated
tests and the separate `adTest` preview described below.

## Account-free ad preview

Build the separate **Parlor Ad Test** app to try the placements without Play or
AdMob account configuration:

```sh
./gradlew :app:assembleAdTest
adb install -r app/build/outputs/apk/adTest/app-adTest.apk
```

Select `adTest` in Android Studio. Its package is `com.parlor.puzzles.adtest`,
so it has its own progress and can coexist with the regular development app.
This variant uses Google's real demo banner, interstitial and rewarded units.
Its application supplies preview-only Free and consent states to the ad adapter;
it does not run UMP forms, enable purchases, grant Pro, or initialize Crashlytics.
The preview adapter is compiled only into `adTest` and rejects other ad IDs.
Ordinary debug, storeTest, benchmark and release keep real ownership/consent gates.

Finish onboarding and the tutorial, then return Home to see a banner. Banners
also appear on level select, Results, Stats and Settings, and never cover Play.
Use a hint reward offer and choose **Watch an ad** to try rewarded ads. Rewards
are saved only after the real SDK earned callback. Results interstitials keep
the normal completion and time limits described below: first eligible after
four completions and 60 seconds since launch, then the usual cooldowns.
Network availability and demo fill still determine whether an ad appears; no
empty banner space is reserved on failure. This build does not qualify real
consent, purchases, or production ad serving.

## Configure the release

Create an Android app and the three ad formats in the owner's AdMob account.
Put these **public identifiers** in the user's Gradle properties outside Git:

```properties
parlor.admob.app=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
parlor.admob.banner=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
parlor.admob.interstitial=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
parlor.admob.rewarded=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
```

`:app:verifyReleaseAds` rejects missing IDs and Google's sample publisher.
Debug, adTest, storeTest and benchmark always override these properties with Google's
demo app/unit IDs. The storeTest variant is for Billing qualification and does
not serve production ads. Production catalog and Billing configuration gates
remain independent requirements for release.

In AdMob Privacy & messaging, configure GDPR/UK and applicable US state messages
for the actual application. The manifest app ID and the SDK's initialization ID
come from the same build configuration. Configure the developer website and
publish the account's exact app-ads.txt entry before production; neither is
created by the local integration. Update the actual privacy policy and Play Data
Safety disclosures for the selected SDKs and account settings.

## Runtime behavior

- Constructing the coordinator makes no GMA call. Initialization happens on an IO
  dispatcher only after Billing establishes Free, UMP permits requests, and the
  activity is foregrounded. Pending, acknowledging, opening-checkout, unknown and
  Pro ownership all suppress initialization and ad requests. Local play does not
  wait for the Billing or consent network response.
- UMP refreshes once per Free application session. Its current `canRequestAds()`
  result is authoritative; Parlor does not copy a consent string into progress.
  Update/load failures and ten-second deadlines leave normal navigation usable.
  A required form is loaded separately and shown only on a safe surface. If the
  player has already started a puzzle or tutorial, presentation waits until they
  leave it. The first-launch ordering and under-20-second physical first move
  remain qualification items, including slow/offline service responses.
- Settings exposes Privacy options. An explicit request can refresh and reopen
  required choices after a Pro upgrade, without permitting ad initialization.
  Forms are not presented over Play, tutorials, demonstrations or the Pro screen.
- Standard anchored adaptive banners occupy a separate bottom slot on Home,
  level select, Results, Stats and Settings. They are absent from active Play,
  Daily entry, onboarding, tutorials, demonstrations and Pro. A failed load leaves
  no empty reserved area; SDK refresh applies to a visible banner.
- Interstitial preloading begins after an unfinished level starts. Only Next or
  Back from completed Results can consume a ready ad. A transactional departure
  claim prevents repeat presentation and later interruptions from missed loads.
  A missing ad or failed local claim continues navigation; local claim work has a
  250-ms ceiling and never waits for an ad network response.
- Frequency uses permanent completion records: first eligible at four lifetime
  completions, at least three completions since the previous reserved show,
  at least 120 seconds after the previous interstitial closes, and no interstitial in the first 60
  seconds of launch. Times use `elapsedRealtime` and a boot marker combining the
  boot counter with a random installation ID excluded from backup. Restored
  progress cannot mistake another device's uptime for the original clock. A reboot or
  unavailable boot counter conservatively requires 120 seconds after launch.
  A durable unsettled reservation also enforces that new-launch interval if the
  process dies during the ad or the close write fails. An in-process cooldown
  independently covers failed close writes while the app remains running.
  Failed shows may consume a frequency opportunity, reducing ad frequency.
- Only an actually shown ad's close callback increments the dismissal count.
  Every fifth dismissal can offer Pro before proceeding from Results; one soft
  prompt is claimed after ten completions. Claims and frequency records survive
  reset and database reopen. Existing Hard/Expert and Settings Pro routes remain.
- Ownership changes invalidate pending ad callbacks, dispose banners/loaded ads
  and stop preloaders. A fullscreen ad's own activity pause does not cancel that
  already-presented ad. Future Pro launches never initialize GMA. The SDK offers
  no way to undo an initialization or network request already in flight when a
  later ownership update arrives; subsequent requests are gated.

## Explicit rewards and recovery

The player can choose two earned hints from Play, one Hard/Expert level for
24 hours from its paywall, or another scored retry from today's Daily entry.
Each offer explains the reward before its separate **Watch an ad** action.
Opening an offer makes no ad request. Pro hides reward offers in Play/Daily and
the shared ownership/consent gate rejects SDK loads and presentation after an
upgrade. All three rewards share ten completed views per local date.

Play checkpoints the current attempt and pauses its timer before opening a hint
offer. A pause invalidates queued input; an already accepted transaction may
finish, then the saved board is retained. Dismissing the offer resumes play.
Loading is cancelable and expires after 20 seconds. Local eligibility checks have
a two-second deadline and run before loading and again before presentation.
Timeouts, load/show failures, canceled offers and early closes grant nothing.
A canceled request cannot present an advertisement when a late load returns.

Only `onUserEarnedReward` creates an `EarnedReward` receipt. Its local UUID, chosen
reward, offered date, callback date and callback wall time are captured once.
Delivery first writes a bounded, versioned AtomicFile in `noBackupFilesDir`, then
applies reward, completed-view count and permanent claim in one Room transaction,
then clears the private receipt. A restart between Room commit and file clearing
replays the same ID without another grant. Temporary access expires 24 hours from
earning, not from a delayed recovery. An earned reward remains owed after Pro.

Malformed, oversized or unsupported recovery files fail closed. A failed first
file write keeps the callback in memory and asks the player to retry before
closing the app; a durable pending receipt retries on foreground return and
through the visible recovery action. Other local play remains available, while
another rewarded view waits for recovery. A client-only app cannot recover a
process death before the earned callback has reached durable storage, or an SDK
event never delivered to it; no server-side verification is introduced.

Dated completed-view counts preserve earlier days when a pending receipt is
recovered after a later reset. Older preview ledgers migrate their saved daily
count before advancing the reset date. Resetting progress retains reward claims,
dated counts and balances. Receipt files are excluded from backup; committed
progress and anti-duplication records stay in the progress database.

For the midnight boundary, the implementation assumes the Daily target freezes
when the ad is presented: a video started for that day's challenge may finish
after midnight and still earns that promised retry. Its view counts against the
actual completion date. A request that becomes a past-date offer while loading
does not show, and new offers for past challenges are unavailable. This assumption
was presented for owner steering to resolve an unspecified edge; the requirement
files remain unchanged.
The earned screen's explicit **Start scored retry** action replaces an unfinished
practice board with a fresh scored attempt; merely earning a retry preserves the
practice board, and repeating Start preserves an already active scored attempt.

## Background work and permissions

The selected SDK depends on WorkManager 2.7.0 and can queue an offline ping worker.
The app removes WorkManager's automatic startup initializer and provides a custom
worker factory. WorkManager is reserved for ad work; Billing uses native
JobScheduler. The custom configuration is installed before GMA initializes. A
persisted worker encountered on a Pro, unresolved or otherwise ineligible cold
start is replaced with a no-op worker before constructing the SDK worker.

On API 26+, ordinary workers use the platform JobScheduler backend. Parlor removes
the unused alarm/foreground services and their WAKE_LOCK/FOREGROUND_SERVICE
permissions. RECEIVE_BOOT_COMPLETED is retained for persisted Billing retries.
The merged manifest also contains INTERNET, ACCESS_NETWORK_STATE, AD_ID and the
SDK's READ_BASIC_PHONE_STATE permission. Re-audit SDK manifests and worker paths
on upgrades; a future foreground worker must not silently bypass this design.
Manifest checks and emulator runs do not establish physical battery performance
or exhaustively exercise SDK offline reporting after reboot.

UMP storage and the WorkManager database are excluded by the existing Android
backup allowlist. Only progress and player preferences transfer. No Analytics SDK
or application event tracking is added.

## Reproduce qualification

The deterministic suite uses injected consent/ad interfaces and synthetic local
records. Android startup tests also assert `MobileAds.isInitialized == false`,
exercise the gated worker factory, and inspect actual merged permissions.

```sh
./gradlew :core:model:test :core:game:test :core:progress:test :core:platform:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

`AdNetworkQualification` cases are excluded from that suite. Run them separately
with Google demo units in a fresh instrumentation process:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.parlor.puzzles.AdsSdkQualificationTest,com.parlor.puzzles.RewardSdkQualificationTest \
  com.parlor.puzzles.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The banner/interstitial fixture injects known Free and consent permission, loads a real
Google demo banner, presents and closes a preloaded interstitial, then verifies
Pro disposal and removal of the preloader configuration. Android's first-use
fullscreen tip is dismissed separately. The pinned demo's X is tapped at its
observed position when the WebView does not expose an accessibility node. The
advertisement's destination is never opened. That case does not exercise rewards.
Neither fixture establishes live UMP geography/forms, real Play entitlement or
production fill.
`RewardSdkQualificationTest` additionally watches a real Google rewarded demo,
checks the actual earned callback and persisted +2 hints, verifies unchanged board
and active time, and checks that Pro removes the next reward offer/request. It
waits for the demo's visible close control after the callback; the callback can
precede the end-card animation. No purchase is performed. The screenshot labelled `Ad layout fixture` is a
Compose placement/large-text test, not a served advertisement.

The API 26 emulator uses Chrome WebView 69 and visibly crops the demo interstitial
image; its close remains available. API 36's WebView 133 renders the portrait
creative fully. The old WebView is a possible cause, not a confirmed diagnosis.
Updated-WebView and physical-device visual checks remain open, as does human
TalkBack through SDK-owned surfaces. The callback smoke is not visual acceptance.

Before release, observe real UMP forms and withdrawal in configured GDPR/UK/US
test geographies; fresh/returning/expired consent; cold cached/restored Pro;
pending purchase during loads; all Results boundaries and fullscreen lifecycle;
offline/timeout/no-fill recovery; background/reboot offline reporting; actual
reward-earned versus close callbacks, shared daily caps and midnight; and every
allowed/forbidden placement on the physical device matrix.

## Primary references

- [GMA Next-Gen setup](https://developers.google.com/admob/android/next-gen/quick-start)
- [SDK release notes](https://developers.google.com/admob/android/next-gen/rel-notes)
- [Google demo ad units](https://developers.google.com/admob/android/next-gen/test-ads)
- [UMP privacy integration](https://developers.google.com/admob/android/privacy)
- [Adaptive banners](https://developers.google.com/admob/android/next-gen/banner)
- [Interstitials and preloading](https://developers.google.com/admob/android/next-gen/interstitial)
- [Single-request rewarded ads](https://developers.google.com/admob/android/next-gen/rewarded/single-load)
- [Custom WorkManager configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)
