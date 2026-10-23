# Themes and appearance

Settings → Themes and appearance offers six original rooms:

- Linen room (`linen`): the free default, with a fine woven surface.
- Garden room (`sage`): sage greens and a softly speckled surface.
- Rose nook (`rose`): dusty rose and ceramic tones.
- Amber study (`amber`): honey/mustard tones and a woven surface.
- Slate studio (`slate`): muted blue and fine diagonal marks.
- Walnut library (`walnut`): warm wood tones and subtle grain.

Pro includes all six. Each has light and dark colors for app surfaces, controls,
Home artwork and all twelve board families. Terracotta remains the action accent;
dark surfaces use warm charcoal tones. Original code-drawn surface marks are
decorative and carry no puzzle state. Pieces retain names, shapes, position
descriptions, selection outlines and accessibility actions. Peg occupancy uses
filled versus outlined circles as well as its spoken state.

**Light** is the installation default. **Dark** and **Use device setting** are
available to Free and Pro, independently of the room choice. The latter follows
Android's current night configuration; an explicit light/dark choice overrides it.
The actual Activity updates status/navigation icon appearance alongside the app.
No wallpaper-derived dynamic palette replaces these authored colors.

## Access, persistence and backup

`PlayerSettings.theme` keeps a stable room ID; `appearance` is a serialized enum
whose default preserves older light-only settings. Selection writes use DataStore
transactions. The Pro check runs inside the queued settings transaction, so a
queued premium selection fails if ownership changes before that write executes.
Errors retain the prior choice, allow retry and keep Back available.

Free or unresolved ownership displays Linen without overwriting a saved Pro
theme. Restoring confirmed Pro restores the saved choice. Choosing Linen explicitly
replaces that saved choice. A locked room opens the existing Pro screen; returning
preserves the selector. No Pro prompt is inserted into active play.

Themes change presentation only. They do not rewrite attempts, histories, scores,
hint flags, viewports or image bytes. Personal photos and the bundled Garden picture
retain their original pixels; number labels have an opaque contrasting background.
Changing theme/appearance does not clear an active pause or create a new attempt.

Manual backup **schema 4** includes theme and appearance, with the same bounded
photo contract introduced in schema 3. Schema-2/3 imports remain supported as
Linen/light settings; missing appearance defaults to Light. Unknown theme IDs,
appearance enum values and future schemas are rejected before replacement.
Recovery restores the saved choice without granting ownership. Room stays at
schema 4; no new database table or migration is required for a settings change.

## Design boundary and checks

`:core:design` owns shared Compose colors, board skins and decorative marks.
`:feature:library` retains the licensed Nunito typography and provides the theme.
Both library and play consume the same board skin. Portable models contain only
stable selection/access rules and have no Compose or Android imports.

The palette unit checks cover named Material foreground/container pairs, action
and error text, board labels/markers, selected targets and the most affected point
under a decorative mark. These pairs meet 4.5:1 in all twelve room/appearance
combinations, including full-severity protanopia and deuteranopia simulations using
the published Machado–Oliveira–Fernandes linear-RGB matrices. This is a computational
palette check, not a human accessibility qualification. Decorative colors, photos,
disabled Material defaults and every possible rendered component state are not
asserted by that check; the complete component/physical accessibility audit remains
part of the release gate.

Device checks cover saved choices after DataStore reopen, a queued selection after
Pro loss, backup import/recovery, Free fallback, restore of a retained Pro choice,
save failures, Back during a pending write and the actual Settings/Pro Back stack.
Native 320×640-dp/200% font fixtures exercise the selector. A separate compact
render fixture visits all twelve families under all six rooms in both appearances;
it supplements the existing board-completion tests rather than claiming all 144
render combinations are human playtests. A real paused photo board retains its
history, hint flag, selection and image through theme/Pro changes, then resumes redo.

Physical reach, human TalkBack, complete contrast/component-state coverage,
theme-transition frame/heap measurements, tablet/fold/RTL coverage and owner approval
of final identity remain open. These are development assets, not approved store art.

Primary references: [Material 3 color roles](https://developer.android.com/develop/ui/compose/designsystems/material3),
[Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults),
and [the authors' color-vision simulation matrices](https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html).
