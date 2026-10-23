#!/usr/bin/env python3
"""Exercise Wave B tutorials and committed moves across real debug process death.

Requires adb on PATH and the isolated 1080x2280 development emulator.
--reset clears only com.parlor.puzzles.debug test data.
"""
import argparse
import hashlib
import json
import re
import time
from datetime import datetime, timezone
import device_smoke as d
from wave_a_device_smoke import visible


def tap(text=None, desc=None, prefix=None):
    for _ in range(8):
        match = next((n for n in d.tree().iter('node') if
                      (text is not None and n.get('text') == text) or
                      (desc is not None and n.get('content-desc') == desc) or
                      (prefix is not None and n.get('content-desc', '').startswith(prefix))), None)
        if match is not None:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', match.get('bounds')))
            # UIAutomator can expose a node clipped beneath the navigation bar.
            if x2 > x1 and y2-y1 >= 30 and 70 <= y1 and y2 <= 2160:
                d.adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
        # Scroll in the page margin: a gesture on a block/tile is an actual drag.
        d.adb('shell', 'input', 'swipe', '30', '1850', '30', '700', '350')
    raise AssertionError(f'Control not found: {text or desc or prefix}')


def tile(value):
    tap(prefix=f'Tile {value},')
    tap(text=f'Slide tile {value} into the empty square.')


def block(identity, x, y, exit=False):
    tap(prefix=f'Block {identity},')
    label = f'Slide {identity} through the opening.' if exit else f'Move {identity} to row {y+1}, column {x+1}.'
    tap(text=label, desc=label)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reset', action='store_true')
    options = parser.parse_args()
    d.OUT = d.ROOT / 'docs/evidence/wave-b'
    if options.reset:
        d.adb('shell', 'pm', 'clear', d.PACKAGE)
    d.adb('shell', 'am', 'start', '-n', f'{d.PACKAGE}/com.parlor.puzzles.MainActivity')
    if visible(text='Continue'):
        tap(text='Continue')
        tap(text='Continue')
        tap(text='Step inside')
    levels = json.loads((d.ROOT / 'app/src/main/assets/levels/wave-b.json').read_text())['levels']
    results = []
    for puzzle, title, short in [('SLIDING_TILES', 'Sliding Tiles', 'tiles'),
                                 ('BLOCK_ESCAPE', 'Block Escape', 'blocks'),
                                 ('BOX_PUSHER', 'Box Pusher', 'boxes')]:
        level = next(l for l in levels if l['puzzle'] == puzzle and l['tier'] == 'EASY')
        tap(text=title)
        tap(text='Level 1')
        if puzzle == 'SLIDING_TILES':
            for value in [4, 5, 8]:
                tile(value)
        elif puzzle == 'BLOCK_ESCAPE':
            block('B', 0, 2)
            block('A', 1, 0)
            block('A', 1, 5, exit=True)
        else:
            tap(desc='Empty floor, row 3, column 2.')
            tap(desc='Box, row 3, column 3.')
            tap(desc='Box, row 3, column 4.')
        tap(text='Begin a puzzle')
        action = level['reference'][0]
        if puzzle == 'SLIDING_TILES':
            tile(action['tile'])
            expected = '1 move'
            restored_cell = 'Tile 4, row 2, column 2.'
        elif puzzle == 'BLOCK_ESCAPE':
            block(action['id'], action['x'], action['y'])
            expected = '1 move'
            restored_cell = 'Block J, row 1, column 2. 1 columns wide and 1 rows tall.'
        else:
            cell, width = action['cell'], level['initial']['width']
            tap(desc=f'Empty floor, row {cell//width+1}, column {cell%width+1}.')
            expected = '0 pushes'
            restored_cell = f'You, row {cell//width+1}, column {cell%width+1}.'
        assert visible(text=expected)
        d.adb('shell', 'am', 'force-stop', d.PACKAGE)
        d.adb('shell', 'am', 'start', '-n', f'{d.PACKAGE}/com.parlor.puzzles.MainActivity')
        if visible(text='Pick up where you left off'):
            tap(text='Pick up where you left off')
        assert visible(text=expected)
        assert visible(desc=restored_cell), restored_cell
        if puzzle == 'BOX_PUSHER':
            assert visible(text='2 walking steps')
        time.sleep(1)
        d.screenshot(f'{short}.png')
        results.append({'puzzle': puzzle, 'level': level['id'], 'tutorial': 'passed',
                        'committed_board_and_counters_after_force_stop': 'passed', 'screenshot': f'{short}.png'})
        print(f'PASS: {title} tutorial, committed board/counters and force-stop/resume.', flush=True)
        d.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        tap(text='Save and leave')
        d.adb('shell', 'input', 'swipe', '540', '700', '540', '1850', '350')
    apk = d.ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    (d.OUT / 'installed-smoke.json').write_text(json.dumps({
        'recorded_at_utc': datetime.now(timezone.utc).isoformat(),
        'package': d.PACKAGE, 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'device': d.adb('shell', 'getprop', 'ro.build.fingerprint').strip(),
        'results': results, 'physical_qualification': False,
    }, indent=2) + '\n')


if __name__ == '__main__':
    main()
