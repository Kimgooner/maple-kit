# 기존 파서 책임 분류 — 2026-10-27

기준은 현재 미커밋 작업을 포함한 작업 트리다. [파일 해시](source-manifest.json)는 조사 기준을 식별하며 전체 소스의 정합성 검증을 뜻하지 않는다. 아래 메소드의 필드·정규식 지식을 새 모델에 재구현한다. 클래스 복사는 하지 않는다. 특히 기존 출력 StatSheet는 새 라이브러리의 공개 모델이 아니다.

분류: **해석**은 재사용할 지식, **분리**는 필드 추출만 사용하고 정책 제거, **제외**는 소비자 계산/운영 책임.

| 기존 클래스·메소드 | 분류 | 가져올 부분 / 남길 부분 |
|---|---|---|
| EffectTextSplitter.split/addCommaSplit | 분리 | 복합 이름 안의 쉼표 구별은 활용. trim·개행 분리 전 원문과 Unicode code-point 범위를 보존. 조건절을 잃는 단순 분할은 금지 |
| StatSheetParser.rules/theoryRules | 분리 | 스탯 동의어·숫자·단위 문법은 rule별 재검증. combat/theory 구분은 새 모델에 없음 |
| StatSheetParser.normalize | 분리 | 안전한 어휘 정규화만 별도 view로 사용. 괄호·자신의·대신 등 조건/대상을 지우지 않음; 원문 불변 |
| StatSheetParser.parse/apply/apply* / shouldSkip | 제외 | 합산, 방무/최종뎀 곱연산, flatStatAsNoPercent, 조건부 문구 skip은 소비자 정책. decoder는 Option 목록을 반환 |
| Jsons.text/optionalInt/parseInt/parseDouble/array | 재작성 | missing/null/invalid→빈값/0 치환, double→floor 정수화, 비배열→빈배열 처리를 이식하지 않음 |
| ItemEquipmentParser.getCurrentPresetItemEquipment/getCurrentItemEquipment/getDragonItem/getMechanicItem | 해석 | 필드 경로 지식. 원본 상태를 명시적으로 반환 |
| ItemEquipmentParser.getItemEquipmentByPreset/getTitleItem(번호)/availablePresets | 분리 | 프리셋 필드명 활용. 현재값 fallback, 빈 프리셋 제거, 1번 합성 제거. current와 title_preset은 독립 |
| ItemEquipmentParser.scorePreset/isSeedRing | 제외 | 보스 프리셋 점수와 선호 정책 |
| ItemParser.getPotentialOptions/getAdditionalPotentialOptions/getSoulPotentialOptions | 분리 | 각 줄의 필드명·순서 활용. 누락/빈 문자열 구분 및 원본 경로 유지; 소울 빈 줄 필터 제거 |
| ItemParser.applyTooltip/TOOLTIP_STATS/optionValue | 분리 | base/add/etc/starforce/total의 개별 경로·단위 활용. 0행 제거 및 누락→0 금지. API total을 구성값으로 재계산하지 않음 |
| ItemParser.addStructuredOptionEffects* / addSoulEffects/describeSoul | 분리 | 옵션 키/소울 필드 지식만 사용. 무기별 제외·양수만 보존·문자열 합성·합산 제거 |
| ItemParser.get*SnapShot/getItemStatEffects*/applyEffects/hpChunks | 제외 | 현재 반환 모델은 가변 계산/화면 모델. 새 원본 아이템 모델로 대체 |
| ItemParser.astraCombatPowerOption/isReplacedByAstra/actualWeaponAdjustment/getNormalizedAttackForZero/getStarForce15/zeroSubWeaponBoss | 제외 | 아스트라 차감, 제로·무기 환산, 스타포스 재계산 |
| ItemParser.getTitleStatEffects/getTitleItemSnapShot/statLikeLines | 분리 | 칭호 이름·설명·기간 보존. 만료 제외와 숫자 포함 문장만 남기는 필터 제거 |
| AbilityParser.getCurrentPresetAbility/getCurrentAbilityByPreset/availablePresets/normalizePresetNo | 분리 | 필드 경로 활용. 기본 1번·빈 목록 제외·유효하지 않은 번호 1 치환 제거 |
| AbilityParser.AP_CONVERSION | 해석 | 원문에서 기준 스탯=LUK, 비율=7%, 대상=DEX, 기준=직접 투자 AP를 expression으로 기록 |
| AbilityParser.resolveApConversions/investedAp/scorePreset | 제외 | AP 곱셈/내림/0 제외 및 추천 |
| HyperStatParser.getCurrentPresetNo/availablePresets/getStatIncreaseEffects/remainPoint | 분리 | 프리셋·레벨·잔여 포인트를 그대로 보존. fallback 1/Integer.MAX_VALUE 제거 |
| HyperStatParser.scorePreset | 제외 | 가중치·미투자 감점 |
| SymbolParser.getSymbolStatEffects/mainStatOf/append | 제외+필드 참조 | STR/DEX/INT/LUK를 합쳐 주스탯으로 재배치하는 것은 계산. 심볼별 원본 필드로 새로 작성; GameData 필요 없음 |
| CashItemParser.getItemSnapShot/getCashStatEffect | 분리 | 옵션 type/value, base/additional/공유 프리셋을 모두 보존. 만료 제외·StatSheet merge 제거 |
| PetParser.slot/usesWorldSharePet/getItemSnapShot/getPetEquipmentEffects/getPetEquip | 분리 | 일반/공유 prefix·활성 flag·장비/펫 만료 필드 활용. 한쪽만 선택·빈 슬롯 생략·양수 필터·만료 적용 제거 |
| ArtifactParser.getArtifactEffects | 분리 | 보고된 effect.name 및 원문 분할 지식; 전체 원문과 출처 유지 |
| ArtifactParser.parseCrystals/toEffect | 제외+필드 참조 | crystal 이름/레벨/기간/flag는 보존. 옵션 레벨 합산·상한·레벨×상수·만료 제외는 서버 |
| ChampionParser.getChampionStats/isChampion/badgeUnverified | 분리 | 명단·배지 원문 각각 보존. 명단 없으면 적용=true, 대상 판정으로 효과 삭제 금지. 불완전 명단은 진단 |
| RaiderParser.getUnionRaiderStat/getUnionOccupiedStat/findStatPreset/readTextArray | 해석 | 최상위 대원 효과와 점령 프리셋 구분; 배열의 실제 preset_no 탐색 |
| RaiderParser.getUnionCurrentUse/getUnionOccupiedStatByPreset/availablePresets/getCombatStatEffectsByPreset/scorePreset | 분리/제외 | 현재값 fallback·최대10 고정·합친 효과 목록·추천 제거. legacy 필드도 raw 보존 |
| SkillParser.getAllPassiveEffects/parsedEffectsOf/isCombatRelevant* | 제외 | 축복 최댓값, 월드 고정표, 효과 포함/제외, 조건부 적용 판정 |
| SkillParser.passivePart/effectWithPermanentDodgeSeparated | 분리 | 명시적 패시브 영역·설명 참조는 source span으로 기록 가능. 직업 조건 삭제, 비용 없음→상시 추론, 닷지 특별 변환을 그대로 이식하지 않음 |
| SkillParser.overwrittenSkills/overwritingLines/partlyOverwrittenStats/finalDamageOverrides/passiveFinalDamageEnhancements/passiveIgnoreEnhancements/raiseFinalDamage/raiseIgnoreDefense | 제외 | 스킬 간 대체·강화 합성. 관계 원문을 남겨도 다른 스킬 값을 수정하지 않음 |
| SkillParser.alwaysMaxStackLines/comboCounterLines/gateByEquipment/withoutAttackLines/withoutTimedClauses/raisedByAbilityPassiveLevel | 제외 | 최대 스택, 장비/시간 조건 적용·삭제, 레벨 보정. 1~4차 수집 확대의 근거로 사용하지 않음 |
| HexaParser.getCurrentHexa/applyMainStat/applySubStat/hpPerPoint | 제외+필드 참조 | 코어 이름·레벨·프리셋만 보존. 레벨표 조회, 직업/날짜별 환산, 방무 합산 제외 |
| HexaCoreParser.cores/solErdaFragments/solErdaFragmentsRequired/sum/tableFor | 제외+필드 참조 | API 코어·linked_skill·event level 보존. 솔 야누스 제외, 순서 기반 비용 종류 추론, 조각 비용 합산 제거 |
| SetEffectParser.getSetEffectByPreset/getAppliedSetCounts/withCashForm 및 count/match/lucky helpers | 제외 | 세트 재구성·럭키 아이템·제로·캐시폼·외부 테이블 조합. API set_effect의 개수·효과 배열을 새 raw parser가 보존 |
| BasicParser.characterName/World/Guild/Class/Level/Image/CreatedAt/isInactive | 분리 | 필드·시각 파싱. 레벨 누락→0, 잘못된 시각→null, 접속 flag 의미 추론 제거; offset 원문 유지 |
| OtherStatParser.toEffect/getOtherStatEffects | 분리 | (+%) 단위 표기 활용. 제네시스 패스 등 그룹 제외 없이 모든 그룹 보존 |
| PropensityParser.willpowerLevel/theoryEffects | 제외+필드 참조 | 여섯 성향 원본 레벨 제공. 누락→0, 레벨/10·0.5배 공식 제외 |
| NexonEndpoint / NexonApiClient.get/getOcid | 분리 | endpoint/grade/query/header 지식. Reactor·Spring·오류/캐시 정책은 독립 구현 |
| SnapshotService.fetchSnapshot/fetchEndpoint/missingDocument/isAbsent | 재작성 | 선택 소스와 문서 상태 구현. 모든 4xx를 정상 부재로 취급하는 기존 규칙 금지 |
| SnapshotService.repairChampionRoster/currentRoster/currentSkill6/withRoster | 제외 | 과거 자료에 현재 명단/스킬 아이콘을 혼합하는 서비스 보정 |
| NexonRateLimiter.acquire/reserveDelayNanos | 설계 참조 | 예약 시각 기반 pacing 아이디어만 활용. 기본300·0이면 무제한·Mono 타입은 이식하지 않음 |
| StatSheet, GameData, BowNormalization, WeaponData, PresetSelector, 계산용 StatParser | 제외 | 라이브러리에 import/복사하지 않는 계산·정책 계층 |

