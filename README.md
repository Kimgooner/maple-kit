# Maple Kit

메이플스토리 캐릭터 원본 데이터의 Java/Python 독립 라이브러리.

`java/`와 `python/`은 각각 빌드·설치하며, `contracts/`와 `fixtures/`로 동작을 함께 검증합니다.

**Java/Python 수집·해석·비교 구현:** 전체·선택 API 조회, 27개 소스 상태, 원본·프리셋 보존, 보수적 옵션 해석과 순수 데이터 비교를 제공한다. 실제 키로 두 언어를 검증했다. 외부 게시 전 개발용 패키지이며 상세 지원 범위와 제한은 아래 문서를 따른다.

- [실행 가능한 API 안내](docs/agent-guide.md)
- [구현 상태와 한계](docs/implementation-status.md)
- [전체 구현 계획](.plan/general/2026-10-27-java-python-library.md)
- [공통 계약](contracts/README.md)
- [기존 파서 책임 분류](docs/research/parser-boundary.md)
- [기술 선택](docs/decisions/0001-runtime-and-model.md)

Java는 java 폴더에서 Java21로 `./gradlew test build`, Python은 python 폴더에서 개발 설치 후 `python -m pytest`를 실행한다. 두 테스트 후 루트에서 `python tools/check_conformance.py`로 공통 expected와 교차 검증한다.

전투력·총 스탯 계산, 효과 적용 판단과 보스 프리셋 추천은 소비자 책임이다. MapleDelta 서버 통합은 별도 담당 범위다.

## 라이선스 및 NEXON Open API 이용 안내

이 프로젝트의 자체 작성 코드는 [MIT License](LICENSE)로 제공합니다. 저작권 및 라이선스 고지를 유지하면 사용·수정·재배포할 수 있습니다.

**Data based on NEXON Open API**

- 이 프로젝트는 넥슨의 공식 SDK가 아니며, 넥슨과의 제휴·보증을 의미하지 않습니다.
- MIT는 자체 코드에 적용됩니다. 넥슨 Open API, 결과 데이터, 게임 이미지·문구·상표 및 제3자 저작물에 대한 권리를 부여하지 않습니다.
- 이용자는 본인의 애플리케이션을 등록하고 API Key를 발급받아 사용해야 합니다. 키를 소스·로그·공개 저장소에 포함하거나 다른 이용자와 공유하지 마세요. 공식적으로 허용된 키 공유 예외는 해당 API 안내를 따릅니다.
- API 호출 한도, 데이터 이용·보관·갱신, 재배포 및 영리 이용에는 넥슨의 별도 약관과 운영정책이 적용됩니다. MIT의 상업적 사용 허용이 넥슨 데이터의 영리 이용까지 허용하는 것은 아닙니다.
- 이 라이브러리를 사용하는 서비스·애플리케이션에도 `Data based on NEXON Open API` 출처 문구를 표시해야 합니다.

[공식 이용약관](https://openapi.nexon.com/ko/support/terms/) · [API 사용 및 출처 표기 안내](https://openapi.nexon.com/ko/guide/request-api/)
