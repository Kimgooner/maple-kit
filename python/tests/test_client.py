import asyncio
from datetime import date, datetime, timezone
import httpx
import pytest
from maple_library import MapleClient, CharacterRequest, ClientConfig, ApiError, Source, Document, CharacterSheet
from maple_library.options import decode_options

class Permits:
    def __init__(self): self.count=0
    async def acquire(self): self.count+=1

def run(coro): return asyncio.run(coro)
def client(handler, **kwargs):
    return MapleClient('test-secret',transport=httpx.MockTransport(handler),limiter=kwargs.pop('limiter',Permits()),random_value=lambda:0,**kwargs)

def test_full_and_group_selection():
    async def check():
        calls=[]
        async def handler(r):
            calls.append(r)
            return httpx.Response(200,json={'ocid':'opaque'} if r.url.path.endswith('/id') else {})
        async with client(handler) as c:
            sheet=await c.get_character(CharacterRequest(nickname='synthetic'))
            assert len(sheet.documents)==27 and len(calls)==27
            assert {r.url.params['character_skill_grade'] for r in calls if 'character_skill_grade' in r.url.params}=={'0','hyperpassive','hyperactive','5','6'}
            assert sheet.document(Source.RING_EXCHANGE).status.value=='UNSUPPORTED'
            calls.clear()
            sheet=await c.get_character_selected(CharacterRequest(ocid='opaque'),{Source.UNION,Source.UNION_RAIDER})
            assert len(calls)==len(sheet.documents)==4
            assert all('/user/union' in r.url.path for r in calls)
    run(check())

@pytest.mark.parametrize('status,partial,expected',[(400,True,'FETCH_FAILED'),(400,False,'UPSTREAM_INVALID_REQUEST'),(401,True,'AUTHENTICATION_FAILED'),(403,True,'PERMISSION_DENIED')])
def test_errors(status,partial,expected):
    async def check():
        async with client(lambda r:httpx.Response(status,json={'error':{'message':'test-secret'}}),config=ClientConfig(retries=0)) as c:
            request=CharacterRequest(ocid='opaque',allow_partial=partial)
            if expected=='FETCH_FAILED':
                sheet=await c.get_character_selected(request,{Source.SYMBOLS})
                assert sheet.document(Source.SYMBOLS).status.value==expected
                assert not sheet.quality()['requested_complete']
            else:
                with pytest.raises(ApiError) as e:await c.get_character_selected(request,{Source.SYMBOLS})
                assert e.value.code==expected and 'test-secret' not in str(e.value)
    run(check())

def test_retry_limiter_and_history():
    async def check():
        permits=Permits();calls=[]
        def handler(r):
            calls.append(r)
            if len(calls)==1:return httpx.Response(429,headers={'Retry-After':'0'},json={})
            return httpx.Response(200,json={'date':'2020-01-01T00:00+09:00','symbol':[]})
        async with client(handler,limiter=permits) as c:
            sheet=await c.get_character_selected(CharacterRequest(ocid='opaque',on_date=date(2020,1,1)),{Source.SYMBOLS})
            assert permits.count==2 and sheet.document(Source.SYMBOLS).attempts==2
            assert all(r.url.params['date']=='2020-01-01' for r in calls)
            assert not sheet.fetch_diagnostics
    run(check())

@pytest.mark.parametrize('body,code',[(b'not json','INVALID_JSON'),(b'[]','INVALID_DOCUMENT'),(b'{"symbol":{}}','INVALID_DOCUMENT')])
def test_invalid_response(body,code):
    async def check():
        async with client(lambda r:httpx.Response(200,content=body)) as c:
            with pytest.raises(ApiError) as e:await c.get_character_selected(CharacterRequest(ocid='opaque'),{Source.SYMBOLS})
            assert e.value.code==code
    run(check())

def test_deadline_and_cancellation():
    async def check():
        stopped=asyncio.Event()
        async def handler(r):
            try:await asyncio.sleep(10)
            finally:stopped.set()
        async with client(handler,config=ClientConfig(deadline=.02)) as c:
            with pytest.raises(ApiError,match='DEADLINE_EXCEEDED'):await c.get_character_selected(CharacterRequest(ocid='opaque'),{Source.SYMBOLS})
            assert stopped.is_set()
        stopped.clear()
        async with client(handler) as c:
            task=asyncio.create_task(c.get_character_selected(CharacterRequest(ocid='opaque'),{Source.SYMBOLS}))
            await asyncio.sleep(.01);task.cancel()
            with pytest.raises(asyncio.CancelledError):await task
            assert stopped.is_set()
    run(check())

