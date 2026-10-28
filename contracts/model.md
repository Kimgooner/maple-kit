# 공통 모델 계약 v0.1 설계

상태: 개발 구현 계약. 실제 API와 범위는 docs/agent-guide.md 및 docs/implementation-status.md가 기준이다. 전용 클래스 전체·ABSENT·PARTIAL 해석 상태 등 설계 후보와 구현된 항목은 구분한다.

## 요청과 시트

- CharacterRequest: `target = Nickname(nonblank) | Ocid(nonblank)` 중 하나, `queryTime = Current | OnDate`, `failureMode = STRICT | ALLOW_PARTIAL`(기본 STRICT). 프리셋/직업/보정값은 입력하지 않는다.
- Java `getCharacter(request)` / `getCharacterSelected(request, sources)` → CompletableFuture<CharacterSheet>. Python `await get_character(request)` / `await get_character_selected(request, sources)` → CharacterSheet. sources는 null/빈 집합 거절.
- CharacterSheet: 식별 target·resolved OCID, queryTime, requestedSources, expandedDocumentKeys, 문서 결과, typed 소스 view, quality, contractVersion/parserRulesVersion/implementationVersion.
- 원본 보유는 문서별 immutable RawValue 트리 한 벌. typed view는 필요한 scalar와 RawRef(documentKey, JSON Pointer)를 갖고 큰 하위 트리를 복사하지 않는다. RawValue 전체는 항상 접근 가능; 별도 unknown 복사본을 만들지 않는다.
- API 키는 client에만 존재. OCID는 사용자 지정/조회 식별로 제공하되 repr/toString·기본 로그에는 노출하지 않는다.

## 세 종류의 상태를 분리

| 층 | 상태 | 의미 |
|---|---|---|
| DocumentResult | NOT_REQUESTED, FETCHED, ABSENT, FETCH_FAILED, UNSUPPORTED | HTTP 수집 상태. ABSENT는 문서별 근거로 확인된 해당 없음만 사용 |
| FieldValue<T> | MISSING, NULL, VALUE, INVALID | VALUE는 빈 문자열/빈 배열/0을 포함. INVALID는 원문 RawRef와 진단 보유 |
| Interpretation | KNOWN, PARTIAL, UNKNOWN, AMBIGUOUS | 해석 품질. UNKNOWN이어도 원본 수집은 성공일 수 있음 |

FETCHED 문서의 필수 식별 구조가 잘못되면 문서 validation 오류를 별도로 기록한다. strict에서 필수 구조 오류는 실패, partial에서는 raw와 오류를 반환한다. 선택한 모든 문서는 기본적으로 필수이며 정상 ABSENT는 실패가 아니다. quality는 요청 범위 수집 완료/요청 범위 구조 유효/전체 지원 범위 포함/해석 완전성을 별개로 표시한다. 정상적인 0을 ABSENT로 바꾸지 않는다.

## 원문·숫자·시간

- RawValue = Object(문자열 키), Array(순서 유지), String, Number(원래 숫자 토큰), Boolean, Null. 객체 키 중복 JSON은 오류; 문자열/숫자 타입은 그대로 보존. 원문 보존은 필드값·숫자 토큰 기준이며 HTTP JSON의 공백/escape 표기까지 byte-for-byte 재현하는 계약은 아니다.
- Decimal: Java BigDecimal, Python Decimal. 원문 문자열에서 직접 변환하며 float/double 중간 변환 금지. 숫자 문자열은 문서 필드 문법에서만 해석한다; 임의 텍스트 전체에서 마지막 숫자를 고르지 않는다.
- 비교 숫자 정규화는 지수 없는 문자열, 불필요한 소수부 0 제거, -0→0. 원본 `4.50`, `4.5`, 문자열/JSON number 차이는 raw에 남는다. 금액·스탯 큰 정수는 Java BigInteger/Python int; 범위가 정해진 레벨·번호만 checked int.
- diff 뺄셈은 정확 연산. Python 전역 Decimal context(기본 정밀도)에 의존하지 않고 두 피연산자를 공통 scale의 정수 계수로 맞춰 뺀다. 반올림/허용 오차 없음. NaN/Infinity 거절.
- OnDate는 KST 달력 날짜. 응답 offset timestamp는 offset과 원문을 보존하고 비교할 때 instant를 사용. 날짜만 있는 값은 날짜로 유지; timezone 없는 timestamp는 임의 KST 부착 없이 INVALID 또는 문서별 명시 규칙.
- fetchedAt은 주입 Clock에서 얻은 UTC 시각이며 문서별로 기록. source date 불일치는 진단, 자동 재조회/대체 없음.
- 파서의 Unicode 정규화는 별도 interpretation view에만 적용. 원문 변경 금지. source span은 Unicode code point 반열린 구간 [start,end); Java UTF-16 인덱스를 그대로 공개하지 않는다.

## 옵션

Option: 원문 RawRef, spans, interpretation, targets[], value(FieldValue<Decimal>), unit(FLAT/PERCENT/SECONDS/LEVEL/UNKNOWN), scope(명시된 대상/문구), conditions[], expression, ruleId.

- `보스 몬스터 공격 시 데미지 +30%`: BOSS_DAMAGE, 30, PERCENT, 보스 공격 조건을 보존.
- `공격 시 20% 확률로 데미지 16% 증가`: chance=20 PERCENT 및 effect=16 PERCENT. 3.2로 평균화하지 않음.
- `AP를 직접 투자한 LUK의 7% 만큼 DEX 증가`: expression=PROPORTIONAL_TO_STAT, basis=INVESTED_AP.LUK, ratio=7 PERCENT, target=DEX. 평가값 없음.
- 복합 대상 HP/MP는 targets=[HP,MP]와 한 원문으로 보존; 합산용 독립 효과로 무조건 복제하지 않음.
- 조건 해석이 실패하면 원문 조건을 UNKNOWN으로 남긴다. 알려진 숫자를 무조건 무조건부 효과로 승격하지 않음.

## 프리셋·엔티티

시스템별 current, currentPresetNumber(FieldValue), presets(실제 번호→FieldValue), 추가 폼·공유 슬롯을 별도 보존한다. 숫자 상한을 3/5/10으로 전역 고정하지 않는다. 알려진 프리셋 배열에서 신규 번호는 보존하고, 신규 이름의 필드는 RawValue로 남긴 뒤 typed 지원 진단을 낸다. 프리셋 누락 시 current로 fallback하지 않는다.

Item은 슬롯/이름/아이콘 및 base/add/etc/starforce/total/exceptional, potential/additional/soul 줄, 기간, 기타 raw를 제공한다. Symbol은 이름/레벨/성장/STR·DEX·INT·LUK·HP 등 보고 필드를 개별 제공한다. 스킬은 등급/레벨/이름/설명/효과를 유지하며 이름을 유일 ID로 가정하지 않는다. 전체 필드 후보는 spec-inventory.json에 있다.
