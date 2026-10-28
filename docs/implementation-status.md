# 구현 상태 — 수집·해석·비교

2026-10-28. Java 0.1.0-dev.1 / Python 0.1.0.dev1. 로컬 개발 패키지이며 외부 게시하지 않았다.

## 제공 기능

- Java21 CompletableFuture / Python>=3.12 asyncio 기반 전체·선택 조회, 이름→OCID, OCID 재사용, 현재/과거 날짜.
- 27개 leaf source와 SKILLS/UNION 그룹. 그룹 중복 제거. 스킬은 0/hyperpassive/hyperactive/5/6만 수집.
- 기본 5회/초 pacing, 동시4개, 요청10초/connect5초/전체60초, 최대2회 재시도, Retry-After와 jitter, 공유 limiter/transport/clock 주입. 종료·취소가 진행 요청과 후속 재시도에 전파됨.
- strict/partial 모드, 인증·권한 오류는 전체 실패. 부분 구조 오류에서는 파싱된 raw와 오류를 함께 보존. HTTP 오류 본문은 반환하지 않음.
- 모든 소스의 불변 raw, 스키마 진단, 공통 EntityView의 문자열/정확한 decimal/offset timestamp/객체/배열 접근. primary는 문서 root view다. 모든 preset 이름의 필드를 별도 보존하며 current로 대체하지 않음.
- 옵션 decoder: 명시적 stat/percent/초/레벨, 복합 대상, 보스 조건, 공격 확률, AP 비례, 9레벨 비례, 증가/감소. JSON Pointer와 Unicode code-point span 보존. 조건이나 수식을 평가하지 않음. 미지원 문구 UNKNOWN, 범위·자원 한도 문제 AMBIGUOUS.
- 순수 raw diff. 장비 슬롯·보고 스탯 이름·유니온 preset_no로 대응 가능한 배열을 비교. 중복 키 진단 후 index 비교. 명세 숫자 및 선언된 심볼/장비/보고 스탯 숫자 문자열에 exact delta. 미수집 자료를 삭제로 처리하지 않음.
- 공통 합성 fixture, 오류/재시도/취소 테스트, wheel/sdist/JAR/sources/Javadoc, 독립 소비자 검증, CI workflow.

## 날짜 범위

RING_EXCHANGE는 2025-08-21~2026-03-18, RING_RESERVE는 2026-03-19 이후다. 범위 밖은 HTTP 없이 `UNSUPPORTED`, `DATE_NOT_SUPPORTED`, attempts=0으로 반환한다. 이는 알려진 수집 범위이므로 strict도 문서 상태로 반환한다. 다른 실패를 이 상태로 추정하지 않는다.

전체 조회는 두 링 소스를 포함한 27개 결과를 제공하지만 현재 날짜에는 26개만 HTTP 수집한다. 이름 조회 포함 정상27호출, OCID 입력26호출이다. 재시도는 별도다. quality의 all_sources는 요청 범위 포함 여부이고, requested_complete/structure_valid는 모든 요청 문서가 FETCHED인지를 요구하므로 UNSUPPORTED가 있으면 false다. 호출자는 문서 상태와 함께 판단한다.

## 명시적 한계

- EntityView는 모든 원본 필드를 접근하는 공통 타입이다. 1,939개 명세 경로마다 별도 도메인 클래스/프로퍼티를 생성하지 않았다.
- 자연어 전체를 해석한다고 보장하지 않는다. UNKNOWN도 원문을 온전히 유지한다. `interpretation_complete`는 선택 문서의 수집 성공과 발견한 옵션 모두 KNOWN일 때만 true이며 문서 내 모든 자연어의 의미를 이해했다는 뜻이 아니다.
- 정상 부재 ABSENT 판정은 추가하지 않았다. 근거 없는 400→빈 자료 변환을 하지 않는다. 알려진 링 날짜 범위 외 endpoint의 과거 지원 여부는 upstream 오류를 그대로 구분한다.
- 관찰된 장비·칭호 프리셋 필드와 펫 공유 프리셋 배열을 schema-overrides로 지원한다. 공식 명세와의 차이는 OBSERVED_SCHEMA_EXTENSION으로 명시한다. 4직업 표본의 구조 경로1,419개를 대조했으며 미래 필드까지 보장하지 않는다.
- 과거 스킬을 현재 데이터로 대체하지 않는다. 전투력·합산·효과 적용·만료 판단·서버 통합은 범위 밖이다.
- MIT를 적용했다. 공개 Maven/PyPI 좌표·외부 배포는 미확정이다. CI는 작성했으며 원격 실행/게시하지 않았다.

실행 예제는 [사용 안내](agent-guide.md), 검증 증거는 [검증 기록](validation-2026-10-27.md)을 참고한다.


2026-10-28 확장: 조건·지속시간 문법, 조건을 잃지 않는 분할, 공통 옵션23/스키마2사례, 파서 규칙0.2. 자세한 수집·설치·배포 상태는 [후속 검증](validation-2026-10-28.md)에 기록했다. 기존에는1캐릭터, 후속에는 공개 랭킹4직업을 검증했다.
