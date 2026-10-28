# 공통 합성 fixture

- raw/symbols.json, raw/equipment.json: 독립 필드 접근 검증용 원본.
- cases/offline-v1.json: 15개 before/after와 독립 작성 expected.
- expected/offline-v1.md: 기대 결과의 판단 근거.

실제 키/캐릭터 자료는 없다. 두 언어는 expected를 각각 통과한 뒤 테스트 전용 projection을 비교한다. 기대값은 구현 결과에서 자동 갱신하지 않는다. HTTP·옵션 decoder 사례는 다음 구현 단계에 추가한다.
