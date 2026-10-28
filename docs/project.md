# 프로젝트 범위

- 위치: maple-library/java, maple-library/python.
- Java 21 기준. Python 3.12 이상, Java JDK HttpClient/Jackson3 core, Python HTTPX/표준 dataclasses를 채택했다. 세부 근거는 [ADR](decisions/0001-runtime-and-model.md)을 따른다.
- 입력: 클라이언트 API 키, 조회 요청의 캐릭터 이름(및 OCID 재사용 경로), 현재 또는 날짜.
- 출력: 전체 지원 소스 또는 명시적으로 선택한 소스의 원본 시트. 선택한 시스템의 전체 제공 프리셋을 보존한다.
- 스킬 범위: 0차·하이퍼 패시브/액티브·5차·6차. 1~4차 및 중간 전직은 제외. 링크와 하이퍼스탯은 별개 소스다.
- 원문·조건·출처·알 수 없는 필드를 보존한다. 과거 입력을 현재 스킬표로 채우지 않는다.
- 비교는 네트워크 없이 수행하며 방향은 after - before다. 미수집 데이터를 삭제로 간주하지 않는다.
- 서비스 통합·어센트 잔차 해결은 이 프로젝트의 완료 조건이 아니다.

## 설계 근거

[구현 계획](../.plan/general/2026-10-27-java-python-library.md), [공통 계약](../contracts/README.md), [파서 책임 분류](research/parser-boundary.md)를 따른다. 기존 웹 프로젝트의 조사 기록은 역사적 배경이며 이 저장소의 설치·실행에 필요하지 않다.
