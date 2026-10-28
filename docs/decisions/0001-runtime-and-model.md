# ADR 0001 — 독립 Java/Python 구현과 원본 중심 모델

- 결정일: 2026-10-27
- 상태: 설계 채택, 구현/벤치마크 전

## 기술 선택

| 영역 | 결정 | 이유·대안 |
|---|---|---|
| Java | Java21, Gradle Kotlin DSL `java-library`, 공개 package `org.whitedoggy.maplelibrary` | 기존 소비자의 Java21과 일치. Spring Boot/Reactor/Lombok 의존 없음 |
| Java HTTP | JDK HttpClient.sendAsync | 추가 HTTP 런타임 없이 비동기·연결 재사용. 외부 프레임워크 없이 주입 transport 제공 |
| Java JSON | 내부 Jackson3 core streaming parser | 기존 프로젝트와 같은 major. 원본 숫자 token과 duplicate key 탐지, 불변 RawValue 직접 생성. databind 전체 가변 트리를 또 유지하지 않음 |
| Java 모델 | records + sealed interfaces + 방어적 컬렉션 복사 | null/missing 상태 명시, 내부 가변 컬렉션 누출 차단 |
| Python | CPython >=3.12, src layout, import `maple_library` | 3.12를 최소 호환선으로 선택. asyncio 기반으로 한정; Trio 지원은 초기 범위 아님 |
| Python HTTP | HTTPX AsyncClient | async 연결 pool·transport 주입. requests+thread 우회 대신 명시적 비동기 API |
| Python 모델 | dataclasses(frozen=True, slots=True), tuple, 새 dict를 감싼 MappingProxyType | Pydantic 강제 의존/자동 coercion 없이 상태·불변성을 직접 통제. frozen만으로 중첩 불변성이 확보되지는 않음 |
| Python JSON | 표준 json + parse_int/parse_float token hook + object_pairs_hook | 원본 숫자 lexeme/중복 키 감지 후 RawValue 생성. NaN/Infinity 거절 |
| 수치 | BigDecimal/BigInteger, Decimal/int | float 중간 변환 및 기본 context 반올림 방지 |
| Python 빌드 | setuptools PEP517 + pyproject, wheel/sdist | 단순 패키지와 리소스 포함 검증. 런타임에 빌드 도구 필요 없음 |
| 테스트 | Java JUnit Jupiter; Python pytest + pytest-asyncio; 공통 합성 JSON | 라이브 API 없이 parser/HTTP stub/두 언어 계약 검증 |
| 공개명 | Java artifact `maple-library`, Python distribution `maple-library`는 작업용 제안 | registry 이름 확보·Maven group 소유권·라이선스는 미확정. import/package는 위 이름으로 내부 구현 시작 가능 |

정확한 dependency patch 버전은 빌드 설정 시 공식 릴리스와 호환성을 확인해 고정한다. dynamic/latest 버전은 사용하지 않는다. Python 런타임 필수 외부 의존성은 HTTPX로 시작하고 모델 검증용 별도 런타임은 추가하지 않는다. 공통 계약은 코드 생성 스키마로 확장하지 않고 양쪽의 관용적 API와 fixture로 유지한다.

## 모델 구조 선택

A. DTO에 알려진 필드만 복사: unknown 소실 위험으로 기각.
B. 원본 JSON + 전체 DTO + 별도 unknown subtree: 정보를 보존하지만 대형 장비 프리셋을 중복 저장하므로 기각.
C. immutable RawValue 트리 한 벌 + typed scalar view + RawRef: 채택. 파싱 1회, 큰 subtree 재복사 없이 접근. 캐시가 필요하면 내부에만 두고 외부 의미는 불변.

이 비교는 코드 경로와 소유 구조에 따른 설계 판단이다. 메모리/시간 우수성을 측정한 결과가 아니다. 최소 수직 구현에서 3개 장비 프리셋·심볼·유니온·긴 옵션 합성 사례로 parse 시간/peak memory/반복 접근 비용을 측정하고 비용이 크면 구조를 재검토한다. JDK/Python의 객체 비용이 다르므로 언어 간 성능 수치를 단순 비교하지 않는다.

## 공식 근거 (확인일 2026-10-27)

- [JDK21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html): sendAsync의 CompletableFuture와 client 재사용·종료.
- [Jackson3 migration](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md): Java17 이상 및 tools.jackson 패키지. Java21과 기준 호환.
- [HTTPX async](https://www.python-httpx.org/async/): AsyncClient 및 async 수명 관리.
- [Python 버전 지원표](https://devguide.python.org/versions/): 최소3.12 선택 시 유지보수 기간 확인.
- [Python Decimal](https://docs.python.org/3.12/library/decimal.html): 문자열 기반 정확 표현과 context 정밀도 주의.

## 남은 결정

배포 좌표 소유권·라이선스·공개 저장소는 사용자 결정이 필요하지만 로컬 구현을 막지 않는다. 실제 dependency resolution, 패키지 빌드와 endpoint별 실응답 검증은 아직 하지 않았다.
