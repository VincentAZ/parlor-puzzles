# Shared navigation contract

The Android back stack holds Library, campaign Play, dated Daily Play, tutorials
and Pro demonstrations/purchase screen. Library retains its own Home → destination history and
scroll position while a board is open. Android Back returns to the prior entry;
leaving an unfinished board with moves opens a paused save-and-leave confirmation.

## Implemented

- First launch: three onboarding pages → Home, with asynchronous UMP consent
  once Free is established. A required form waits for a safe surface if the player
  has already entered a puzzle or tutorial. Full first-launch ordering remains
  a service/device qualification item; local play never waits for ownership lookup.
- Home: latest unfinished campaign or daily board in one tap; Traffic Jam lead
  card; Daily Challenge; Progress; eleven additional family cards; Settings. A simple layout omits decorative art.
- Level select: all four tiers, stars, best moves, progression/access explanation,
  tutorial replay → campaign Play. Current content has 56 sample boards in twelve
  families. Each family has its own 3–4-step first-open tutorial and replay.
  A locked Hard/Expert level offers Pro or an explicit ad for that level's 24-hour
  access; earning it does not unlock any other campaign level.
- Daily entry: today's saved assignment and scoring/practice state → dated Play;
  opened challenges from the last 30 days → their saved board or another attempt.
  After today's first scored completion, Free players can explicitly earn a
  scored retry. A separate Start scored retry action replaces an unfinished
  practice board; earning alone preserves it. Earlier dates have no new ad offer.
- Play: select/move, undo/redo, hint/recovery, pause/restart, viewport, saved timer
  and exit. Campaign and daily share controls, with a daily date/eligibility label.
  Box Pusher labels scored pushes separately from walking; image Tiles has number
  labels and a complete-picture preview, including for free Daily players.
  Campaign Tiles offers Pro photo selection → square-crop preview → explicit save,
  plus return to default artwork. Selection preserves moves and scores. Pro loss
  retains the photo and uses numbers; Daily keeps its bundled art.
  Queens uses active-time scoring; Knight’s Tour also tracks undo backtracks.
  Crossings separates scored bridge minutes from the board’s travelled minutes.
  Free players can open an offer for two earned hints. The board is checkpointed
  and paused before the offer; an earned callback changes only the hint balance.
- Results: stars/points for scored completions, explicit practice results otherwise;
  restart/retry, next available campaign board, or return to the prior screen.
  Next/Back can present a ready interstitial within lifetime/time caps. A missed
  ad never queues a later interruption. Periodic Pro prompts stay on Results.
- Pro demonstration: verified reference playback/manual steps, then return to the
  preserved board. No result, timer, streak or achievement credit is generated.
- Progress: campaign summary, separate daily bests, per-puzzle statistics,
  all 40 achievements, Pro attempt-history gate.
- Settings: timer, reduced motion, haptics, effect volume, unavailable optional music,
  simple Home, Themes and appearance, Pro purchase entry, UMP Privacy options and
  per-puzzle/global resets. Progress backups opens the Pro export/import screen.
- Themes and appearance: six room/board skins, explicit Light/Dark/device appearance,
  Free Linen and Pro selection. Failed writes allow retry; loss of Pro retains the
  chosen room while displaying Linen. Locked rooms open Pro and preserve Back.
- Reminders: off by default; one Daily or streak reminder at a chosen local time.
  Enable requests Android notification permission when needed; denial keeps it off.
  Blocked permission or failed storage exposes Android notification settings.
  Completion/foreground/date checks suppress unnecessary delivery. A notification
  opens today's Daily after onboarding and preserves the ordinary Back stack.
- Saved photos: explains local copies and backup capacity; confirmed deletion removes
  current/history, recovery and staged copies while preserving progress. This privacy
  action remains available without Pro.
- Backups: system create/open document → validated preview → explicit replacement.
  A previous-progress recovery copy remains available even after Pro is lost.
  Import saves that copy and progress atomically, then finishes settings before
  allowing play. A failed settings write opens a persistent retry screen.
- Pro: localized price, purchase, manual restore and Play redemption; loading,
  pending, acknowledgement, failure, cancelled and cached-offline states. Settings
  and campaign tier gates navigate here; Back preserves the originating screen.
- Banners: separate bottom slots on Home, level select, Results, Progress and
  Settings, gated by known Free and consent. No banners on Play or Daily entry.
- Reward offers: separate Watch an ad action, cancellable loading, completed-view
  cap, no-fill/cancel/earned states and saving recovery. Home/Settings expose a
  retry if an earned receipt needs recovery after a restart. Pro hides new offers.

## Remaining service and content integration

- Configured Play license-test and test-track qualification of purchase/restore.
- Real regional UMP form/withdrawal qualification and production ad configuration.
- Physical theme/photo/reminder qualification and wider provider/automatic-backup coverage.
- Storage/schema recovery screen beyond the current retry/error presentation.

Service navigation must not block Home or an available puzzle. Daily access cannot
become campaign ownership, and Pro prompts cannot interrupt active play. Returning
from a blocking surface resumes only the saved board; background time is excluded.
