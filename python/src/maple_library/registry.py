"""Packaged source/schema metadata; no dependency on the development repository."""
from importlib.resources import files
from types import MappingProxyType
import json
from .model import Source, DocumentStatus, read_field, escape
from .raw import Kind, MapleError

_DATA = json.loads(files('maple_library').joinpath('registry.json').read_text())
_OVERRIDES = json.loads(files('maple_library').joinpath('schema-overrides.json').read_text())

def all_sources():
    return frozenset(Source(k) for k in _DATA['sources'])

def expand_sources(sources):
    if sources is None:
        raise MapleError('INVALID_REQUEST')
    result = set()
    for source in sources:
        if not isinstance(source, Source):
            raise MapleError('INVALID_REQUEST')
        if source is Source.SKILLS:
            result.update(s for s in all_sources() if s.value.startswith('SKILL_'))
        elif source is Source.UNION:
            result.update(s for s in all_sources() if s.value.startswith('UNION_'))
        else:
            result.add(source)
    if not result:
        raise MapleError('INVALID_REQUEST')
    return frozenset(result)

def specification(source):
    if source.value not in _DATA['sources']:
        raise MapleError('INVALID_REQUEST')
    spec = _DATA['sources'][source.value]
    return MappingProxyType({**spec, 'fields': MappingProxyType({**_DATA['fields'][spec['schema']],**_OVERRIDES.get(source.value,{})})})

def preset_views(document):
    view = document.view
    return MappingProxyType({k: view.field(k) for k in sorted(document.raw.value) if 'preset' in k})

def diagnostics(document):
    if document.status is not DocumentStatus.FETCHED:
        return ()
    fields = specification(document.source)['fields']
    result = []
    official=_DATA['fields'][_DATA['sources'][document.source.value]['schema']]
    def walk(value, path, pattern):
        expected = fields.get(pattern)
        if pattern in _OVERRIDES.get(document.source.value,{}) and expected!=official.get(pattern):
            result.append('OBSERVED_SCHEMA_EXTENSION:'+path)
        if pattern and expected is None:
            result.append('UNKNOWN_FIELD:' + path)
        elif expected and value.kind is not Kind.NULL:
            actual = {'object':Kind.OBJECT,'array':Kind.ARRAY,'string':Kind.STRING,'number':Kind.NUMBER,'integer':Kind.NUMBER,'boolean':Kind.BOOLEAN}.get(expected)
            if actual is not None and value.kind is not actual:
                result.append('SCHEMA_TYPE_DIFFERENCE:' + path)
        if value.kind is Kind.OBJECT:
            for key, child in value.value.items():walk(child,path+'/'+escape(key),pattern+'/'+escape(key))
        elif value.kind is Kind.ARRAY:
            for i, child in enumerate(value.value):walk(child,path+'/'+str(i),pattern+'/*')
    walk(document.raw,'','')
    return tuple(result)
