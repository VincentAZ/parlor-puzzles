> **Historical record:** this document predates the [fully free edition](FREE_EDITION.md). Pro, ads, billing and reward limits described below no longer apply.

# Level selection

All four difficulty groups remain in one lazy list. Cards have stable level IDs;
only visible and nearby rows are composed. Back and Difficulty stay reachable
above the list, and banner space stays below it. The difficulty dialog scrolls
when necessary and jumps to a group without changing campaign access.

List position survives Activity recreation and return from the Pro screen.
Tutorial replay remains available before the level groups. Empty groups explain
that no levels are available. Headings, labelled stars, difficulty-qualified
level descriptions and access states are exposed to accessibility services.

Each card shows earned stars, its saved best cost or time, and its current access
state independently. Losing Pro or temporary access does not hide an earned
best. Moves, pushes, crossing minutes and timed puzzles use their existing units.
The number and star layout allows large text without forcing overlapping columns.

Selecting a card rechecks access using the current clock. An expired 24-hour
grant cannot use a stale displayed availability state to open the board. Visible
expiry state refreshes while the screen is started and on return to it; checks
stop when it leaves the started lifecycle. The repository remains the authority
for actually starting or resuming play.

The native API 26/36 tests use an 80-card layout fixture, all five scoring units,
Pro revocation, temporary expiry, progression gates, empty groups, recreation,
RTL and 200% text. The repeated fixture boards exist only in instrumentation:
the shipped catalog still contains 56 verified samples, including 12 Traffic Jam
levels. Lazy composition and passing navigation tests do not prove a physical
2019 phone's frame-rate or the eventual catalog's usability.

See level-selection evidence. Human TalkBack,
physical scroll/frame measurements with the completed catalog, wider tablet/fold
coverage and localized-content qualification remain open.
