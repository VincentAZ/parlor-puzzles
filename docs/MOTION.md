# Board motion and completion

Board presentation uses 140 ms eased transitions. The engine and repository still
commit a complete move, its counters and its reversible history in one transaction
before the renderer receives the new board. Animation values are display state;
they do not change the saved position, scoring, hints or rewards.

Traffic Jam, Sliding Tiles, Block Escape, Hanoi and Crossings interpolate piece
positions. Sliding tiles cross row boundaries in one drawing plane, and all cells
of a block follow the same position. The planning overviews for Traffic Jam,
Tiles, Blocks and Box Pusher follow the same display coordinates as the interaction
view. Photo crops remain attached to tile identities. Jugs interpolate their fill
levels; pancakes move to their new stack positions. Switch Grid colors, pegs and
chess markers use short visual transitions. Descriptions and counters always
describe the committed state.

Box Pusher walking follows the engine's verified floor path around corners.
An interrupted undo follows the known corridor in reverse or rejoins the next
known path; it does not interpolate a diagonal through walls. Pushed boxes retain
their visual identities. Unrelated snapshots that cannot establish a single move
snap to the committed state.

Traffic Jam, Sliding Tiles, Block Escape and Hanoi accept input during settling.
Dragging uses immediate presentation coordinates, with one committed move on
release. Sliding pieces stay within their unobstructed legal range; cancellation
and pause discard the preview. Legal destinations are highlighted during a drag
(including peg jumps), using only immediate movement rules, never solver results
or reference solutions. Queens, Knight and other deduction controls gain no new
answer-revealing highlights. Box Pusher and Crossings retain their movement lock
while their automatic paths finish. The shared Undo/Redo controls can interrupt movement; the display then
tracks the latest committed board. Existing spatial coordinate conventions remain
unchanged in RTL interfaces. Restart and a newly opened attempt initialize at
their saved positions without replaying an earlier move.

## Motion preference and lifecycle

Reduce motion applies to all twelve renderers, tutorials and demonstrations.
Enabling it during a transition finishes that transition immediately; subsequent
board changes render directly. It also removes the completion delay and confetti.
Demonstrations start paused with this preference; explicit stepping or Play still
shows the verified sequence without sliding transitions.

The Play screen settles motion when paused, backgrounded or covered by its blocking
dialogs, consent/reward surface or photo editor. Opening a saved board starts at
the committed position. Completion celebration state is not a saved reward and is
not replayed after Activity recreation. Compose animation specifications also use
Android's duration scale; the native frame tests specifically cover the app's
reduce-motion preference, not a physical-device system-setting audit.

## Completion presentation

A completed attempt is already durable while its final board transition finishes.
The board is disabled during this interval, and the ad placement remains Play.
Results and their banner become eligible only after the transition. The results
callback also waits for this change, so the existing tenth-completion prompt is
checked at the proper placement.

A newly completed scored attempt plays the existing completion/star cues and a
single 300 ms fall of small, palette-colored confetti above the stars. There is no
screen shake, flashing, loop or additional reward transaction. Practice results
do not create a scored celebration. Completed attempts reopened from storage show
Results directly. Backgrounding or enabling reduced motion cancels the visual
celebration; returning does not replay it.

## Evidence and remaining qualification

The original native frame tests on API 26 and API 36 exercised actual engine moves, intermediate
rendered bounds, the former animation input lock, all twelve families' changed pixels, bundled-image
tile identity, a joined block, corridor walking with an interrupted undo, and a
live reduced-motion change. Actual Activity tests cover durable completion before
the final visual position, ad placement, the one-time celebration, reduced-motion
results and recreation during an ordinary move with retained history/selection.
A separate Play-screen test checks results-callback ordering.

The live-drag update adds `LiveDragTest` for movement before release, continuous
pointer tracking, obstacle clamping, joined blocks, legal highlights, cancellation,
pause and exit gestures beyond the board. The updated motion tests expect sliding
boards to remain interactive while settling. This update is checked on API 36.

The original logs, captures and APK checksums are in the motion evidence.
These emulator tests do not establish 60 fps on the named 2019 reference phone,
under-50-ms physical sound latency, human TalkBack/focus comfort, or physical
tablet/foldable and interruption behavior. Complete those checks with the full
catalog during the existing device and player qualification gates.
