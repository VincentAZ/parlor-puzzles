# Adaptive play, tutorials and demonstrations

Play now uses the available window dimensions to arrange the board and controls.
In landscape windows at least 840 dp wide, the header stays above two independent
scroll areas: the board on the left and instructions, counters and shared actions
on the right. Reading a hint or scrolling the actions does not move the board.
The board width is bounded by the board pane's shorter dimension; unusually short
windows scroll instead of shrinking the existing interaction targets.

Portrait and narrower windows use a centered, scrollable column, capped at 720 dp.
The existing phone order remains header, instructions/counters, board and actions.
Loading, unavailable and results states also use this column. The results banner
remains outside its scroll area. Active play has no banner.

The same layout serves all twelve tutorials and the read-only demonstration.
Traffic Jam places its full-board overview beside the magnified interaction when
at least 384 dp is available; narrower boards keep the vertical arrangement.
Its labelled pan/zoom controls remain available. The layout does not change rules,
move costs, score calculation, hints, rewards or access checks.

The fixed-queen underline is a separate decoration drawn above the label. Its
state marker remains visible at 200% text instead of being covered by the label's
background; the TalkBack description continues to identify a fixed queen.

The existing Activity orientation policy allows both orientations for devices
with a smallest width of at least 600 dp and requests portrait on phones. A
landscape window narrower than the two-pane threshold falls back to the single
column. Rotation and Activity recreation restore the durable board, history,
selection, zoom/pan position, hint flag and active time through the existing
attempt repository. Demonstration viewport persistence is unchanged and is not
covered by that claim.

Separating folds now use the geometry-aware behavior in [Fold layouts](FOLD_LAYOUTS.md).

## Automated coverage

Three added instrumentation cases pass on API 26 and API 36. The display rule uses
native Android size/density/font settings, with a 960 × 600-dp landscape display
and a 600 × 960-dp portrait display; it restores the prior settings afterward.

- A 200% text fixture renders one Expert board from each of the twelve families,
  checks a representative target of at least 48 × 48 dp, and verifies that scrolling
  the control pane leaves that target in place. It exercises a 600-dp window
  fallback, retained viewport state and a results footer separated from actions.
  A rendered-pixel assertion checks that the fixed-queen underline is continuous
  at 200% text, excluding the cell border from the sampled region.
- An actual Activity flow makes a Traffic Jam move, selects and pans a magnified
  board, changes orientation twice and recreates the Activity. It verifies retained
  board/history/view/hint state and nondecreasing active time, then uses undo, redo,
  restart and the eight-move reference solution to reach three-star results with
  one additional completion receipt.
- A 200% text fixture completes the interactive Queens tutorial and advances a
  read-only Traffic Jam demonstration while preserving player rewards/history.

The all-family fixture is a layout check, not twelve completed tablet solutions.
The native Activity case deliberately restarts its test attempt and earns one
completion; it restores settings but does not erase the earned result. No purchase
or rewarded ad is used. Exact reports and captures are in
the evidence packet.

API 26's UI Automation screenshot may return null after resizing the emulated
display. The capture helper then uses native `screencap`; its unmodified images
include black space around the logical landscape display on the portrait emulator
surface. API 36 captures the logical display directly. Test assertions use actual
Compose bounds and native font configuration, not screenshot pixels as dp.

## Remaining qualification

Complete every puzzle and tutorial with human TalkBack on physical tablets in
both orientations, including 200% text, large piece sets and magnified boards.
Check focus order/restoration, persistent planning information, system dialogs,
split-screen resizing, cutouts, hinges and fold/unfold transitions. Extremely
short windows and dense-board controls still require observed usability checks.
These emulator results do not certify physical tablet, foldable or performance
requirements. The Traffic Jam product gate and full 556-level catalog remain open.
