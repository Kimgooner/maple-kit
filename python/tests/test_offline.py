from pathlib import Path
import json
from decimal import localcontext, Decimal
import pytest
from maple_library import *
from maple_library.raw import decimal_text, subtract_exact

ROOT = Path(__file__).resolve().parents[2]
CASES = json.loads((ROOT / 'fixtures/cases/offline-v1.json').read_text())


def sheet(documents, rules='0.1'):
    return CharacterSheet('synthetic-ocid', {Source(k): Document.parse(Source(k), v) for k, v in documents.items()}, parser_rules_version=rules)


def projection(diff):
    return {
        'changes': ['|'.join([c.source.value, c.before_pointer, c.after_pointer, c.kind,
                            c.before.state.value, c.after.state.value,
                            '-' if c.delta is None else decimal_text(c.delta), str(c.semantic_changed).lower()]) for c in diff.changes],
        'compared': [s.value for s in diff.compared_documents],
        'skipped': [s.value + ':' + reason for s, reason in diff.skipped_documents],
        'diagnostics': list(diff.diagnostics),
    }


@pytest.mark.parametrize('case', CASES, ids=lambda c: c['id'])
def test_shared_expected(case):
    with localcontext() as context:
        context.prec = 2
        result = sheet(case['before']).compare_to(sheet(case['after'], case.get('afterRules', '0.1')))
    assert projection(result) == case['expected']


def test_export_results():
    values = {c['id']: projection(sheet(c['before']).compare_to(sheet(c['after'], c.get('afterRules', '0.1')))) for c in CASES}
    directory = ROOT / 'python/build'
    directory.mkdir(exist_ok=True)
    (directory / 'conformance.json').write_text(json.dumps(values, ensure_ascii=False, indent=2))


@pytest.mark.parametrize('text,code', [('{"x":1,"x":2}', 'INVALID_JSON'), ('NaN', 'INVALID_JSON'),
    ('{} {}', 'INVALID_JSON'), ('[1,]', 'INVALID_JSON'), ('{"x":1e999999}', 'RESOURCE_LIMIT_EXCEEDED'),
    ('[' * 129 + '0' + ']' * 129, 'RESOURCE_LIMIT_EXCEEDED')])
def test_reject_invalid(text, code):
    with pytest.raises(MapleError) as exc:
        parse_json(text)
    assert exc.value.code == code
    assert text not in str(exc.value)


def test_raw_and_typed_views():
    document = Document.parse(Source.SYMBOLS, (ROOT / 'fixtures/raw/symbols.json').read_text())
    symbol = document.symbols().value[0]
    assert symbol.number('symbol_str').value == Decimal('10')
    assert symbol.number('symbol_dex').value == Decimal('20')
    assert symbol.number('symbol_hp').value == Decimal(0)
    assert symbol.field('unknown').raw.value['keep'].value is True
    with pytest.raises(TypeError):
        document.raw.value['symbol'] = None
    equipment = Document.parse(Source.EQUIPMENT, (ROOT / 'fixtures/raw/equipment.json').read_text())
    assert equipment.equipment(1).value == ()
    assert equipment.equipment(2).state is FieldState.MISSING
    assert equipment.current_preset_number().value == 1
    weapon = equipment.equipment().value[0]
    assert weapon.object('item_total_option').value.number('attack_power').value == 30
    assert weapon.object('item_base_option').value.number('attack_power').value == 10


def test_numeric_tokens_and_states():
    doc = Document.parse(Source.SYMBOLS, '{"symbol":[{"a":null,"b":"","c":"bad","d":4.50,"e":"0"}]}')
    entity = doc.symbols().value[0]
    assert entity.field('d').raw.value == '4.50'
    assert [entity.number(k).state.value for k in ['missing','a','b','c','d','e']] == ['MISSING','NULL','INVALID','INVALID','VALUE','VALUE']
    assert entity.text('b').value == ''


def test_invalid_documents_identity_and_version():
    with pytest.raises(MapleError, match='INVALID_DOCUMENT'):
        Document.parse(Source.SYMBOLS, '[]')
    a = CharacterSheet('a', {})
    assert 'ocid=' not in repr(a)
    with pytest.raises(MapleError, match='SUBJECT_MISMATCH'):
        a.compare_to(CharacterSheet('b', {}))
    with pytest.raises(MapleError, match='INCOMPATIBLE_CONTRACT'):
        a.compare_to(CharacterSheet('a', {}, contract_version='0.2'))
    failed = CharacterSheet('a', {Source.SYMBOLS: Document(Source.SYMBOLS, DocumentStatus.FETCH_FAILED)})
    assert not a.compare_to(failed).changes


def test_defensive_copy():
    values = {'x': RawValue(Kind.STRING, 'kept')}
    raw = RawValue(Kind.OBJECT, values)
    values.clear()
    assert raw.child('x').value == 'kept'
    documents = {Source.SYMBOLS: Document.parse(Source.SYMBOLS, '{}')}
    snapshot = CharacterSheet('synthetic', documents)
    documents.clear()
    assert snapshot.document(Source.SYMBOLS).status is DocumentStatus.FETCHED
