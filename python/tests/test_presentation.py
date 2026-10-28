"""Shared UI examples plus public typed-access and failure semantics."""
import asyncio
from decimal import Decimal
import json
from pathlib import Path
import httpx
import pytest
from maple_library import Document, DocumentStatus, Source, CharacterSheet, MapleError, MapleClient

ROOT=Path(__file__).resolve().parents[2]
CASES=json.loads((ROOT/'fixtures/cases/presentation-v1.json').read_text())

def pointer(value,path):
    for token in path.lstrip('/').split('/'):
        value=value[int(token)] if isinstance(value,list) else value[token]
    return value

def model(case):return Document.parse(Source(case['source']),json.dumps(case['raw'])).as_model()

@pytest.mark.parametrize('case',CASES,ids=lambda c:c['id'])
def test_shared(case):
    node=model(case)
    view=node.to_view()
    assert json.loads(node.to_json())==view
    if case in CASES[:27]:
        assert not [d for d in Document.parse(Source(case['source']),json.dumps(case['raw'])).diagnostics() if not d.startswith('OBSERVED_SCHEMA_EXTENSION')]
    for path,expected in case['expected'].items():assert pointer(view,path)==expected, path

def test_export():
    path=ROOT/'python/build/presentation.json';path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps({c['id']:model(c).to_view() for c in CASES},ensure_ascii=False))

def test_typed_item_precision_presets_and_immutability():
    equipment=model(CASES[0]);item=equipment.weapon
    assert item.name=='합성 튜너'
    assert item.stats.attack_power==Decimal('1122')
    assert item.options.scroll.attack_power==Decimal('81')
    assert item.potential.lines[0].effects[0].value==Decimal('13')
    assert item.potential.lines[1].parse_status=='UNKNOWN'
    assert item.soul.attack_power==20
    assert item.soul.potential.amplification_level==4
    assert equipment.current_preset_number==2
    assert equipment.preset(2).items[0].name!='합성 튜너'
    assert equipment.title_preset(1).name=='합성 칭호'
    assert 'DEX +0' not in item.stats.to_text()
    assert 'DEX +0' in item.stats.to_text(include_zero=True)
    assert '공격력 +1122' in item.stats.to_text()
    with pytest.raises(AttributeError):item.name='overwrite'
    view=item.to_view();view['name']='overwrite'
    assert item.name=='합성 튜너'
    with pytest.raises(MapleError,match='FIELD_UNAVAILABLE'):equipment.preset(1)
    with pytest.raises(MapleError,match='INVALID_REQUEST'):equipment.preset(True)

def test_all_sources_character_groups_and_statuses():
    docs={Source(c['source']):Document.parse(Source(c['source']),json.dumps(c['raw'])) for c in CASES[:27]}
    assert len(docs)==27
    character=CharacterSheet('synthetic',docs).as_character()
    assert character.name=='합성 캐릭터'
    assert character.union.basic.level==9000
    assert character.union.raider.blocks[0].positions[0].x==-2
    assert character.union.raider.member_effects[0].effects[0].value==40
    assert character.symbols.items[0].growth.current==123
    assert character.skills.sixth.items[0].level==30
    assert character.hexa.cores.cores[0].level==10
    assert character.hyper_stats.items[0].name=='데미지'
    assert character.pets.items[0].equipment.detail.pointer=='/pet_2_equipment'
    assert len(character.to_view()['sources'])==27
    assert character.union.to_view()==character.to_view()['union']
    assert json.loads(character.to_json())==character.to_view()
    (ROOT/'python/build/character-view.json').write_text(character.to_json())

def test_partial_and_malformed_are_not_empty_success():
    for status in [DocumentStatus.NOT_REQUESTED,DocumentStatus.FETCH_FAILED,DocumentStatus.UNSUPPORTED]:
        character=CharacterSheet('synthetic',{Source.EQUIPMENT:Document(Source.EQUIPMENT,status)}).as_character()
        with pytest.raises(MapleError,match='DOCUMENT_UNAVAILABLE'):_=character.equipment
        view=character.to_view()
        assert view['equipment'] is None
        assert view['sources']['EQUIPMENT']['status']==status.value
    item=model(next(c for c in CASES if c['id']=='invalid-types')).items[0]
    with pytest.raises(MapleError,match='INVALID_FIELD'):_=item.starforce
    with pytest.raises(MapleError,match='INVALID_FIELD'):_=item.stats.str
    assert item.detail.text('starforce').value=='oops'

def test_client_convenience_fetches_only_selected_and_never_on_access():
    async def run():
        seen=[]
        async def handler(request):
            seen.append(request.url.path)
            return httpx.Response(200,json={'ocid':'synthetic'} if request.url.path.endswith('/id') else {'symbol':[]})
        async with MapleClient('synthetic-key',transport=httpx.MockTransport(handler)) as client:
            character=await client.character('합성 캐릭터',sources=[Source.SYMBOLS])
            assert character.symbols.items==()
            character.symbols.to_view();character.to_json()
            assert seen==['/maplestory/v1/id','/maplestory/v1/character/symbol-equipment']
            with pytest.raises(MapleError,match='DOCUMENT_UNAVAILABLE'):_=character.union.basic
    asyncio.run(run())
