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
