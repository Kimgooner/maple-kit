# API 커버리지 조사 — 2026-10-27

공식 공개 명세를 다운로드·파싱한 결과다. 실제 캐릭터 API 호출은 하지 않았다. 전 필드/파라미터/원문 설명/명세 해시는 [spec-inventory.json](spec-inventory.json)에 있다. **지원 목표이며 구현 완료 표가 아니다.**

## 수집 경계

캐릭터 구성에 필요한 아래 문서와 유니온 4종을 대상으로 한다. popularity·beauty-equipment·dojang은 초기 범위 밖이다. 캐시·안드로이드 문서 안의 외형은 보존한다. 계정 캐릭터 목록·업적·Open ID·랭킹·연무장·길드 API도 수집하지 않는다.

| endpoint (`/maplestory/v1/` 이하) | 날짜 | 1회 조회 문서 수 | 상태 |
|---|---|---:|---|
| `id` | 미지원 | 1 | 이름 해석 |
| `character/basic` | 선택 date / KST | 1 | 구현 예정 |
| `character/stat` | 선택 date / KST | 1 | 구현 예정 |
| `character/hyper-stat` | 선택 date / KST | 1 | 구현 예정 |
| `character/propensity` | 선택 date / KST | 1 | 구현 예정 |
| `character/ability` | 선택 date / KST | 1 | 구현 예정 |
| `character/item-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `character/cashitem-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `character/symbol-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `character/set-effect` | 선택 date / KST | 1 | 구현 예정 |
| `character/android-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `character/pet-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `character/skill` | 선택 date / KST | 5 | 구현 예정 |
| `character/link-skill` | 선택 date / KST | 1 | 구현 예정 |
| `character/vmatrix` | 선택 date / KST | 1 | 구현 예정 |
| `character/hexamatrix` | 선택 date / KST | 1 | 구현 예정 |
| `character/hexamatrix-stat` | 선택 date / KST | 1 | 구현 예정 |
| `character/other-stat` | 선택 date / KST | 1 | 구현 예정 |
| `character/ring-exchange-skill-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `character/ring-reserve-skill-equipment` | 선택 date / KST | 1 | 구현 예정 |
| `user/union` | 선택 date / KST | 1 | 구현 예정 |
| `user/union-raider` | 선택 date / KST | 1 | 구현 예정 |
| `user/union-artifact` | 선택 date / KST | 1 | 구현 예정 |
| `user/union-champion` | 선택 date / KST | 1 | 구현 예정 |

선택 소스는 문서 단위 enum을 기본으로 하고 `SKILLS`는 위 5등급, `UNION`은 union/raider/artifact/champion 4문서의 편의 그룹이다. 하위 소스 개별 선택도 허용하며 그룹·하위 소스 중복은 문서 키로 제거한다. 링크·V·HEXA는 별도 소스다.

계산을 하지 않으므로 심볼 조회에 basic이나 장비가 필요하지 않다. 이름 입력이면 OCID 해석 1회가 유일한 공통 의존성이다. OCID 직접 입력은 이를 생략한다. 모든 문서 키는 endpoint+grade+query time으로 구분한다.

목록 기준 전체 조회는 **27문서 + 이름 해석 1회 = 28호출**, OCID 입력이면 27호출이다. 기존 18문서에서 android/link/V/union/반지 2종의 6문서와 스킬 추가 3등급이 늘어 **18+6+3=27문서**다. 재시도는 별도이며 최악의 시도 수는 각 문서의 retry budget에 따라 늘어난다. 심볼 단독은 이름 입력 2회/OCID 입력 1회, 유니온 그룹은 5회/4회다.

## 프리셋과 명세 차이

- 장비: current `item_equipment`, `item_equipment_preset_1..3`; title은 독립 보존. 기존 코드의 `title_preset1..3`은 이 명세에 없음. 추정 fallback 금지.
- 캐시: base/additional 양쪽 1..3, 월드 공유 master label 및 활성 flag 보존.
- 어빌리티·하이퍼·안드로이드: 1..3과 현재 번호.
- 링크: 장착·소유 각각 1..3, 이 명세에 현재 preset 번호 필드는 없음. 임의 생성하지 않음.
- V: current와 1..5. HEXA: 코어 원문, 스탯 current/preset 1..3. 기존 코드의 current 4..6은 명세 밖 확장으로 보존.
- 펫: 일반/공유 양쪽, `world_share_pet_equipment_preset`과 활성 flag. 한쪽만 선택해 삭제하지 않음.
- 유니온: `union_state_stat_preset`의 실제 번호를 읽음. legacy raider preset 1..5도 별도 보존. 현재 코드의 최대 10을 타입 상한으로 사용하지 않음.

## 날짜·호출 정책

각 endpoint description에 시작일이 있으므로 inventory의 설명을 개별 적용한다. info에는 평균 15분 반영과 전일 데이터의 다음날 02:00 준비가 명시돼 있다. current는 date 생략, 과거는 지정 날짜이며 다른 시점으로 자동 대체하지 않는다. 아직 경계일 실제 응답은 검증하지 않았다.

## 남은 검증

1. endpoint별 실제 응답과 명세의 타입/null/신규 필드 차이 확인. 기존 fixture는 키·식별정보를 출력하지 않는 구조 검사부터 수행.
2. 필드마다 typed accessor / raw-only / 해석 지원 상태를 분류하고 사례 ID 연결. 현재 inventory는 전 필드 PLANNED_NOT_IMPLEMENTED.
3. 정상적인 데이터 없음 응답과 오류를 문서별로 구별하는 증거 확보. 모든 4xx를 absent로 간주하지 않음.

## 실행 확인 (2026-10-27)

27개 source의 수집 경로와 스키마를 패키지 registry에 포함했다. 모든 원본 필드는 공통 EntityView로 접근하며 필드별 전용 도메인 클래스 생성과 구분한다. 현재 전체 조회는26개 유효문서이며 종료된 RING_EXCHANGE를 제외해 이름 입력27회/OCID26회다. 범위 밖 링은 UNSUPPORTED(0호출) 결과로 보존한다. 실제26문서와 과거 링·심볼을 검증했으며 상세 내용은 docs/validation-2026-10-27.md에 있다.
