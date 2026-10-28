"""Async collection with explicit source scope, bounded retries, and cancellation."""
import asyncio
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from zoneinfo import ZoneInfo
from email.utils import parsedate_to_datetime
import random
import math
import ssl
import httpx
from .raw import MapleError, Kind, parse_json
from .model import CharacterSheet, Document, DocumentStatus, Source
from .registry import all_sources, expand_sources, specification


class ApiError(MapleError):
    def __init__(self, code, *, source=None, upstream_code=None, http_status=None, retryable=False, attempts=0):
        super().__init__(code)
        self.source, self.upstream_code, self.http_status = source, upstream_code, http_status
        self.retryable, self.attempts = retryable, attempts


@dataclass(frozen=True, slots=True)
class CharacterRequest:
    nickname: str | None = field(default=None, repr=False)
    ocid: str | None = field(default=None, repr=False)
    on_date: date | None = None
    allow_partial: bool = False

    def __post_init__(self):
        if (self.nickname is None) == (self.ocid is None):
            raise MapleError('INVALID_REQUEST')
        target = self.nickname if self.nickname is not None else self.ocid
        if not isinstance(target,str) or not target.strip() or (self.on_date is not None and type(self.on_date) is not date):
            raise MapleError('INVALID_REQUEST')
        if type(self.allow_partial) is not bool:
            raise MapleError('INVALID_REQUEST')


class RateLimiter:
    """Share this instance between clients on the same asyncio event loop/key."""
    def __init__(self, requests_per_second=5):
        if not math.isfinite(requests_per_second) or requests_per_second <= 0:
            raise MapleError('INVALID_REQUEST')
        self._interval = 1 / requests_per_second
        self._next = 0.0
        self._lock = asyncio.Lock()

    async def acquire(self):
        # Wait under the lock so cancellation does not leave unbounded reservations.
        async with self._lock:
            loop = asyncio.get_running_loop()
            await asyncio.sleep(max(0, self._next - loop.time()))
            self._next = loop.time() + self._interval


@dataclass(frozen=True, slots=True)
class ClientConfig:
    concurrency: int = 4
    request_timeout: float = 10
    connect_timeout: float = 5
    deadline: float = 60
    retries: int = 2
    max_body_bytes: int = 16 * 1024 * 1024

    def __post_init__(self):
        if type(self.concurrency) is not int or self.concurrency < 1 or type(self.retries) is not int or self.retries < 0 or type(self.max_body_bytes) is not int or self.max_body_bytes < 1 or self.max_body_bytes > 16*1024*1024:
            raise MapleError('INVALID_REQUEST')
        if any(not math.isfinite(x) or x <= 0 for x in (self.request_timeout,self.connect_timeout,self.deadline)):
            raise MapleError('INVALID_REQUEST')


def _error(status, raw, source, attempt):
    code = None
    if raw is not None and raw.kind is Kind.OBJECT:
        error = raw.child('error')
        name = error.child('name') if error is not None else None
        if name is not None and name.kind is Kind.STRING and name.value.startswith('OPENAPI'):
            code = name.value if name.value in {f'OPENAPI{i:05}' for i in (1,2,3,4,5,6,7,9,10,11)} else None
    mapping = {'OPENAPI00001':'UPSTREAM_UNAVAILABLE','OPENAPI00002':'PERMISSION_DENIED','OPENAPI00003':'INVALID_IDENTIFIER',
               'OPENAPI00004':'UPSTREAM_INVALID_REQUEST','OPENAPI00005':'AUTHENTICATION_FAILED','OPENAPI00006':'INVALID_API_ROUTE_OR_SCOPE',
               'OPENAPI00007':'RATE_LIMITED','OPENAPI00009':'DATA_NOT_READY','OPENAPI00010':'UPSTREAM_UNAVAILABLE','OPENAPI00011':'UPSTREAM_UNAVAILABLE'}
    name = mapping.get(code, 'AUTHENTICATION_FAILED' if status == 401 else 'PERMISSION_DENIED' if status == 403 else
                       'RATE_LIMITED' if status == 429 else 'UPSTREAM_UNAVAILABLE' if status >= 500 else 'UPSTREAM_INVALID_REQUEST')
    return ApiError(name,source=source,upstream_code=code,http_status=status,
                    retryable=name in ('RATE_LIMITED','UPSTREAM_UNAVAILABLE'),attempts=attempt)


