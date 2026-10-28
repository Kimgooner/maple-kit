# Java 개발 패키지

Java21, package `org.whitedoggy.maplelibrary`, Gradle9.4.1, 내부 Jackson core3.1.2. 비동기 전체·선택 수집, 27개 소스 원본/view, 옵션 해석과 비교를 구현했다. Spring/Reactor 의존성은 없다.

- `./gradlew test build`: 단위/공통 fixture 테스트와 JAR·sources·Javadoc 빌드.
- `./gradlew publish`: build/repository 로컬 Maven 저장소에만 게시.
- `examples/Offline.java`: 설치된 library+runtime dependency로 실행하는 합성 소비자 예제.

[사용 안내](../docs/usage.md)에서 API와 지원 범위를 확인할 수 있다. 공개 배포 좌표는 미확정이다.

전체 조회·소스별 선택·필드 접근·프리셋·오류 처리는 [시작하기](../docs/usage.md)와 [API 참고](../docs/reference.md)를 참고하세요. 실행 가능한 장비/심볼 예제는 [Quickstart.java](examples/Quickstart.java)입니다.

```java
var sheet = client.getCharacterSelected(request, Set.of(Source.EQUIPMENT)).join();
var character = sheet.asCharacter();
var item = character.equipment().weapon();
if (item != null) {
    System.out.println(item.stats().attackPower());
    var tooltip = item.toView();
}
```

간편 모델 타입은 `Models.Item`, `Models.Symbol`, `Models.UnionRaider` 등입니다. 모든 전용 모델에서 `detail()`로 기존 EntityView를 읽을 수 있습니다. `MapleClient.character(...)`로 바로 `MapleCharacter`를 받을 수도 있습니다.

## 라이선스 및 NEXON Open API 이용 안내

이 프로젝트의 자체 작성 코드는 [MIT License](../LICENSE)로 제공합니다. 저작권 및 라이선스 고지를 유지하면 사용·수정·재배포할 수 있습니다.

**Data based on NEXON Open API**

- 이 프로젝트는 넥슨의 공식 SDK가 아니며, 넥슨과의 제휴·보증을 의미하지 않습니다.
- MIT는 자체 코드에 적용됩니다. 넥슨 Open API, 결과 데이터, 게임 이미지·문구·상표 및 제3자 저작물에 대한 권리를 부여하지 않습니다.
- 이용자는 본인의 애플리케이션을 등록하고 API Key를 발급받아 사용해야 합니다. 키를 소스·로그·공개 저장소에 포함하거나 다른 이용자와 공유하지 마세요. 공식적으로 허용된 키 공유 예외는 해당 API 안내를 따릅니다.
- API 호출 한도, 데이터 이용·보관·갱신, 재배포 및 영리 이용에는 넥슨의 별도 약관과 운영정책이 적용됩니다. MIT의 상업적 사용 허용이 넥슨 데이터의 영리 이용까지 허용하는 것은 아닙니다.
- 이 라이브러리를 사용하는 서비스·애플리케이션에도 `Data based on NEXON Open API` 출처 문구를 표시해야 합니다.

[공식 이용약관](https://openapi.nexon.com/ko/support/terms/) · [API 사용 및 출처 표기 안내](https://openapi.nexon.com/ko/guide/request-api/)