## 이식 순서와 반례

1. 심볼: STR=10, DEX=20이 주어져도 두 값을 유지. 주스탯30으로 만들지 않음.
2. 장비: total/base/add/starforce를 모두 보존. total과 부분합이 달라도 수정하지 않음.
3. 옵션: `최대 HP,MP+300`은 두 대상에 대한 하나의 원문 표현으로 보존. 원문 span은 양쪽 언어에서 같은 code-point offset.
4. AP 변환·확률·조건·기간은 표현식/조건으로 기록. 평가하지 않음.
5. 프리셋2 누락과 프리셋2 빈 배열은 다르다. current로 보충하지 않음.
6. 모르는 문구는 unknown, 일부만 아는 복합 문구는 미해석 범위를 남김. 공격기 문구를 조용히 버리지 않음.

## 판단의 한계

모든 private 메소드의 정확성을 증명한 감사가 아니다. 주요 파서 진입점과 계산 결합 지점을 분류했다. 표에 없는 helper는 자동 이식 대상이 아니며 이식 직전 caller와 원본 fixture를 함께 검토한다. 현재 파서의 골든 결과는 계산 소비자 기준이므로 새 원본 시트의 정답으로 복사하지 않는다.

## 소스 위치

아래 경로는 현재 형제 프로젝트 기준이다.