class MapleClient:
    def __init__(self, api_key: str, *, config=ClientConfig(), limiter=None, transport=None,
                 clock=lambda: datetime.now(timezone.utc), sleep=asyncio.sleep, random_value=random.random,
                 base_url='https://open.api.nexon.com'):
        if not isinstance(api_key,str) or not api_key.strip(): raise MapleError('INVALID_REQUEST')
        url = httpx.URL(base_url)
        if url.scheme != 'https' and not (url.scheme=='http' and url.host in ('127.0.0.1','localhost','::1')):
            raise MapleError('INVALID_REQUEST')
        if url.username or url.password or url.query or url.fragment or url.path not in ('','/'):
            raise MapleError('INVALID_REQUEST')
        self._key=api_key
        self._config=config
        self._limiter=limiter or RateLimiter()
        # A custom HTTPX transport remains caller-owned. The wrapper avoids closing it.
        class BorrowedTransport(httpx.AsyncBaseTransport):
            async def handle_async_request(self, request):return await transport.handle_async_request(request)
            async def aclose(self):pass
        self._http=httpx.AsyncClient(base_url=base_url,transport=BorrowedTransport() if transport else None,
                                    follow_redirects=False,trust_env=False,
                                    timeout=httpx.Timeout(config.request_timeout,connect=config.connect_timeout))
        self._clock,self._sleep,self._random=clock,sleep,random_value
        self._semaphore=asyncio.Semaphore(config.concurrency)
        self._active=set()
        self._closed=False

    def __repr__(self):return 'MapleClient(api_key=<redacted>)'
    async def __aenter__(self):return self
    async def __aexit__(self,*args):await self.aclose()
    async def aclose(self):
        self._closed=True
        current=asyncio.current_task()
        pending=[t for t in self._active if t is not current]
        for task in pending:task.cancel()
        if pending:await asyncio.gather(*pending,return_exceptions=True)
        await self._http.aclose()
        self._key=''

    async def get_character(self, request):
        return await self.get_character_selected(request, all_sources())

    async def get_character_selected(self, request, sources):
        requested=frozenset(sources) if sources is not None else frozenset()
        expanded=expand_sources(requested)
        if self._closed or not isinstance(request,CharacterRequest):raise MapleError('INVALID_REQUEST')
        if request.on_date is not None and request.on_date >= self._clock().astimezone(ZoneInfo('Asia/Seoul')).date():
            raise MapleError('INVALID_REQUEST')
        task=asyncio.current_task();self._active.add(task)
        children=[]
        try:
            async with asyncio.timeout(self._config.deadline):
                ocid=request.ocid
                if ocid is None:
                    raw,_,_ = await self._fetch('/maplestory/v1/id',{'character_name':request.nickname},None)
                    identity=raw.child('ocid') if raw.kind is Kind.OBJECT else None
                    if identity is None or identity.kind is not Kind.STRING or not identity.value.strip():raise ApiError('INVALID_DOCUMENT')
                    ocid=identity.value
                async def collect(source):
                    spec=specification(source)
                    query_date=request.on_date or self._clock().astimezone(ZoneInfo('Asia/Seoul')).date()
                    if (spec.get('valid_from') and query_date.isoformat()<spec['valid_from']) or (spec.get('valid_until') and query_date.isoformat()>spec['valid_until']):
                        error=ApiError('DATE_NOT_SUPPORTED',source=source)
                        return Document(source,DocumentStatus.UNSUPPORTED,None,None,error,0)
                    params={'ocid':ocid}
                    if request.on_date is not None:params['date']=request.on_date.isoformat()
                    if spec['grade'] is not None:params['character_skill_grade']=spec['grade']
                    raw=None
                    try:
                        raw,attempts,when=await self._fetch(spec['path'],params,source)
                        document=Document(source,DocumentStatus.FETCHED,raw,when,None,attempts)
                        if not document.structurally_valid:raise ApiError('INVALID_DOCUMENT',source=source,attempts=attempts)
                        if spec['grade'] is not None:
                            grade=raw.child('character_skill_grade')
                            if grade is not None and grade.kind is Kind.STRING and grade.value != spec['grade']:
                                raise ApiError('INVALID_DOCUMENT',source=source,attempts=attempts)
                        return document
                    except MapleError as error:
                        if not request.allow_partial or error.code in ('AUTHENTICATION_FAILED','PERMISSION_DENIED'):
                            raise
                        if not isinstance(error,ApiError):error=ApiError(error.code,source=source)
                        return Document(source,DocumentStatus.FETCH_FAILED,raw,self._clock(),error,error.attempts)
                children=[asyncio.create_task(collect(s)) for s in sorted(expanded,key=lambda s:s.value)]
                documents=await asyncio.gather(*children)
                diagnostics=[]
                if request.on_date is not None:
                    for doc in documents:
                        if doc.status is DocumentStatus.FETCHED:
                            d=doc.raw.child('date')
                            if d is not None and d.kind is Kind.STRING:
                                try:
                                    stamp=datetime.fromisoformat(d.value)
                                    reported=stamp.astimezone(ZoneInfo('Asia/Seoul')).date() if stamp.tzinfo else date.fromisoformat(d.value)
                                    if reported!=request.on_date:diagnostics.append('SOURCE_DATE_MISMATCH:'+doc.source.value)
                                except ValueError:diagnostics.append('INVALID_FIELD:'+doc.source.value+':/date')
                return CharacterSheet(ocid,{d.source:d for d in documents},requested_sources=requested,
                                      query_time=request.on_date,fetch_diagnostics=tuple(diagnostics))
        except TimeoutError:
            raise ApiError('DEADLINE_EXCEEDED') from None
        finally:
            for child in children:
                if not child.done():child.cancel()
            if children:await asyncio.gather(*children,return_exceptions=True)
            self._active.discard(task)

    async def _fetch(self,path,params,source):
        config=self._config
        for attempt in range(1,config.retries+2):
            delay=None
            try:
                async with self._semaphore:
                    await self._limiter.acquire()
                    async with asyncio.timeout(config.request_timeout):
                        async with self._http.stream('GET',path,params=params,headers={'x-nxopen-api-key':self._key,'Accept-Encoding':'identity'}) as response:
                            body=bytearray()
                            async for chunk in response.aiter_bytes():
                                body.extend(chunk)
                                if len(body)>config.max_body_bytes:raise ApiError('RESOURCE_LIMIT_EXCEEDED',source=source,attempts=attempt)
                            status=response.status_code
                            try:raw=parse_json(body.decode('utf-8'))
                            except (UnicodeError,MapleError) as error:
                                if 200<=status<300:raise ApiError(error.code if isinstance(error,MapleError) else 'INVALID_JSON',source=source,attempts=attempt) from None
                                raw=None
                            if 200<=status<300:
                                if raw is not None and raw.kind is Kind.OBJECT and raw.child('error') is None:return raw,attempt,self._clock()
                                if raw is None or raw.kind is not Kind.OBJECT:raise ApiError('INVALID_DOCUMENT',source=source,attempts=attempt)
                            error=_error(status,raw,source,attempt)
                            retry=response.headers.get('retry-after')
                            if retry:
                                try:
                                    seconds=float(retry)
                                    delay=max(0,seconds) if math.isfinite(seconds) else None
                                except ValueError:
                                    try:delay=max(0,(parsedate_to_datetime(retry)-self._clock()).total_seconds())
                                    except (ValueError,TypeError,OverflowError):pass
                            raise error
            except (httpx.TimeoutException,TimeoutError):error=ApiError('REQUEST_TIMEOUT',source=source,retryable=True,attempts=attempt)
            except httpx.TransportError as exc:
                cause=exc
                tls=False
                while cause is not None:
                    tls=tls or isinstance(cause,ssl.SSLCertVerificationError)
                    cause=cause.__cause__
                error=ApiError('TRANSPORT_ERROR',source=source,retryable=not tls,attempts=attempt)
            except ApiError as exc:error=exc
            if not error.retryable or attempt>config.retries:raise error from None
            jitter=min(3.0,0.3*2**min(attempt-1,10))*self._random()
            await self._sleep(max(jitter,delay or 0))
        raise AssertionError('unreachable')
