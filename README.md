# Maple Kit

메이플스토리 캐릭터 원본 데이터의 Java/Python 독립 라이브러리.

`java/`와 `python/`은 각각 빌드·설치하며, `fixtures/`로 동작을 함께 검증합니다.

현재·과거 캐릭터 조회, 필요한 소스만 선택한 조회, 원본·프리셋 보존, 옵션 해석과 데이터 비교를 제공합니다. 전투력 계산이나 효과 적용 판단은 수행하지 않습니다. 해석하지 못한 문구도 원문으로 보존합니다.

- [설치·임포트·장비/심볼 실행 예제](docs/usage.md#설치와-임포트)
- [선택 가능한 소스 27개와 필드 접근](docs/usage.md#선택-가능한-소스-27개)
- [그룹 조회](docs/usage.md#전체선택그룹-조회) · [프리셋](docs/usage.md#프리셋-읽기) · [오류 처리](docs/usage.md#실패와-진단)
- [코드 에이전트용 체크리스트](docs/usage.md#코드-에이전트용-체크리스트)
- [Java 빌드](java/README.md) · [Python 설치](python/README.md)

간단히 가져와서 모델로 읽거나 화면용 JSON으로 반환할 수 있습니다.

```python
from maple_library import MapleClient, Source

async with MapleClient(api_key) as maple:
    character = await maple.character("캐릭터명", sources=[Source.EQUIPMENT, Source.SYMBOLS, Source.UNION])
    item = character.equipment.weapon
    if item is not None:
        print(item.stats.to_text())
        tooltip = item.to_view()
    page = character.to_view()
```

```java
try (var maple = new MapleClient(apiKey)) {
    var character = maple.character("캐릭터명",
        Set.of(Source.EQUIPMENT, Source.SYMBOLS, Source.UNION)).join();
    var item = character.equipment().weapon();
    if (item != null) {
        System.out.println(item.stats().toText());
        var tooltip = item.toView();
    }
    var page = character.toView();
}
```

Java import는 `org.whitedoggy.maplelibrary.MapleClient`, `org.whitedoggy.maplelibrary.Source`, `java.util.Set`입니다. [27개 소스의 간편 접근, 장비·심볼·유니온 모델과 프론트엔드 예제](docs/usage.md#간편-모델과-화면용-데이터)를 참고하세요. 기존 원본 API도 유지합니다.

```text
java/       Java 21 라이브러리와 테스트
python/     Python 3.12+ 라이브러리와 테스트
fixtures/   두 언어의 공통 테스트 데이터
docs/       사용 안내
tools/      공통 테스트·패키지 검증
```

Java는 `java/`에서 `./gradlew test build`, Python은 `python/`에서 `pip install -e '.[test]'` 후 `python -m pytest`로 검증합니다. 두 테스트 후 루트에서 `python tools/check_conformance.py`로 결과를 비교합니다. Maven/PyPI에는 아직 게시하지 않은 개발 버전입니다.

## 라이선스 및 NEXON Open API 이용 안내

이 프로젝트의 자체 작성 코드는 [MIT License](LICENSE)로 제공합니다. 저작권 및 라이선스 고지를 유지하면 사용·수정·재배포할 수 있습니다.

**Data based on NEXON Open API**

- 이 프로젝트는 넥슨의 공식 SDK가 아니며, 넥슨과의 제휴·보증을 의미하지 않습니다.
- MIT는 자체 코드에 적용됩니다. 넥슨 Open API, 결과 데이터, 게임 이미지·문구·상표 및 제3자 저작물에 대한 권리를 부여하지 않습니다.
- 이용자는 본인의 애플리케이션을 등록하고 API Key를 발급받아 사용해야 합니다. 키를 소스·로그·공개 저장소에 포함하거나 다른 이용자와 공유하지 마세요. 공식적으로 허용된 키 공유 예외는 해당 API 안내를 따릅니다.
- API 호출 한도, 데이터 이용·보관·갱신, 재배포 및 영리 이용에는 넥슨의 별도 약관과 운영정책이 적용됩니다. MIT의 상업적 사용 허용이 넥슨 데이터의 영리 이용까지 허용하는 것은 아닙니다.
- 이 라이브러리를 사용하는 서비스·애플리케이션에도 `Data based on NEXON Open API` 출처 문구를 표시해야 합니다.

[공식 이용약관](https://openapi.nexon.com/ko/support/terms/) · [API 사용 및 출처 표기 안내](https://openapi.nexon.com/ko/guide/request-api/)
