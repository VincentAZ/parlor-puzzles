# Home

Home presents Traffic Jam on a side table with a window, lamp and plant. The
other eleven collections sit on shelves as original code-drawn puzzle objects.
All twelve keep native text and touch controls; illustrations are decorative and
add no focus stops. The existing six light/dark palette sets supply the colours.
This is app artwork, not an approved store identity packet.

The Simple Home setting replaces the room with plain cards. Both layouts keep
the same collection routes, Daily Challenge, Progress, and campaign completion
counts. Traffic Jam stays first and largest. Shelves/cards adapt to one, two or
three columns from available width and font scale; a compact phone at 200% text
uses one full-width column. Text wraps without fixed card heights. The existing
resume control remains before the puzzle collection and routes to the saved
campaign board or original Daily date.

Overall and per-puzzle counts use unique saved campaign best IDs that exist in
the current catalog. A repeated completion, a better result for the same level,
a Daily result or an unknown old content ID cannot add a solved campaign level.
All tiers are included, independently of current entitlement. Resetting progress
updates the counts. Numbers use the current locale. Text chooses its reading direction from its
content, keeping English fallback sentences and numbers in order within an RTL
layout. Text accompanies each bar;
no information depends on colour, and Home introduces no points, hint balances
or streak counters.

Home has its own saved scroll position, separate from Settings/Daily scrolling.
Returning from a collection and recreating the Activity retain the visible
shelf. Switching to Simple Home retains the setting across recreation. Banner
space remains outside the scroll area under the existing placement rules.

## Evidence and limits

`HomeRoomTest` covers both layouts, all twelve callbacks, native 320 × 640 dp at
200% font scaling, RTL in the simple layout, 48-dp targets, text layout bounds,
reactive campaign counts, duplicate/unknown best IDs and clearing progress.
`HomeNavigationTest` exercises the actual Activity, saved layout setting,
collection navigation, return position and recreation without changing progress.
Its captures show every room object, plain cards and dark-mode shelves.
Both pass on API 26 and API 36. See the evidence packet.

The catalog still contains 56 development levels. The UI uses the actual bundled
count; it does not imply that the 556-level release ladder is complete. Human
TalkBack, physical device/large-tablet/fold qualification and final identity
approval remain open.
