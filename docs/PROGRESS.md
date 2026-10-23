# Progress and attempt history

The Progress screen keeps Summary and Achievements available to every player.
Best results and History require Pro. Losing Pro hides those details immediately
without deleting any saved progress. The Pro screen returns to the selected tab;
tabs, filters and history page positions survive Activity recreation.

## Summary and best results

Summary shows campaign completions, stars out of the available catalog, points,
longest streak, total play time and earned achievements. It also shows per-puzzle
campaign totals and play time, and Daily totals separately. Averages compare each
completed level's best cost with its par, grouped by moves, pushes, scenario
minutes or active-time targets. Different scoring units are never averaged together.

Pro Best results lists completed campaign levels and filters by puzzle. It shows
saved stars, points, time and the applicable move/push/minute cost, with its target
and first-completion date. These bests are independent: the fastest completion
need not be the one with the most stars or points. The screen explains that they
can come from different attempts. Browsing never awards or recomputes saved totals.

## History

History reads one page of 20 completed attempts at a time. A single additional
row determines whether an earlier page exists; its attempt JSON is not decoded.
The main app no longer subscribes to the unbounded attempt history on launch.
The page flow runs only in a visible Pro History tab while its lifecycle is at
least started. Leaving the tab, backgrounding or losing Pro cancels collection.
A free request is rejected before opening Room, with entitlement checked again
before a page is exposed. These are local capability controls, not encryption.

Players can filter by puzzle and campaign/Daily play. Earlier and more recent
buttons page through the results without loading all saved boards. Ordering uses
the saved update time and a stable attempt-ID tie break. Page cursors retain both
values, so a newer completion does not shift an older page or duplicate a tied
record. This is local wall-clock ordering; deliberate clock changes are not a
trusted chronology. Changing either filter returns to its newest page. A reset
or import can leave an older page empty; More recent attempts remains available.

Each card shows the puzzle/tier/level, actual local completion date, campaign or
original Daily challenge date, attempt stars/points, active time, relevant scored
cost/backtracks, target, undos, hint use and box-pushing walking steps. Practice
completions explicitly award no stars or points. The introductory copy distinguishes
an attempt result from an improvement to the saved best; equal/worse replays do
not add to totals. A missing legacy completion date is labelled unavailable.

A Daily result uses the level definition saved when that challenge opened, even
if the current catalog has a newer revision or target. If a campaign revision is
no longer available, its retained time and counters are kept, but no result is
invented using a different revision. The UI explains the unavailable details.
Daily attempts follow the existing 30-day retention/pruning rules; campaign
history is retained until the player resets the corresponding progress.

Malformed page data produces a storage failure, never an empty-history success.
Transient failures offer retry. Changing a filter cancels the old request so a
late response cannot replace the selected page. No migration, backup-schema or
reward-rule change is needed: the views read existing attempt, completion and
saved Daily rows. Photo payloads are not loaded by history.

## Presentation and qualification

All four tabs use lazy lists. Tab buttons, filter selections and page controls
have a 48-dp minimum; labels and cards grow with text. The filter dialog scrolls,
and Back remains outside the list. Dates and numbers use the device locale.
Banner placement remains the allowed Stats surface, outside the scrolling list.

The new fixtures cover equal-time pagination, insertions between pages, filters,
practice/demo separation, saved Daily targets, actual completion dates, reopen,
backup import, unreadable older rows, reset, Pro loss and no mutation of bests.
Native UI fixtures exercise 320 x 640 dp with the device font scale set to 200%,
right-to-left layout, loading/retry/cancellation, filtering/paging, saved state,
background collection and all five scoring units. Real Activity navigation covers
return from Pro and recreation; ownership in Pro-rendering fixtures is injected.

The evidence packet is progress-history.
Emulator coverage does not establish human TalkBack usability, physical frame
performance or live Play entitlement. Those remain release qualification work.
