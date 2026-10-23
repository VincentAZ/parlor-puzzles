#!/usr/bin/env python3
"""Exercise all four Wave C tutorials and real process-death restoration.

Requires adb on PATH and the isolated 1080x2280 development emulator.
--reset clears only com.parlor.puzzles.debug test data.
"""
import argparse
import hashlib
import json
import time
from datetime import datetime, timezone
import device_smoke as d
from wave_a_device_smoke import visible
from wave_b_device_smoke import tap


def peg(source, destination, width):
    tap(prefix=f'Peg, row {source//width+1}, column {source%width+1}.')
    tap(text=f'Jump from row {source//width+1}, column {source%width+1} to row {destination//width+1}, column {destination%width+1}.')


def queen(cell, side):
    tap(desc=f'Empty square, row {cell//side+1}, column {cell%side+1}.')


def knight(cell, side):
    tap(prefix=f'Row {cell//side+1}, column {cell%side+1}. Not visited.')


def crossing(passengers, right=False):
    names={'W':'Wolf W', 'G':'Goat G', 'C':'Cabbage C'}
    for actor in passengers:
        tap(prefix=f'{names[actor]},')
    tap(text='Cross left' if right else 'Cross right')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reset', action='store_true')
    options=parser.parse_args()
    d.OUT=d.ROOT/'docs/evidence/wave-c'
    apk=d.ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    expected_apk=hashlib.sha256(apk.read_bytes()).hexdigest()
    installed_path=d.adb('shell','pm','path',d.PACKAGE).strip().removeprefix('package:')
    installed_apk=d.adb('shell','sha256sum',installed_path).split()[0]
    assert installed_apk==expected_apk, 'Install the current debug APK before running the smoke check.'
    if options.reset:
        d.adb('shell', 'pm', 'clear', d.PACKAGE)
    d.adb('shell', 'am', 'start', '-n', f'{d.PACKAGE}/com.parlor.puzzles.MainActivity')
    if visible(text='Continue'):
        tap(text='Continue'); tap(text='Continue'); tap(text='Step inside')
    levels=json.loads((d.ROOT/'app/src/main/assets/levels/wave-c.json').read_text())['levels']
    results=[]
    for puzzle,title,short in [('PEG_SOLITAIRE','Peg Solitaire','pegs'), ('QUEENS','Queens','queens'),
                                ('KNIGHTS_TOUR','Knight’s Tour','knight'), ('CROSSINGS','Crossings','crossings')]:
        level=next(l for l in levels if l['puzzle']==puzzle and l['tier']=='EASY')
        tap(text=title); tap(text='Level 1')
        if puzzle=='PEG_SOLITAIRE':
            for source,destination in [(12,10),(15,5),(0,10)]: peg(source,destination,5)
        elif puzzle=='QUEENS':
            for cell in [7,8,14]: queen(cell,4)
        elif puzzle=='KNIGHTS_TOUR':
            for cell in [7,4,13]: knight(cell,5)
        else:
            crossing(['G']); crossing([],right=True); crossing(['C'])
        tap(text='Begin a puzzle')
        action=level['reference'][0]
        if puzzle=='PEG_SOLITAIRE':
            peg(action['from'],action['to'],7)
            cell=action['to'];restored=f'Peg, row {cell//7+1}, column {cell%7+1}.'
        elif puzzle=='QUEENS':
            cell=action['cell'];side=level['initial']['side'];queen(cell,side)
            restored=f'Queen, row {cell//side+1}, column {cell%side+1}.'
        elif puzzle=='KNIGHTS_TOUR':
            cell=action['cell'];side=level['initial']['side'];knight(cell,side)
            restored=f'Row {cell//side+1}, column {cell%side+1}. Visited at step 2. The knight is here.'
        else:
            crossing(action['passengers']);restored='Goat G, right bank'
        count='Actions: 1' if puzzle in ['QUEENS','KNIGHTS_TOUR'] else '1 move'
        assert visible(text=count), count
        d.adb('shell','am','force-stop',d.PACKAGE)
        d.adb('shell','am','start','-n',f'{d.PACKAGE}/com.parlor.puzzles.MainActivity')
        if visible(text='Pick up where you left off'): tap(text='Pick up where you left off')
        assert visible(text=count), count
        assert visible(prefix=restored), restored
        time.sleep(1)
        d.screenshot(f'{short}.png')
        results.append({'puzzle':puzzle,'level':level['id'],'tutorial':'passed',
                        'committed_board_and_counters_after_force_stop':'passed','screenshot':f'{short}.png'})
        print(f'PASS: {title} tutorial, committed board/counters and force-stop/resume.',flush=True)
        d.adb('shell','input','keyevent','KEYCODE_BACK');tap(text='Save and leave')
        d.adb('shell','input','swipe','30','700','30','1850','350')
    (d.OUT/'installed-smoke.json').write_text(json.dumps({
        'recorded_at_utc':datetime.now(timezone.utc).isoformat(),'package':d.PACKAGE,
        'apk_sha256':installed_apk,
        'device':d.adb('shell','getprop','ro.build.fingerprint').strip(),
        'results':results,'physical_qualification':False},indent=2)+'\n')


if __name__=='__main__':
    main()
