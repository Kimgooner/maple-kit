# Maple Library — Python 개발 패키지

Python>=3.12에서 실행되는 비동기 API 수집, 원본 JSON 파싱, 옵션 해석과 데이터 비교 라이브러리다. import 이름은 maple_library이며 JVM이나 외부 런타임 의존성이 없다. HTTPX0.28.1 기반 현재/과거·전체/선택 조회를 제공한다. 옵션은 보수적 문법으로 해석하고 미지원 문구 원문을 보존한다.

```python
from maple_library import Document, Source

symbols = Document.parse(Source.SYMBOLS,
    '{"symbol":[{"symbol_name":"합성 심볼","symbol_str":"10"}]}').as_model()
print(symbols.items[0].stats.str)           # Decimal('10')
print(symbols.items[0].to_view()['stat_rows'])
```


숫자는 Decimal로 읽고 missing/null/invalid/0을 구분한다. 모든 원본 필드는 불변 raw 트리에서 접근할 수 있다. 전투력 계산·합산·보정은 수행하지 않는다.

개발 저장소에서 `python -m pip install -e '.[test]'`, `python -m pytest`를 사용한다. 테스트는 상위 fixtures를 읽는다. `uv build` 또는 `python -m build`로 sdist/wheel을 생성하며 설치된 라이브러리는 저장소 fixture 없이 동작한다.

외부 게시 전이며 공개 패키지명은 미확정이다.

전체 조회·소스별 선택·필드 접근·프리셋·오류 처리는 [시작하기](../docs/usage.md)와 [API 참고](../docs/reference.md)를 참고하세요. 실행 가능한 장비/심볼 예제는 [quickstart.py](examples/quickstart.py)입니다.

## 라이선스 및 NEXON Open API 이용 안내

이 프로젝트의 자체 작성 코드는 [MIT License](LICENSE)로 제공합니다. 저작권 및 라이선스 고지를 유지하면 사용·수정·재배포할 수 있습니다.

**Data based on NEXON Open API**

- 이 프로젝트는 넥슨의 공식 SDK가 아니며, 넥슨과의 제휴·보증을 의미하지 않습니다.
- MIT는 자체 코드에 적용됩니다. 넥슨 Open API, 결과 데이터, 게임 이미지·문구·상표 및 제3자 저작물에 대한 권리를 부여하지 않습니다.
- 이용자는 본인의 애플리케이션을 등록하고 API Key를 발급받아 사용해야 합니다. 키를 소스·로그·공개 저장소에 포함하거나 다른 이용자와 공유하지 마세요. 공식적으로 허용된 키 공유 예외는 해당 API 안내를 따릅니다.
- API 호출 한도, 데이터 이용·보관·갱신, 재배포 및 영리 이용에는 넥슨의 별도 약관과 운영정책이 적용됩니다. MIT의 상업적 사용 허용이 넥슨 데이터의 영리 이용까지 허용하는 것은 아닙니다.
- 이 라이브러리를 사용하는 서비스·애플리케이션에도 `Data based on NEXON Open API` 출처 문구를 표시해야 합니다.

[공식 이용약관](https://openapi.nexon.com/ko/support/terms/) · [API 사용 및 출처 표기 안내](https://openapi.nexon.com/ko/guide/request-api/)
