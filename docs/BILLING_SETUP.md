# Google Play Billing setup and qualification

The app now contains a real Billing adapter and Pro purchase/restore screen. It
uses one non-consumable product, signed purchase validation, durable acknowledgement
retry, automatic reconciliation and cached offline entitlement. No Play product or
licensing key has been supplied in this workspace. Normal development builds show
an unavailable purchase state and remain playable. There is no production fake
purchase path. The 56-level sample catalog is unchanged.

## Configure the product

In the owner's Play Console app for `com.parlor.puzzles`, configure one active
one-time product with a single permanent buy option, a USD 2.99 base price and
the intended regions. The default product ID in code is `parlor_pro`; override it
if the configured product has another ID. Do not configure a rental, preorder,
multi-quantity offer or subscription for this entitlement.

Put these Gradle properties in the build environment or the developer's private
Gradle user properties, outside this repository:

```properties
parlor.proProductId=parlor_pro
parlor.playPublicKey=BASE64_APP_LICENSING_PUBLIC_KEY_FROM_PLAY_CONSOLE
```

The licensing **public** key is deliberately embedded to validate receipts. It is
not an upload/signing private key, service account credential or Play Developer API
credential. Release signing remains a separate, unfinished release setup.

Billing is pinned to 9.1.0 in the version catalog with dependency checksums.
Recheck library submission deadlines at release time. See the
[official Billing release notes](https://developer.android.com/google/play/billing/release-notes).

## Build for license testing

```sh
./gradlew :app:assembleStoreTest
```

The output is `app/build/outputs/apk/storeTest/app-storeTest.apk`. This debuggable,
debug-signed variant uses `com.parlor.puzzles` and the sample catalog. Use a
dedicated test device/profile: it shares the production package name. Normal
debug and benchmark variants retain `.debug` and `.benchmark` suffixes. Neither
`storeTest` nor `release` builds without a parseable RSA licensing public key.
Release also requires all 556 verified levels; `storeTest` does not bypass that
release gate or represent a release candidate.

Google allows license testers to sideload a debug-signed app whose package matches
the Play app. Configure the device's purchasing account as a license tester and
confirm the checkout identifies a test purchase before using its test payment
method. Ordinary test-track users can incur real charges. Full release
qualification must also cover an actual Play-distributed build.
[Google's Billing testing guidance](https://developer.android.com/google/play/billing/test).

No app has been uploaded, test purchase launched or owner account configured by
this implementation. A synthetically keyed local build, when recorded in evidence,
only proves build configuration; it cannot validate a real Play purchase.

## Implemented behavior

- Home waits only for local entitlement loading, never for Billing's network.
  Cold starts, foreground returns and network availability request reconciliation.
  Unresolved ownership does not permit ad initialization. Ads/UMP are not yet
  integrated; the entitlement gate is ready for them.
- Checkout uses Play's localized price and offer token. Details refresh before
  launch; a changed price requires another tap. One permanent offer is supported.
- The client verifies the RSA signature, package, product, quantity, token and
  purchase state. A pending purchase never grants Pro. A paid purchase grants only
  after acknowledgement and a durable entitlement write. Duplicate callbacks are
  serialized by token; an empty partial callback cannot revoke ownership.
- Signed receipts and locally confirmed acknowledgements live in an `AtomicFile`
  under Android's `noBackupFilesDir`. They are separate from Room progress, manual
  exports, Android backup and device transfer. Malformed/future/oversized caches
  remain unentitled until a valid reconciliation repairs them.
- A persisted, network-constrained `JobScheduler` job queues acknowledgement
  recovery before receipt writes/network calls. Foreground and network retries
  remain available when the platform cannot schedule a job. No app wake lock is
  acquired. Scheduler timing and retry after a physical reboot still need device
  qualification.
- Offline/query failures preserve confirmed Pro. A successful full ownership
  query can remove it. Revocation preserves scores and saved boards; a currently
  authorized foreground board may finish, while later restricted entry is gated.
  A device-only history of the latest 8,192 revoked token hashes prevents delayed
  callbacks from restoring removed purchases; a later full query remains authoritative.
- Restore queries Play directly. Reinstallation/device transfer relies on Play,
  never an imported entitlement. Redeem opens Play's standard redemption page;
  returning to the app reconciles purchases.

Client-only acknowledgement follows the
[Billing integration API](https://developer.android.com/google/play/billing/integrate).
This project explicitly accepts weaker tamper resistance than server verification;
it adds no backend or Play Integrity. See
[Google's security guidance](https://developer.android.com/google/play/billing/security).
Synthetic RSA fixtures exercise the verifier, but only configured Play transactions
can establish receipt-format/signature interoperability.

## Required live evidence

Record device, OS, build/source hashes, Play product configuration, test account
class and observed state transitions. Do not archive raw purchase tokens or account
identifiers in the repository. Required cases are:

1. Localized offer, normal approval, cancellation and decline; one permanent
   entitlement with no repeat purchase while owned.
2. Delayed approval/decline, backgrounding, process death before/after payment and
   acknowledgement, network loss, retry and reboot. Confirm no pending grant.
3. Manual restore, reinstall and second-device restore using the same account.
   Confirm an offline restart retains previously confirmed Pro.
4. Refund/revocation followed by successful reconciliation; retain progress and
   the authorized foreground attempt while removing future restricted access.
5. Promo redemption, stale/changed offer, unavailable product/store, and normal
   play during provider failure.
6. After Ads/UMP integration: restored/cached Pro never initializes ads; upgrading
   destroys current ads and prevents further requests. Exercise free consent
   states and all reward/placement limits separately.

Automated synthetic lifecycle tests and the read-only, unconfigured SDK smoke are
local evidence only. The live cases, test-track delivery, production verification
and P8/P9 observation gates remain open.

The Billing dependency brings Google transport/Play services libraries. The merged
manifest is checked for permissions and startup components; no Analytics/Ads SDK
is intentionally added here. Final network inspection and Data Safety review must
include Billing's actual SDK behavior, not assume an absence of SDK telemetry.
