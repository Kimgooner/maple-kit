# 2026-10-27 — Java/Python 캐릭터 라이브러리 구현 계획

- Date: 2026-10-27
- Issue: None
- Status: 2026-10-28 수집·옵션·실응답 스키마 확장 및 로컬 배포 후보 검증; 저장소/라이선스/공개 게시 설정 대기

## Goal

Java와 Python에서 독립 설치 가능한 캐릭터 데이터 라이브러리를 각각 제공한다. 같은 API 자료를 원문·프리셋·옵션·진단까지 보존하고 네트워크 없는 데이터 비교를 제공한다. 두 구현의 의미는 공통 계약과 검토된 fixture로 일치시킨다.

## Non-goals

전투력/총 스탯 계산, 프리셋 추천, 효과 적용·만료 판단, DPM·Shapley, MapleDelta 코드·adapter·UI 수정, 어센트 잔차 연구, 공개 시트 저장·복원 API. 사용자 요청에 따라 로컬 구현·빌드·의존성 설치를 진행한다. Git 저장소 생성과 외부 게시는 이번 범위 밖이다.

## Context / Constraints

- 상세 요구사항은 docs/project.md가 연결하는 기존 계획을 따른다. 새로운 두 언어 범위와 전담 경계는 이 계획이 기준이다.
- Java 21 / Python >=3.12. 런타임·모델·의존성 방향은 docs/decisions/0001-runtime-and-model.md에 기록했다. 양쪽은 상대 언어 런타임에 의존하지 않는다.
- 공개 모델은 불변이고 외부 프레임워크 타입을 노출하지 않는다. Python에서도 중첩 컬렉션 불변성을 확인한다.
- 전체/선택 조회를 구분하고 소스별 필수 문서만 호출한다. 스킬 등급 제외 범위를 유지한다.
- 모든 HTTP 시도/재시도에 키 단위 공유 제한기를 연결한다. 제한기·Clock·transport 주입 지점을 제공하고 자원 소유/종료·취소 계약을 정한다.
- 실제 API 한도·필드·날짜 지원은 구현 전 공식 문서와 허용된 표본으로 확인한다. 기존 문서의 호출 수를 현재 사실로 사용하지 않는다.
- 기존 웹 프로젝트의 미커밋 파일은 참고만 하고 수정하지 않는다. 공통 fixture에는 비밀키·연구 원본을 포함하지 않는다.

## Approach (Checklist)

- [x] 초기 준비: 기존 계획을 두 언어 기준으로 개정하고 전담 경계·폴더·계약 초안을 작성.
- [ ] **Step 0: Recon** (Inspect existing code, locate files)
  - [x] 공식 명세 기반 endpoint/필드/날짜/프리셋/스킬 등급/소스 의존성/호출 수를 contracts/coverage.md 및 spec-inventory.json에 기록. 실응답과의 대조는 미완료.
  - [x] 주요 parser 진입점과 계산 결합 메소드를 docs/research/parser-boundary.md에 분류. 표에 없는 helper는 이식 전 추가 검토.
  - [ ] 실제 응답 구조 대조 및 대표 합성 입력의 메모리·성능 측정. 모델 소유 구조 비교와 수용 사례 설계는 완료.
  - [x] Python 최소 버전, HTTP·JSON·모델, 내부 package/import명, 수치/시각/오류 계약 채택. dependency patch·공개 배포 좌표는 후속.
- [ ] **Step 1: Implementation** (Code changes, file paths)
  - 1A: contracts에 모델·오류·비교·버전 계약 확정. fixtures에 합성 입력과 수동 검토 기대값 작성.
  - [x] 1B: Gradle/pyproject와 독립 테스트·개발 패키지 마련. HTTP 의존성은 해당 구현 단계에 추가.
  - [x] 1C: HTTP 없는 원본→불변 모델→diff의 심볼·장비 최소 경로 구현. 전체 typed 모델/옵션 해석은 후속. 공통15사례와 독립 소비자 검증.
  - 1D: 장비/캐시/펫/안드로이드, 어빌리티/하이퍼, 유니온/링크, 지원 스킬/V/HEXA, 성향/기타/특수 장비 순으로 커버리지 확대. 각 묶음마다 양쪽 검증을 통과.
  - 1E: 이름→OCID, 전체/선택 수집, strict/partial, 공유 제한·재시도·취소·deadline·자원 수명 구현. 소스 의존 계획은 1A에서 확정해 파서와 일치시킨다.
  - 1F: 언어별 설치·현재/과거·선택 조회·프리셋 탐색·비교·실패 예제 및 agent-guide 작성. 오류 안내는 코드와 일치 검증.
- [ ] **Step 2: Tests** (Unit tests, manual verification steps)
  - 두 구현이 같은 사례의 독립 기대 결과를 통과하고 테스트용 정규화 결과도 일치하는지 검증.
  - 부분 선택의 미선택 HTTP 호출 0, 의존 문서 중복 0, 빈 선택 거절, 취소 후 후속 retry 중단 확인.
  - 날짜/Clock·429/timeout·인증/스키마 오류·strict/partial·unknown/null/0·불변성 검증.
  - 소스 누락을 삭제로 오판하지 않는 diff, 순서·동명 장비·현재 프리셋 변경·호환 버전 검증.
  - 깨끗한 소비자 환경에 Java JAR 및 Python wheel을 설치해 예제 실행. sdist에서 wheel 빌드, 리소스 포함, 상대 저장소·소스 트리 없는 실행 확인.
