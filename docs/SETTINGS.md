> **Historical record:** this document predates the [fully free edition](FREE_EDITION.md). Pro, ads, billing and reward limits described below no longer apply.

# Settings accessibility and progress reset

Sound effects has a labelled, adjustable slider. Music has a separately labelled,
disabled slider whose accessibility state explains that no music is included.
Both slider handles and their control bounds are at least 48 dp high. Sound
volume changes persist through DataStore without changing the other preferences.
Silent-mode playback behavior remains in the existing audio adapter.

Per-puzzle and global progress reset require confirmation. The dialog keeps its
title and actions outside a scrollable explanation, so all retention rules remain
reachable on a native 320 × 640 dp display at 200% text. Cancel and Android Back
dismiss without calling reset. The selected scope survives saved-state recreation.

While reset is running, both actions are disabled and Back cannot dismiss it.
Repeated confirmation is guarded before launching the work. Failure returns the
explanation to its top and announces the error; retry clears the previous error
and keeps the same scope. A successful reset closes the dialog.

The existing Room transaction clears only the selected saved boards, campaign
bests, daily results and play time. Hints, temporary access, earned retries,
dated claims, completion receipts, streaks and achievements remain. Reset does
not renew a Daily scoring allowance. Purchase ownership and privacy state live
outside this progress transaction.

The Settings evidence covers native
API 26 and API 36 fixtures, an injected SQL failure with rollback, scoped/global
reset, duplicate reward protection, persisted sound volume and RTL semantics.
Destructive UI checks use an isolated Room database. Installed debug and minified
smoke checks only open and cancel the confirmation.

Human TalkBack task completion, physical devices, actual audio output latency,
silent-mode listening and wider tablet/locale qualification remain open. Automated
semantics actions do not establish human TalkBack usability.