- [EffectTextSplitter](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/common/support/EffectTextSplitter.java)
- [StatSheetParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/common/stat/StatSheetParser.java)
- [Jsons](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/global/Jsons.java)
- [ItemEquipmentParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/item/parser/ItemEquipmentParser.java)
- [ItemParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/item/parser/ItemParser.java)
- [AbilityParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/ability/AbilityParser.java)
- [HyperStatParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/hyper/HyperStatParser.java)
- [SymbolParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/symbol/SymbolParser.java)
- [CashItemParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/cash/CashItemParser.java)
- [PetParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/pet/PetParser.java)
- [ArtifactParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/union/artifact/ArtifactParser.java)
- [ChampionParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/union/champion/ChampionParser.java)
- [RaiderParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/union/raider/RaiderParser.java)
- [SkillParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/skill/SkillParser.java)
- [HexaParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/hexa/HexaParser.java)
- [HexaCoreParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/hexa/HexaCoreParser.java)
- [SetEffectParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/set/parser/SetEffectParser.java)
- [BasicParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/basic/BasicParser.java)
- [OtherStatParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/otherstat/OtherStatParser.java)
- [PropensityParser](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/domain/propensity/PropensityParser.java)
- [NexonEndpoint](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/external/nexon/config/NexonEndpoint.java)
- [NexonApiClient](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/external/nexon/client/NexonApiClient.java)
- [SnapshotService](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/analysis/service/SnapshotService.java)
- [NexonRateLimiter](../../../maple-web-proj-v2/src/main/java/org/whitedoggy/mapleweb2/external/nexon/client/NexonRateLimiter.java)
