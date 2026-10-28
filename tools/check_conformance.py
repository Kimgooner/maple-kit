#!/usr/bin/env python3
"""Compare test-only projections after Java and Python tests have passed."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
java = json.loads((root / 'java/build/conformance.json').read_text())
python = json.loads((root / 'python/build/conformance.json').read_text())
expected = {c['id']: c['expected'] for c in json.loads((root / 'fixtures/cases/offline-v1.json').read_text())}
if java != expected or python != expected:
    raise SystemExit('Conformance failed: results differ from independently reviewed expectations')
print(f'{len(expected)} shared cases: Java, Python, and expected results agree')

# UI projections are compared in full, in addition to hand-written expectations in tests.
java_views = json.loads((root / 'java/build/presentation.json').read_text())
python_views = json.loads((root / 'python/build/presentation.json').read_text())
assert java_views == python_views, 'Java/Python UI projections differ'
cases = json.loads((root / 'fixtures/cases/presentation-v1.json').read_text())
assert set(java_views) == {c['id'] for c in cases}
for case in cases:
    for path, expected_value in case['expected'].items():
        actual = java_views[case['id']]
        for token in path.lstrip('/').split('/'):
            actual = actual[int(token)] if isinstance(actual, list) else actual[token]
        assert actual == expected_value, (case['id'], path)
assert json.loads((root / 'java/build/character-view.json').read_text()) == json.loads((root / 'python/build/character-view.json').read_text()), 'Character views differ'
print(f'{len(cases)} UI cases and all 27 sources: full Java/Python JSON views agree')
