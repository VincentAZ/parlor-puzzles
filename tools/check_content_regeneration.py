#!/usr/bin/env python3
"""Regenerate all Kotlin content waves in a temporary catalog with a later epoch.

Requires JDK 17 and :tools:levels:installDist. Does not edit bundled app content.
"""
import hashlib
import json
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'app/src/main/assets/levels'
generator=ROOT/'tools/levels/build/install/levels/bin/levels'
if not generator.is_file():
    raise SystemExit('First run ./gradlew :tools:levels:installDist with JDK 17.')
with tempfile.TemporaryDirectory(prefix='parlor-regeneration-') as directory:
    destination=Path(directory)
    for path in source.glob('*.json'): shutil.copy2(path,destination/path.name)
    index=json.loads((destination/'catalog-index.json').read_text())
    index['contentVersion']=5;index['files'].append('future-wave.json')
    (destination/'future-wave.json').write_text(json.dumps({'contentVersion':5,'levels':[]})+'\n')
    (destination/'catalog-index.json').write_text(json.dumps(index)+'\n')
    schedule=json.loads((destination/'daily-schedule.json').read_text())
    schedule['epochs'].append({'version':5,'effectiveFrom':'2026-10-10','pool':schedule['epochs'][-1]['pool']})
    (destination/'daily-schedule.json').write_text(json.dumps(schedule)+'\n')
    hashes={path.name:hashlib.sha256(path.read_bytes()).hexdigest() for path in destination.glob('*.json') if path.name!='catalog-index.json'}
    for wave in ['a','b','c']:
        subprocess.run([str(generator),f'generate-wave-{wave}',str(destination)],check=True)
        assert json.loads((destination/'catalog-index.json').read_text())==index
        for filename,digest in hashes.items():
            assert hashlib.sha256((destination/filename).read_bytes()).hexdigest()==digest, (wave,filename)
    output=ROOT/'docs/evidence/wave-c/content-regeneration.json'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps({
        'recorded_at_utc':datetime.now(timezone.utc).isoformat(),'result':'passed',
        'check':'Regenerate Waves A/B/C with a synthetic later catalog file and frozen epoch',
        'all_existing_content_bytes_preserved':True,'future_index_entry_and_version_preserved':True,
        'frozen_epochs_preserved':True,'files_sha256':hashes,
        'notes':['Future file and epoch are fixtures in a temporary directory, never bundled.']},indent=2)+'\n')
    print('PASS: all three waves reproduce exactly and preserve later content and schedules.')
