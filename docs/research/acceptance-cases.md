# 첫 구현 수용 사례

현재는 테스트 설계이며 실행 결과가 아니다. 각 사례는 raw 입력/독립 expected/manifest ID로 만들고 양쪽 테스트 및 교차 exporter로 검증한다.

| ID | 입력·상황 | 기대 결과 |
|---|---|---|
| F001 | 심볼 STR=10, DEX=20 | 두 보고값 유지, 합산/주스탯 환산 없음 |
| F002 | 장비 옵션 missing/null/"0"/0/""/잘못된 숫자 | 상태 및 raw 타입 각각 구분; 잘못된 숫자는 INVALID |
| F003 | total=30, base=10, add=10 | total30 유지, 차액10을 보정하지 않음 |
| F004 | 프리셋1=[], 프리셋2 누락, current 장비 존재 | 빈/누락/current 독립, fallback 없음 |
| F005 | 최대 HP,MP+300 뒤 데미지10% | 복합 대상과 다음 효과 분리; 원문 span 보존 |
| F006 | 확률20%/데미지16%, AP LUK7%→DEX | 조건·expression만 기록, 평가 결과 없음 |
| F007 | 공유/일반 펫 모두 존재, 만료일 과거 | 두 자료와 flag/기간 보존, 효과 제거 없음 |
| F008 | 스킬 비용·패시브 괄호·직업 조건·모르는 절 | 명시 영역/조건 보존, 상시 적용 추론 없음 |
| F009 | 유니온 preset_no=11, HEXA 신규 필드 | 번호/unknown raw 보존, 알려진 필드 외 삭제 없음 |
| F010 | 4.50→4.5, 매우 큰 정수+소수 차이 | rawChanged와 semanticChanged 구분, exact delta |
| F011 | 양쪽 수집 범위 다름/한쪽 실패 | 해당 소스 skipped, 삭제 변화 없음 |
| F012 | 중복 슬롯·동명 장비·배열 순서 변경 | 임의 ID 매칭 없음, 중복 진단/index fallback |
| F013 | parserRulesVersion 다름, 동일 raw | 해석 변화 진단, 기본 의미 delta 생략 |
| F014 | supplementary Unicode 포함 문구 | Java/Python 동일 code-point span |
| H001 | 심볼만 이름/OCID 입력 | 각각 HTTP 2회/1회, basic 호출0 |
| H002 | UNION+UNION_RAIDER 선택 | 문서 중복0, 이름 입력5회 |
| H003 | 429 뒤 성공/00004/잘못된 JSON | 예산 내 retry/재시도 없음/구조 오류, permit 매시도 |
| H004 | 취소·deadline·strict/partial | 취소 삼키지 않음, 후속 retry 중단, 모드별 결과 |
| H005 | 과거 스킬6·챔피언 누락 | current 조회로 보충하지 않음 |
| P001 | source tree 밖 JAR/wheel 소비 | 독립 실행, 리소스 포함, 두 언어 런타임 상호 의존 없음 |

성능 탐색: 3개 장비 프리셋과 10개 유니온 프리셋을 포함한 합성 문서를 작은/중간/한도 인접 크기로 만들고 parse wall time, peak memory, 반복 typed 접근을 기록한다. 합격 시간은 측정 후 사용 목표에 따라 정하며 아직 임의 성능 약속을 하지 않는다.
