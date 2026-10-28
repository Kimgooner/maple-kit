# 버전과 두 언어 적합성

- contractVersion `0.1`은 이 설계의 식별자이며 릴리스가 아니다. parserRulesVersion은 어휘/조건 해석 규칙 버전, implementationVersion은 각 언어 패키지 버전이다.
- 첫 릴리스부터 두 구현에 동일 contractVersion과 parserRulesVersion을 명시한다. 패키지 패치 번호는 달라도 된다. 어느 한쪽만 지원한 기능은 지원표에 드러내며 전체 지원으로 표시하지 않는다.
- breaking model/상태/diff 변경은 공통 계약 버전 변경과 migration 설명이 필요하다. unknown 추가 필드는 raw에 보존되지만 typed 지원 추가로 해석 결과가 바뀌면 parserRulesVersion을 바꾼다.
- 합성 입력과 독립 검토 expected를 Java/Python 각각 검증한 뒤 결과를 교차 비교한다. 두 구현끼리 같은 실수를 해도 통과하지 않도록 expected 검증이 필수다.
- 테스트용 정규화 JSON에서 decimal은 문자열, 상태는 명시 enum, 객체 키는 code-point 정렬, 배열은 의미 순서, 시간은 고정 Clock 값을 사용한다. production 객체 직렬화와 분리된 테스트 전용 exporter를 둔다.
- 공개 저장·복원 API와 JSON Schema는 초기 범위가 아니다. API 사용 예제는 각 언어 실제 공개 타입으로 작성한다.
- 릴리스 검증: Java21 및 호환 상위 LTS, Python 최소3.12와 구현 당시 지원 stable 버전. source tree 밖에서 JAR/wheel 설치·리소스 접근·예제 실행 확인. 정확한 CI matrix와 도구 patch 버전은 빌드 설정 단계에 고정한다.

2026-10-28: contractVersion0.1 유지, parserRulesVersion0.2로 올렸다. 조건 해석·분할과 관찰된 스키마 확장의 변화다. 서로 다른 파서 규칙의 시트 비교는 기존대로 raw 변경을 반환하되 수치 delta를 생략한다. 후보 패키지는 Java0.1.0-dev.1/Python0.1.0.dev1이다.
