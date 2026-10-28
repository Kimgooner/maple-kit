"""Validate distributable resources and licenses without release-account configuration."""
import json
import re
import tarfile
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
config = tomllib.loads((ROOT / 'python/pyproject.toml').read_text())
python_version = config['project']['version']
java_version = re.search(r'^version = "([^"]+)"', (ROOT / 'java/build.gradle.kts').read_text(), re.M)[1]
license_text = (ROOT / 'LICENSE').read_bytes()
assert (ROOT / 'python/LICENSE').read_bytes() == license_text, 'License copies differ'
resources = ['registry.json', 'option-aliases.json', 'option-rules.json', 'schema-overrides.json']
for name in resources:
    python = ROOT / 'python/src/maple_library' / name
    java = ROOT / 'java/src/main/resources/org/whitedoggy/maplelibrary' / name
    assert json.loads(python.read_text()) == json.loads(java.read_text()), f'Resource differs: {name}'
wheel = ROOT / f'python/dist/maple_library-{python_version}-py3-none-any.whl'
with zipfile.ZipFile(wheel) as archive:
    for name in resources:
        assert archive.read('maple_library/' + name) == (ROOT / 'python/src/maple_library' / name).read_bytes()
    assert archive.read(f'maple_library-{python_version}.dist-info/licenses/LICENSE') == license_text
with tarfile.open(ROOT / f'python/dist/maple_library-{python_version}.tar.gz') as archive:
    assert archive.extractfile(f'maple_library-{python_version}/LICENSE').read() == license_text
for suffix in ('', '-sources', '-javadoc'):
    with zipfile.ZipFile(ROOT / f'java/build/libs/maple-library-{java_version}{suffix}.jar') as archive:
        assert archive.read('META-INF/LICENSE') == license_text
        if not suffix:
            for name in resources:
                assert archive.read('org/whitedoggy/maplelibrary/' + name) == (ROOT / 'java/src/main/resources/org/whitedoggy/maplelibrary' / name).read_bytes()
print('Java/Python resources and packaged licenses verified')
