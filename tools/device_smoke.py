#!/usr/bin/env python3
"""Drive the isolated development emulator through tutorial, a move and process death.

Requires adb on PATH. Targets only the development package on an emulator. Use
--reset explicitly for a fresh test install (erases this debug package's test data).
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

PACKAGE = 'com.parlor.puzzles.debug'
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/evidence/traffic-prototype'


def adb(*args, binary=False):
    return subprocess.check_output(['adb', '-e', *args], text=not binary)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/parlor-smoke.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/parlor-smoke.xml'))


def tap(text=None, desc=None, prefix=None):
    nodes = list(tree().iter('node'))
    match = next((n for n in nodes if
        (text is not None and n.get('text') == text) or
        (desc is not None and n.get('content-desc') == desc) or
        (prefix is not None and n.get('content-desc','').startswith(prefix))), None)
    if match is None:
        raise AssertionError(f'Control not visible: {text or desc or prefix}')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', match.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def screenshot(name):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_bytes(adb('exec-out','screencap','-p',binary=True))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--reset', action='store_true')
    options = parser.parse_args()
    if options.reset:
        adb('shell','pm','clear',PACKAGE)
    adb('shell','am','start','-n',f'{PACKAGE}/com.parlor.puzzles.MainActivity')
    tap(text='Continue'); tap(text='Continue'); tap(text='Step inside')
    tree(); screenshot('home.png')
    tap(text='Find your next puzzle'); tap(text='Level 1')
    tap(prefix='Piece A,'); tap(prefix='Piece B,')
    tap(desc='Move piece B to row 4'); tap(prefix='Piece A,')
    tap(desc='Slide piece A through the exit'); tap(text='Begin a puzzle')
    levels = json.loads((ROOT / 'app/src/main/assets/levels/traffic-jam.json').read_text())['levels']
    level = levels[0]; move = level['reference'][0]
    piece = next(p for p in level['initial']['pieces'] if p['id'] == move['pieceId'])
    tap(prefix=f"Piece {piece['id']},")
    unit = 'column' if piece['axis'] == 'HORIZONTAL' else 'row'
    tap(desc=f"Move piece {piece['id']} to {unit} {move['to']+1}")
    assert any(n.get('text')=='1 move' for n in tree().iter('node'))
    screenshot('play.png')
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-n',f'{PACKAGE}/com.parlor.puzzles.MainActivity')
    restored = tree()
    if any(n.get('text') == 'Pick up where you left off' for n in restored.iter('node')):
        tap(text='Pick up where you left off')
    assert any(n.get('text')=='1 move' for n in tree().iter('node'))
    screenshot('restored.png')
    print('PASS: onboarding, four interactive tutorial steps, committed move and force-stop/resume.')


if __name__ == '__main__':
    main()
