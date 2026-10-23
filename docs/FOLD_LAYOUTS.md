# Fold, hinge and cutout layouts

The app observes WindowManager's layout flow while its Activity is started. A
separating fold or fully occluding hinge partitions the usable window; a flat,
nonseparating crease does not change the layout. Geometry uses window pixels,
including the Compose root's offset, before converting to layout dimensions.
System bars and display cutouts are removed through safe-drawing insets. Content
leaves another 8 dp on each side of a separating feature.

Home, onboarding, levels, stats, settings, Pro, themes, photos, reminders, backups,
loading and storage recovery stay together in the largest unobstructed pane.
Equal areas prefer the top pane, then the reading-start pane. Physical offsets
remain absolute under RTL; puzzle coordinates keep their established direction.

Play, tutorials and demonstrations place the board/header in the first pane and
information/actions in the second when there are exactly two regions and each is
at least 288 dp wide and 240 dp tall. Both scroll independently. Book postures use
side-by-side regions; tabletop postures use top and bottom. Otherwise the largest
region uses the existing single-column or wide layout. Results and their banner
stay in one region. Banner width follows its available pane; active play has no
banner. The existing board renderers retain their 48-dp targets and magnification
controls. Very short regions can require scrolling to reach a board or overview;
observed planning usability remains a physical/player gate.

App-owned alert dialogs retain Material's title, text, action and accessibility
implementation. On folded windows their native modal fills the window, while the
visible content is measured and centered inside one safe region. Outside taps
and Back dismiss as before. A short region scrolls the whole dialog with finite
constraints for its existing text scroller, keeping actions reachable at 200%
text. Unfolding restores ordinary Material sizing. System photo/document pickers,
consent, Billing and full-screen ad windows are supplied by their providers and
still need separate device/service qualification.

Fold changes do not modify puzzle rules, durable attempts, time accounting,
hints, scoring or rewards. The existing repository restores board/history,
selection and magnification after Activity recreation. Native qualification
covers a saved Traffic Jam move, selected magnified view, book/tabletop/unfold,
portrait/landscape resize, recreation, undo/redo/restart and an eight-move finish
with one completion receipt. It does not simulate process death inside a fold
transition or certify all twelve completed solutions with TalkBack.

## Verification

The evidence packet records precise inputs,
reports, APK hashes and captures. Tests use WindowManager's official publisher
rule on native API 26 and API 36 emulators: these are real Android layouts with
injected hinge geometry, not a physical folding panel. All twelve Expert sample
renderers are checked at 200% text in book and tabletop arrangements, with a
representative 48-dp target, hinge exclusion and independently scrolling actions.
Additional checks cover an off-center hinge, flat crease, flat occluding hinge,
RTL, live dialog posture changes, outside taps and Back.

The API 36 cutout case enables Android's native double-cutout overlay, verifies
nonempty native cutout bounds and compares screen/dialog bounds with actual
system/cutout insets. The overlay and display/font settings are restored. The
cutout case requires API 28 or newer and is excluded on API 26.

## Remaining qualification

Use physical folding devices in book, tabletop, flat and closed-cover states,
including multi-window, repeated folding, background/process termination,
200% text and all twelve completed puzzle/tutorial flows. Observe planning
information and magnified controls together, one-handed reach and TalkBack
focus after pane changes. Qualify provider-owned windows, actual cutout hardware,
rotation, animation timing and service behavior. Emulator passes do not close the
physical P2/P7 gates or authorize expansion to the 556-level catalog.

## Primary references

- [Android fold-aware layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware): separating and occluding features partition content; bounds are window-relative.
- [Compose cutouts](https://developer.android.com/develop/ui/compose/system/cutouts): safe-drawing insets protect controls under edge-to-edge display.
- [WindowManager release notes](https://developer.android.com/jetpack/androidx/releases/window): stable 1.5.1 is pinned for the implementation and testing helper. The nine added resolved artifact checksums were verified against Google Maven; see the evidence packet.
