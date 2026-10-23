# Asset provenance

- `tools/levels/generate_traffic.py` authors the first 12 boards by independent
  seeded packing and bounded breadth-first search. Seed: 20260928. Each level
  records the candidate and search count. No third-party layout is used.
- `tools/levels/.../WaveA.kt` creates 16 representative levels from classical Hanoi
  constructions and fixed-seed switch/jug/pancake states. Seed: 20260928. Every
  solution replays through the portable engine; no third-party layout is copied.
- `tools/levels/.../WaveB.kt` creates 12 representative tile/block/box levels with
  seed 20260929. Tile shuffles use legal reverse moves; block packings are original
  random placements; box boards are reverse-authored from goals. Stored routes
  replay through the shipping engines. No commercial layout is copied.
- `feature/play/.../WaveBBoards.kt` draws the original garden-window illustration
  used by bundled image tiles, plus rectangular blocks and non-colour box/goal
  symbols. Image tiles crop the same illustration coordinates at every size.
- Photo qualification uses synthetic fixtures only: runtime solid colours, seeded
  noise and a drawn colour atlas. `core/data/src/androidTest/assets/photos/animated.gif`
  is an original 96×64 red/green two-frame Pillow fixture; it is confined to the
  data test APK. No personal or externally sourced photograph is bundled.
- `tools/levels/.../WaveC.kt` creates 16 representative levels with seed 20260930:
  reverse-authored peg patterns, constrained Queens placements, constructed knight
  tours and original presentations of classical crossing rules. Every reference
  is replayed; no commercial level pack is used. The Medium knight sample is open,
  placing closed tours after open tours in the sample difficulty ladder.
- `feature/play/.../WaveCBoards.kt` draws peg holes, goal rings, fixed/blocked queen
  markers, knight routes and labelled crossing tokens directly in Compose. These
  original functional assets remain subject to the final visual and contrast gate.
- `tools/generate_sfx.py` synthesizes 24 short PCM assets from sinusoids and seeded
  noise. No source recording, music or external sample is used. The code and
  generated WAVs are original project assets. Nine base cues include shared reward
  chimes; five interaction cues each have wood, metal and water versions. Physical
  latency remains unmeasured.
- `feature/play/.../WaveABoards.kt` and `SwitchBoardView.kt` draw original wood
  disks/stacks, water-volume gauges and switch cells using Compose Canvas.
- `feature/library/.../RoomIllustration.kt` draws an original room with a side
  table, window, lamp and plant, plus twelve puzzle objects; `PuzzleRoom.kt` draws
  the shelves. Shapes are authored directly in Compose Canvas and use the six
  existing palette sets. No external reference art or raster asset was added.
  `app/src/main/res/drawable/ic_parlor.xml` is a provisional original tile mark.
  Both are implementation samples, not approved store assets.
- `feature/library/src/main/res/font/nunito.ttf`: Nunito variable font from the
  [Google Fonts distribution](https://github.com/google/fonts/tree/main/ofl/nunito),
  downloaded 2026-09-28. SIL Open Font License 1.1 permits application embedding;
  the complete license and copyright are retained in `docs/licenses/Nunito-OFL.txt`.
  The license is also bundled in the APK's `assets/licenses/Nunito-OFL.txt`.
  SHA-256: `bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207`.
  The app uses the regular, medium and bold weights. The font is bundled for
  offline availability; there is no downloadable-font network provider.

## Room themes

Six authored palettes and their woven, speckled, diagonal and grain surface marks
are original code in `core/design/`; all twelve board families share those tokens.
No raster asset, photograph, external theme pack or additional font was added.
The simulation matrices in the palette test are numerical data from Machado,
Oliveira and Fernandes (2009), linked in `THEMES.md`; they are test-only. Final
identity/store approval and physical theme qualification remain open.
