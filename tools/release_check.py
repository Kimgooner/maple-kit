"""Local release inventory; --public additionally checks owner-supplied release metadata."""
import argparse,json,hashlib,zipfile,tarfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--public',action='store_true');args=p.parse_args()
 config=json.loads((ROOT/'release/config.json').read_text());errors=[]
 if (ROOT/'LICENSE').read_bytes()!=(ROOT/'python/LICENSE').read_bytes():errors.append('LICENSE_DRIFT')
 resources=['registry.json','option-aliases.json','option-rules.json','schema-overrides.json']
 for name in resources:
  if json.loads((ROOT/'python/src/maple_library'/name).read_text())!=json.loads((ROOT/'java/src/main/resources/org/whitedoggy/maplelibrary'/name).read_text()):errors.append('RESOURCE_DRIFT:'+name)
 versions=[('python/pyproject.toml',config['python_version']),('java/build.gradle.kts',config['java_version'])]
 for file,version in versions:
  if f'version = "{version}"' not in (ROOT/file).read_text():errors.append('VERSION_DRIFT:'+file)
 wheel=ROOT/'python/dist'/f"maple_library-{config['python_version']}-py3-none-any.whl"
 sdist=ROOT/'python/dist'/f"maple_library-{config['python_version']}.tar.gz"
 jar=ROOT/'java/build/libs'/f"maple-library-{config['java_version']}.jar"
 artifacts=[wheel,sdist,jar,jar.with_name(jar.stem+'-sources.jar'),jar.with_name(jar.stem+'-javadoc.jar')]
 for path in artifacts:
  if not path.is_file():errors.append('MISSING_ARTIFACT:'+path.name)
 for archive,prefix in [(wheel,'maple_library/'),(jar,'org/whitedoggy/maplelibrary/')]:
  if archive.exists():
   with zipfile.ZipFile(archive) as z:
    for name in resources:
     if prefix+name not in z.namelist():errors.append('MISSING_PACKAGED_RESOURCE:'+archive.name+':'+name)
 if args.public:
  for field in ['repository_url','license_spdx','copyright_holder','developer_id','developer_name','maven_namespace_verified','pypi_publisher_configured']:
   if not config.get(field):errors.append('UNRESOLVED:'+field)
  if not (ROOT/'LICENSE').exists():errors.append('MISSING_LICENSE')
  if 'dev' in config['java_version'] or 'dev' in config['python_version']:errors.append('DEVELOPMENT_VERSION')
 inventory={'public_ready':args.public and not errors,'errors':errors,'artifacts':[{'file':str(path.relative_to(ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size} for path in artifacts if path.exists()]}
 (ROOT/'release/artifacts.json').write_text(json.dumps(inventory,indent=2)+'\n');print(json.dumps(inventory,indent=2))
 raise SystemExit(bool(errors))
if __name__=='__main__':main()
