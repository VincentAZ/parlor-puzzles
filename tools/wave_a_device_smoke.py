#!/usr/bin/env python3
"""Exercise four first visits and force-stop/resume on the isolated debug emulator.

Requires adb on PATH. --reset erases only com.parlor.puzzles.debug test data.
"""
import argparse
import json
import time
import device_smoke as d


def visible(**match):
    return any((match.get('text') is not None and n.get('text') == match['text']) or
               (match.get('desc') is not None and n.get('content-desc') == match['desc']) or
               (match.get('prefix') is not None and n.get('content-desc', '').startswith(match['prefix']))
               for n in d.tree().iter('node'))


def tap(**match):
    for _ in range(8):
        if visible(**match):
            d.tap(**match)
            return
        d.adb('shell', 'input', 'swipe', '540', '1850', '540', '700', '350')
    raise AssertionError(f'Control not found: {match}')


def move(action, flip_count=2):
    kind = {'transfer_disk':'disk', 'fill_jug':'fill', 'empty_jug':'empty', 'pour_jug':'pour', 'flip_prefix':'flip'}.get(action['type'], action['type'])
    if kind == 'disk':
        tap(prefix=f"Peg {action['from']+1}.")
        tap(text=f"To peg {action['to']+1}")
    elif kind == 'toggle':
        tap(desc=f"Row {action['index']//3+1}, column {action['index']%3+1}")
    elif kind in ('fill', 'empty', 'pour'):
        tap(prefix=f"Jug {action.get('jug', action.get('from'))+1},")
        tap(text={'fill':'Fill', 'empty':'Empty', 'pour':f"Pour to jug {action.get('to',0)+1}"}[kind])
    elif kind == 'flip':
        for _ in range(abs(action['count']-flip_count)):
            tap(text='More' if action['count'] > flip_count else 'Fewer')
        tap(text='Flip')
    else:
        raise AssertionError(kind)


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument('--reset', action='store_true')
    options = args.parse_args()
    d.OUT = d.ROOT / 'docs/evidence/wave-a'
    if options.reset:
        d.adb('shell', 'pm', 'clear', d.PACKAGE)
    d.adb('shell', 'am', 'start', '-n', f'{d.PACKAGE}/com.parlor.puzzles.MainActivity')
    if visible(text='Continue'):
        tap(text='Continue'); tap(text='Continue'); tap(text='Step inside')
    levels = json.loads((d.ROOT / 'app/src/main/assets/levels/wave-a.json').read_text())['levels']
    for puzzle, title, short in [('HANOI','Tower of Hanoi','hanoi'), ('SWITCH_GRID','Switch Grid','switch'),
                                 ('WATER_JUGS','Water Jugs','jugs'), ('PANCAKE_FLIP','Pancake Flip','pancake')]:
        level = next(l for l in levels if l['puzzle']==puzzle and l['tier']=='EASY')
        tap(text=title); tap(text='Level 1')
        if puzzle == 'HANOI':
            tap(prefix='Peg 1.'); tap(text='To peg 3')
            move({'type':'disk','from':0,'to':1}); move({'type':'disk','from':2,'to':1})
        elif puzzle == 'SWITCH_GRID':
            for index in [0,4,8]: move({'type':'toggle','index':index})
        elif puzzle == 'WATER_JUGS':
            move({'type':'fill','jug':0}); move({'type':'pour','from':0,'to':1})
        else:
            count = 2
            for value in [3,4,2]: move({'type':'flip','count':value}, count); count=value
        tap(text='Begin a puzzle')
        move(level['reference'][0])
        assert visible(text='1 move')
        d.adb('shell','am','force-stop',d.PACKAGE)
        d.adb('shell','am','start','-n',f'{d.PACKAGE}/com.parlor.puzzles.MainActivity')
        if visible(text='Pick up where you left off'): tap(text='Pick up where you left off')
        assert visible(text='1 move')
        time.sleep(1)  # A semantic node can appear before the navigation transition settles.
        d.screenshot(f'{short}.png')
        print(f'PASS: {title} tutorial, committed move and force-stop/resume.', flush=True)
        d.adb('shell','input','keyevent','KEYCODE_BACK'); tap(text='Save and leave')
        # A cold-start resume returns directly to Home; the screen may remain scrolled.
        d.adb('shell','input','swipe','540','700','540','1850','350')


if __name__ == '__main__':
    main()
