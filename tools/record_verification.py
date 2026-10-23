#!/usr/bin/env python3
"""Record existing test reports and input/artifact hashes; never fabricate test results."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import argparse
import json
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, default=ROOT / 'docs/evidence/traffic-prototype/verification.json')
parser.add_argument('--archive-reports', action='store_true', help='Copy the exact XML reports beside the evidence record.')
args = parser.parse_args()
reports = []
devices = set()
patterns = ['core/*/build/test-results/test*/TEST-*.xml',
            'core/data/build/outputs/androidTest-results/connected/**/TEST-*.xml',
            'app/build/outputs/androidTest-results/connected/**/TEST-*.xml']
for pattern in patterns:
    for path in ROOT.glob(pattern):
        data = ET.parse(path).getroot()
        devices.update(prop.get('value') for prop in data.findall('./properties/property') if prop.get('name') == 'device')
        reports.append({'cases': [case.get('classname') + '.' + case.get('name') for case in data.findall('testcase')], 'report': str(path.relative_to(ROOT)), 'timestamp': data.get('timestamp'),
                        'tests': int(data.get('tests')), 'failures': int(data.get('failures')),
                        'errors': int(data.get('errors')), 'skipped': int(data.get('skipped', '0')),
                        'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
if not reports or any(r['failures'] or r['errors'] for r in reports):
    raise SystemExit('Missing or failing reports; no passing evidence record produced.')
if args.archive_reports:
    for report in reports:
        source = ROOT / report['report']
        archive_path = Path(*[part for part in Path(report['report']).parts if part != 'build'])
        target = args.output.resolve().parent / (args.output.stem + '-reports') / archive_path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        report['report'] = str(target.relative_to(ROOT))
digest = hashlib.sha256()
for directory in ['app', 'core', 'feature', 'gradle', 'tools', 'benchmark']:
    for path in sorted((ROOT / directory).rglob('*')):
        if not path.is_file() or {'build', '__pycache__'} & set(path.parts):
            continue
        digest.update(str(path.relative_to(ROOT)).encode() + b'\0' + path.read_bytes())
for filename in ['settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat']:
    digest.update(filename.encode() + b'\0' + (ROOT / filename).read_bytes())
apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
content_dir = ROOT / 'app/src/main/assets/levels'
index_path = content_dir / 'catalog-index.json'
index = json.loads(index_path.read_text())
levels = [level for filename in index['files'] for level in json.loads((content_dir / filename).read_text())['levels']]
content_files = ['catalog-index.json', *index['files'], 'daily-schedule.json']
content_hashes = {filename: hashlib.sha256((content_dir / filename).read_bytes()).hexdigest() for filename in content_files}
metadata = json.loads((apk.parent / 'output-metadata.json').read_text())
result = {
    'recorded_at_utc': datetime.now(timezone.utc).isoformat(),
    'variant': metadata['variantName'], 'version': metadata['elements'][0]['versionName'],
    'source_input_sha256': digest.hexdigest(),
    'devices': sorted(devices),
    'reports': reports, 'tests_total': sum(r['tests'] for r in reports),
    'distinct_cases_total': len({case for report in reports for case in report['cases']}),
    'tests_skipped': sum(r['skipped'] for r in reports),
    'catalog': {'content_version': index['contentVersion'], 'levels': len(levels),
                'per_puzzle': {puzzle: sum(level['puzzle'] == puzzle for level in levels) for puzzle in sorted({level['puzzle'] for level in levels})},
                'files_sha256': content_hashes},
    'apk': {'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()},
    'notes': [
        'This records existing reports; it does not run tests or prove unchanged inputs since each report.',
        'Debug APK size is not a delivered release AAB size measurement.',
        'No physical audio, human TalkBack, 2019-device performance, beta or production evidence.',
        'Release catalog verifier intentionally fails: 80 Traffic Jam and 556 total levels required.',
    ],
}
out = args.output
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(result, indent=2) + '\n')
print(f"Recorded {result['tests_total'] - result['tests_skipped']} passing executions across {result['distinct_cases_total']} distinct cases and {result['tests_skipped']} skipped; source and APK hashes saved.")