- [ ] **Step 3: Rollout / Rollback** (Feature flags, migration steps)
  - 공통 계약 버전과 각 패키지 버전의 지원표 및 커버리지 공개. Java sources/Javadoc와 Python wheel/sdist 준비.
  - 배포 좌표·라이선스 확정 후 로컬 배포 검증. 실제 외부 게시는 별도 실행 작업.
  - MapleDelta 담당자에게 Java API·소스 범위·공유 제한기 연결·진단·고정 fixture 전달. 서버 전환/골든 검증은 해당 담당자가 수행.

## Validation

- **Commands to run:** java에서 `./gradlew test build`, python에서 `python -m pytest`, `uv build` 또는 `python -m build`. 루트에서 `python tools/check_conformance.py`. 설치 예제는 소스 트리 밖에서 실행한다.
- **Expected output:** 현재 단계는 첫 오프라인 구현이며 전체 계약 완료가 아니다. 최종 구현은 공통 fixture 기대 결과와 양쪽 결과 일치, 문서별 커버리지 명시, 독립 패키지 설치 예제 통과, 계산 정책 유입 없음. 미실행·미지원은 통과로 표시하지 않는다.

## Risks & Rollback

- **Risks:** 언어 간 숫자/날짜/null 의미 차이, 요구사항 중복 문서의 불일치, 파서에 계산 로직 혼입, 전체 조회 비용, 패키지 리소스 누락, 지원 범위 차이.
- **Rollback steps:** 계약과 fixture 변경을 함께 관리하고 각 패키지는 이전 릴리스로 복귀 가능하게 유지한다. 공개 버전을 덮어쓰지 않는다. MapleDelta 전환은 별도 작업으로 분리한다. 현재 문서 스캐폴드는 이번 생성 파일만 되돌릴 수 있다.

## Open Questions

- 공개 Maven/PyPI 이름 소유권·라이선스·배포 대상은 미확정. 로컬 구현은 채택한 package/import명으로 진행 가능.
- 공식 명세와 실제 응답 차이: 칭호 프리셋·HEXA 추가 코어 등은 raw로 보존하되 typed 모델 반영 전 fixture 확인.
- 메모리/시간 비용은 최소 수직 구현 후 측정한다. 현재는 성능 수치를 주장하지 않는다.

## 사전 조사 산출물 (2026-10-27)

- [파서 책임 분류](../../docs/research/parser-boundary.md), [기술 선택](../../docs/decisions/0001-runtime-and-model.md)
- [공통 계약 색인](../../contracts/README.md), [수용 사례](../../docs/research/acceptance-cases.md)
- API 키 사용/캐릭터 자료 복사/서버 코드 수정 없이 공개 명세를 조사했다. 전체 목표는 OCID 입력27문서, 이름 입력28호출(재시도 제외)이다.
- Step0 전체 체크는 실응답 구조 대조와 성능 측정이 남아 미완료로 유지한다. 다음 구현은 공통 synthetic fixture와 심볼/장비의 HTTP 없는 양쪽 수직 경로다.

## 첫 구현 산출물

실행 가능한 API·남은 범위는 [구현 상태](../../docs/implementation-status.md), 실행 절차는 [사용 안내](../../docs/agent-guide.md)에 기록한다.


## 수집·전체 소스 구현 결과 (2026-10-27)

- [x] 실제 응답 구조 대조. Java/Python 현재26문서와 날짜가 유효한 과거 링·심볼 조회 확인.
- [x] 공통 registry 기반27소스와 SKILLS/UNION 그룹, 일반 typed EntityView·프리셋·schema 진단.
- [x] 보수적 옵션 decoder, 원문 pointer/span, 확률/AP/레벨 조건·표현식 보존.
- [x] HTTP 이름/OCID·현재/과거·전체/선택·strict/partial·limiter/retry/deadline/취소.
- [x] 공통 diff19/옵션8사례 및 수집 stub 검증, 독립 JAR/wheel 소비자.
- [x] 로컬 패키징·실행 안내·합성 성능 기준·키 없는 CI workflow.

구현 결정: 모든 필드에 전용 클래스를 늘리는 대신 공유 불변 raw + EntityView 접근을 채택했다. 일반 view가 모든 원본/프리셋을 보존하며 명세 차이는 진단한다. 임의 자연어 전체 해석은 보장하지 않고 UNKNOWN을 유지한다. 정상 부재를 확정할 근거 없는 ABSENT 변환은 하지 않는다. 이전 체크리스트의 후속 상태는 docs/implementation-status.md가 최신 기준이다. 외부 게시와 웹 서버 통합은 이번 범위 밖으로 유지한다.

2026-10-28 후속: 4직업 현재104/과거12문서 확인, 옵션23·스키마2 공통 사례, 관찰된 schema overlay, 규칙0.2, 모노레포 CI/아티팩트 점검을 추가했다. 저장소 생성은 사용자가 담당하며 라이선스·외부 게시는 미확정이다. docs/validation-2026-10-28.md가 최신 검증 기록이다.
