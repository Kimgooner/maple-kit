# 데이터 비교 계약 v0.1

`before.compareTo(after)` / `before.compare_to(after)`는 네트워크 없는 순수 비교다. 다른 OCID 또는 호환되지 않는 contract major는 오류. 닉네임 변경은 허용한다. 결과에는 comparedDocuments, skippedDocuments(reason), changes가 있다.

- 양쪽 FETCHED이며 구조상 비교 가능한 문서만 비교한다. NOT_REQUESTED/FETCH_FAILED/UNSUPPORTED는 추가·삭제가 아니라 skip. ABSENT↔FETCHED는 문서별 ABSENT 의미가 확인된 경우에만 추가·삭제.
- 양쪽 문서가 유효하면 필드 MISSING/NULL/VALUE 변화는 상태 변경으로 기록한다. INVALID 필드는 원문 변화만 기록하고 수치 차이를 내지 않는다.
- change: documentKey, preset/form/slot 또는 source JSON Pointer, kind, before, after, optional exact numeric delta, interpretationChanged. 단위가 같은 숫자만 after-before 제공한다.
- rawChanged와 semanticChanged를 구분한다. `4.50`→`4.5`는 raw 변경이지만 숫자 delta=0; 타입 string→number 변화도 raw 변경이다. fetchedAt·수집 시도 수·라이브러리 버전 자체는 게임 데이터 changes에서 제외한다.
- parserRulesVersion이 다르면 raw 변화는 비교 가능하지만 해석값 delta는 기본 생략하고 PARSER_VERSION_DIFFERENCE 진단을 낸다. 이전 시트를 현재 규칙으로 몰래 다시 해석하지 않는다.
- 객체 키 순서는 무시한다. 배열 순서는 기본적으로 의미가 있다. 장비의 유일 (폼, 프리셋, 슬롯), 유니온의 유일 preset_no처럼 명시된 키가 있는 배열만 키로 매칭한다. 심볼·스킬은 안정 ID/유일 키가 증명되지 않으면 index 기반으로 비교한다.
- 중복 슬롯/키는 임의 덮어쓰기하지 않는다. DUPLICATE_ENTITY_KEY 진단과 그 배열의 index 비교로 전환한다. 이름만으로 동일 아이템·이동을 추정하지 않는다.
- changes는 documentKey ASCII, preset 숫자, pointer 토큰 Unicode code-point 순으로 결정적으로 정렬한다. 원본 배열은 재정렬하지 않는다. 정렬 방식은 tests에 명시 사례로 고정한다.
- 데이터 차이는 전투력 기여도나 성장 원인 분석이 아니다. API 보고 전투력 delta는 reportedStat 변화로만 제공한다.

## 개발 구현 확장 (2026-10-27)

양쪽에 문서가 없는 소스는 skipped에 나열하지 않는다. 어느 한쪽에만 있는 소스는 상태로 비교 제외된다. 보고 스탯 final_stat은 stat_name이 유일할 때 대응하며 stat_value만 수치 비교한다. 명세의 number/integer 경로도 delta 대상이다. 모르는 숫자 문자열은 해석하지 않는다. 유니온의 현재/개별 프리셋은 각각 실제 필드 경로 아래에서 비교한다. 기타 배열은 안정적인 키가 확인되지 않으면 순서 비교한다.

이 변경은 외부 출시 전0.1 개발 패키지 내 확장이며 이전 개발 산출물의 diff/skipped 목록과 다를 수 있다. 두 언어 패키지와 fixture를 함께 갱신해야 한다.