def test_options_and_original_spans():
    text='🧪 알 수 없음\n STR +12, DEX +3%'
    options=decode_options(text)
    assert [o.status for o in options]==['UNKNOWN','KNOWN','KNOWN']
    for o in options:assert text[o.start:o.end]==o.raw_text
    ap=decode_options('AP를 직접 투자한 LUK의 15% 만큼 DEX 증가')[0]
    assert ap.value is None and dict(ap.expression)['basis']=='INVESTED_AP.LUK'
    chance=decode_options('공격 시 20% 확률로 STR +12')[0]
    assert chance.value==12 and 'CHANCE_PERCENT=20' in chance.conditions
    doc=Document.parse(Source.OTHER_STATS,'{"x":{"option_type":"STR","option_value":"+12"}}')
    o=doc.decoded_options()[0]
    assert o.pointer=='/x/option_value' and o.option.raw_text=='+12' and o.option.end==3

@pytest.mark.parametrize('case',__import__('json').loads((__import__('pathlib').Path(__file__).parents[2]/'fixtures/cases/options-v1.json').read_text()))
def test_shared_options(case):
    from maple_library.raw import decimal_text
    options=decode_options(case['text'])
    assert ['|'.join((o.status,','.join(o.targets),'-' if o.value is None else decimal_text(o.value),o.unit,','.join(o.conditions),o.rule_id)) for o in options]==case['expected']
    for o in options:assert case['text'][o.start:o.end]==o.raw_text
    if 'expression' in case:assert dict(options[0].expression)==case['expression']

def test_partial_preserves_invalid_structure():
    async def check():
        async with client(lambda r:httpx.Response(200,json={'symbol':{},'future':42})) as c:
            sheet=await c.get_character_selected(CharacterRequest(ocid='opaque',allow_partial=True),{Source.SYMBOLS})
            assert sheet.document(Source.SYMBOLS).raw.child('future').value=='42'
    run(check())

def test_size_limit_and_borrowed_transport():
    async def check():
        class Transport(httpx.AsyncBaseTransport):
            closed=False
            async def handle_async_request(self,r):return httpx.Response(200,content=b'{"long":"1234567890"}')
            async def aclose(self):self.closed=True
        transport=Transport()
        async with MapleClient('test-secret',transport=transport,limiter=Permits(),config=ClientConfig(max_body_bytes=10)) as c:
            with pytest.raises(ApiError,match='RESOURCE_LIMIT_EXCEEDED'):await c.get_character_selected(CharacterRequest(ocid='opaque'),{Source.SYMBOLS})
        assert not transport.closed
    run(check())

def test_timestamp_and_unsupported_no_http():
    from maple_library import FieldState
    doc=Document.parse(Source.BASIC,'{"date":"2026-01-01T00:00+09:00","naive":"2026-01-01T00:00"}')
    assert doc.view.timestamp('date').state is FieldState.VALUE
    assert doc.view.timestamp('naive').state is FieldState.INVALID
    async def check():
        def handler(r):raise AssertionError('unsupported date must not call HTTP')
        async with client(handler) as c:
            sheet=await c.get_character_selected(CharacterRequest(ocid='opaque'),{Source.RING_EXCHANGE})
            assert sheet.document(Source.RING_EXCHANGE).attempts==0
    run(check())

@pytest.mark.parametrize('case',__import__('json').loads((__import__('pathlib').Path(__file__).parents[2]/'fixtures/cases/schema-v1.json').read_text()))
def test_observed_schema(case):
    import json
    d=Document.parse(Source(case['source']),json.dumps(case['json']))
    assert d.structurally_valid
    assert case['preset'] in d.presets()
    diagnostics=d.diagnostics()
    assert not any(x.startswith(('UNKNOWN_FIELD:','SCHEMA_TYPE_DIFFERENCE:')) for x in diagnostics)
    assert len([x for x in diagnostics if x.startswith('OBSERVED_SCHEMA_EXTENSION:')])==case['extensions']
