# 사용 안내

Java21과 Python>=3.12는 독립 설치한다. 상대 언어 런타임이 필요하지 않다. 키는 생성자에 실행 시 전달하고 코드·fixture·로그에 저장하지 않는다.

## Python

```sh
cd python
python -m pip install -e '.[test]'
python -m pytest
uv build
```

```python
import asyncio, os
from datetime import date
from maple_library import MapleClient, CharacterRequest, Source, DocumentStatus

async def main():
    async with MapleClient(os.environ['MAPLE_OPEN_API_KEY']) as client:
        sheet = await client.get_character_selected(
            CharacterRequest(nickname='캐릭터명'), {Source.EQUIPMENT, Source.SYMBOLS})
        equipment = sheet.document(Source.EQUIPMENT)
        for field_name, value in equipment.presets().items():
            print(field_name, value.state)  # 원문/캐릭터 정보 출력은 소비자가 결정
        previous = await client.get_character_selected(
            CharacterRequest(ocid=sheet.ocid, on_date=date(2026, 10, 26), allow_partial=True),
            {Source.EQUIPMENT, Source.SYMBOLS})
        changes = previous.compare_to(sheet)  # after - before; 네트워크 없음
        print(len(changes.changes), changes.skipped_documents)

asyncio.run(main())
```

전체 조회는 `await client.get_character(request)`. `Source.SKILLS`, `Source.UNION`은 그룹이며 개별 source와 겹쳐도 한 번만 호출한다. 클라이언트를 재사용하고 같은 키의 여러 클라이언트에 `RateLimiter`를 공유한다. caller-owned HTTPX transport는 client 종료로 닫히지 않는다.

## Java

```sh
cd java
./gradlew test build
./gradlew publish  # java/build/repository 로컬 저장소만 사용
```

로컬 Maven 좌표 `org.whitedoggy:maple-library:0.1.0-dev.1`. 전이 의존성 Jackson core3.1.2가 필요하다.

```java
try (var client = new MapleClient(System.getenv("MAPLE_OPEN_API_KEY"))) {
    var sheet = client.getCharacterSelected(
        CharacterRequest.nickname("캐릭터명"),
        java.util.Set.of(Source.EQUIPMENT, Source.SYMBOLS)).join();
    var equipment = sheet.document(Source.EQUIPMENT);
    var presets = equipment.presets();
    var previous = client.getCharacterSelected(
        CharacterRequest.ocid(sheet.ocid()).on(java.time.LocalDate.of(2026, 10, 26)).partial(),
        java.util.Set.of(Source.EQUIPMENT, Source.SYMBOLS)).join();
    var diff = previous.compareTo(sheet);
}
```

모든 클래스는 `org.whitedoggy.maplelibrary`에 있다. 웹 요청 스레드에서는 join 대신 future를 연결한다. 전체 조회 `getCharacter(request)`, 취소는 반환 future의 cancel. custom RateLimiter/HttpTransport/Clock는 확장 생성자로 주입하며 외부 자원의 소유권은 호출자에게 있다.

## 원본과 해석

`Document.parse(source, json)`은 네트워크 없이 쓸 수 있다. `view`/`primary`는 root EntityView, `collection(name)`은 배열 FieldValue, `presets()`는 실제 필드 이름→FieldValue다. 이름을 임의로 숫자 프리셋으로 합치지 않는다. equipment(preset)/symbols는 전용 편의 접근이다. FieldValue의 MISSING/NULL/VALUE/INVALID를 먼저 확인한다. timestamp는 offset이 있는 값만 변환한다.

`decoded_options()` / `decodedOptions()`는 LocatedOption 목록이다. pointer는 원문 필드, start/end는 code point 기준이며 structured option_type/option_value의 span은 option_value 원문에 속한다. ruleId가 structured:로 시작하면 type sibling도 해석 근거다. ratio/확률/level 표현을 계산 결과로 사용하지 않는다.

`diagnostics()`는 UNKNOWN_FIELD/SCHEMA_TYPE_DIFFERENCE를 반환한다. 반환한 raw는 이 진단과 무관하게 보존된다. diff의 숫자 delta는 원본 수치 차이이며 캐릭터 성능 차이가 아니다.

## 실패와 실제 API 검증

기본 strict: 선택 HTTP 문서 오류 시 예외. partial: FETCH_FAILED 문서에 오류; 인증/권한은 전체 실패. 문법상 파싱 가능한 구조 오류 raw는 FETCH_FAILED.raw에서 확인한다. 취소는 일반 실패로 변환하지 않는다. 링의 명시적 지원 날짜 밖은 두 모드 모두 UNSUPPORTED로 반환한다.

라이브 검증 도구는 opt-in이며 테스트/CI가 키를 읽지 않는다.

```sh
python tools/live_smoke.py --env-file /절대경로/.env --nickname 캐릭터명 --date 2026-10-26
cd java
./gradlew liveSmoke -PkeyFile=/절대경로/.env -Pnickname=캐릭터명
```

키 파일의 MAPLE_OPEN_API_KEY를 메모리에서 읽고 상태/개수만 출력한다. 원본 캐릭터 응답은 저장하지 않는다. `tools/check_conformance.py`는 양쪽 테스트 실행 후 19개 diff fixture의 expected와 결과를 비교한다. 옵션 23사례는 각 언어 테스트가 같은 expected를 검증한다.
