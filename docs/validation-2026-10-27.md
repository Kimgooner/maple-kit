# 검증 기록 — 2026-10-27

## 자동 검증

- Java21: JUnit 41개 통과. Gradle test/build, JAR/sources/Javadoc, 로컬 Maven publish 성공.
- Python3.14.6: pytest 52개 통과. 최소 지원3.12.13에도 wheel 설치 후 같은52개 통과.
- 공통 diff19사례: 각 언어가 독립 expected를 검증하고 `tools/check_conformance.py` 교차 비교 성공.
- 공통 옵션8사례: 같은 JSON expected로 stat/colon/복합 대상/보스/확률/AP/9레벨/UNKNOWN·AMBIGUOUS 및 Unicode span 검증.
- 수집 stub: 전체/선택/그룹 중복 제거, 지정 스킬 등급, strict/partial, 인증, 429 재시도별 permit, 날짜 전달, 오류 본문 비노출, 구조 오류 raw 보존, deadline/취소, 크기 제한. Python은 borrowed transport 종료 소유권도 검증.
- Python wheel은 sdist를 거쳐 빌드했다. 소스 트리 밖 Python3.12 환경에 설치 후 테스트 실행. Java 외부 Gradle 소비자에 로컬 Maven artifact를 설치해 예제 실행 성공.
- 원본 웹 프로젝트25개 참고 파일 SHA-256은 사전 조사 manifest와 동일하다.

## 실제 API

사용자 허가에 따라 원본 `.env`의 MAPLE_OPEN_API_KEY를 메모리에서 읽어 실행했다. 키를 소스·fixture·보고서에 복사하지 않았다. 실제 캐릭터 원본을 저장하지 않았다.

- Python: 현재26문서 FETCHED, RING_EXCHANGE는 날짜 범위 밖 UNSUPPORTED(0호출). 과거2026-10-26 SYMBOLS 성공, 날짜 불일치 진단 없음.
- Java: 동일 현재26문서 성공, RING_EXCHANGE UNSUPPORTED. 2026-03-18 RING_EXCHANGE를 별도 조회해 FETCHED 확인.
- 최초 Python 확인에서는 현재 RING_EXCHANGE가400이었다. 공식 명세의 종료 날짜를 반영해 재검증했다. 오류를 빈 문서로 바꾸지 않았다.
- 현재 전체 조회는 이름 ID1회 + 유효 문서26회. 27개 leaf source 결과 중 날짜 비지원1개는 네트워크 호출하지 않는다.
- 장비346개 경로 UNKNOWN_FIELD(반복 원소 포함), 펫1개 SCHEMA_TYPE_DIFFERENCE. 장비의 새 scroll_upgradeable_count/프리셋 slot·flag/칭호 프리셋과 펫 공유 프리셋 타입 차이로 확인했다. 알려지지 않은 실제 응답 필드도 raw에 남는다.
- [Python 비식별 상태 보고서](../reports/live-python-2026-10-27.json). Java 결과는 본 기록 및 liveSmoke 재현 도구에 남겼다.

실제 확인은 한 캐릭터의 표본이다. 모든 직업·시점의 해석 완료를 주장하지 않는다. 현재 표본의 interpretation_complete=false는 보수적 decoder가 모르는 문구를 보존하기 때문이다.

## 합성 성능 기준

`tools/benchmark.py`: 1,000개 합성 장비의 parse+옵션+self-diff를10회 실행. Python3.14.6에서0.3203초, tracemalloc peak2,544,806bytes. CPU/런타임별 일반 성능이나 HTTP 응답 시간의 보장이 아니다. [측정 출력](../reports/benchmark-python.json).

Java21은 같은 규모에서0.2077초, 종료 시 heap 점유11,606,888bytes였다. `./gradlew benchmark`로 합성 baseline을 재현한다. 출력 heap_used는 종료 시 JVM 점유량이며 peak allocation이 아니다.

## 실행하지 않은 것

원격 CI 실행, 공개 배포, 소비자 웹 서버 교체, 전체 직업/날짜에 대한 API 전수 검증. CI workflow는 라이브 키 없이 합성 테스트만 실행하도록 작성했다.
