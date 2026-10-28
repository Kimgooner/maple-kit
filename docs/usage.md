# Maple Kit 시작하기

캐릭터 데이터를 가져오고, 필요한 값을 읽고, 화면에 보여주기까지.
Java와 Python 중 서비스에서 쓰는 언어 하나만 선택하면 됩니다.

**[화면 예제 먼저 보기](frontend.md)** · [전체 API 찾아보기](reference.md)

![Maple Kit으로 만든 장비 툴팁, 심볼과 유니온 화면](images/frontend-desktop.png)

위 화면은 라이브러리의 `to_view()` 결과를 실제로 렌더링한 예제입니다. 합성 데이터와 자체 제작한 샘플 아이콘을 사용했습니다.

## 1. 설치하기

아직 PyPI와 Maven Central에 배포하지 않은 개발 버전입니다. 저장소를 내려받아 설치하세요.

```sh
git clone https://github.com/Kimgooner/maple-kit.git
cd maple-kit
```

### Python · 3.12 이상

```sh
python -m venv .venv
source .venv/bin/activate
python -m pip install ./python
```

Windows에서는 활성화 명령 대신 `.venv\Scripts\activate`를 사용합니다. HTTP 통신에 필요한 의존성도 함께 설치됩니다.

### Java · 21 이상

먼저 로컬 Maven 저장소에 라이브러리를 빌드합니다.

```sh
./java/gradlew -p java publish
```

Windows에서는 `java\gradlew.bat -p java publish`를 사용합니다. 다음으로, 사용할 서비스의 `build.gradle.kts`에 추가합니다. 경로는 실제 내려받은 위치로 바꿔주세요.

```kotlin
repositories {
    maven { url = uri("/absolute/path/maple-kit/java/build/repository") }
    mavenCentral()
}
dependencies {
    implementation("org.whitedoggy:maple-library:0.1.0-dev.1")
}
```

## 2. 캐릭터 가져오기

실행 환경에 `MAPLE_OPEN_API_KEY`와 `MAPLE_CHARACTER_NAME`을 설정합니다. 넥슨 API 키는 백엔드에서만 사용하고, 프론트엔드로 보내지 않습니다. 라이브러리는 `.env`를 자동으로 읽지 않습니다.

아래 예제는 **장비·심볼·유니온**만 가져옵니다. 필요한 데이터는 `sources`에서 선택합니다.

### Python

```python
import asyncio
import os
from maple_library import MapleClient, Source

async def main():
    async with MapleClient(os.environ["MAPLE_OPEN_API_KEY"]) as maple:
        character = await maple.character(
            os.environ["MAPLE_CHARACTER_NAME"],
            sources=[Source.EQUIPMENT, Source.SYMBOLS, Source.UNION],
        )
        for item in character.equipment.items:
            print(item.slot, item.name)
        print("유니온 레벨:", character.union.basic.level)

asyncio.run(main())
```

### Java

```java
import java.util.Set;
import org.whitedoggy.maplelibrary.MapleClient;
import org.whitedoggy.maplelibrary.Source;

public class Example {
    public static void main(String[] args) {
        try (var maple = new MapleClient(System.getenv("MAPLE_OPEN_API_KEY"))) {
            var character = maple.character(
                System.getenv("MAPLE_CHARACTER_NAME"),
                Set.of(Source.EQUIPMENT, Source.SYMBOLS, Source.UNION)
            ).join();
            for (var item : character.equipment().items()) {
                System.out.println(item.slot() + " " + item.name());
            }
            System.out.println("유니온 레벨: " + character.union().basic().level());
        }
    }
}
```

파일로 된 예제가 필요하면 [Python](../python/examples/quickstart.py) / [Java](../java/examples/Quickstart.java)를 사용하세요. 서버에서는 클라이언트를 재사용하고, 종료 시 닫습니다.

## 3. 필요한 값 읽기

아래 코드는 위에서 가져온 `character`를 사용합니다. 값을 읽을 때마다 API를 다시 호출하지 않습니다.

| 하고 싶은 일 | Python | Java |
| --- | --- | --- |
| 현재 장비 목록 | `character.equipment.items` | `character.equipment().items()` |
| 무기 하나 | `character.equipment.weapon` | `character.equipment().weapon()` |
| 무기의 공격력 | `item.stats.attack_power` | `item.stats().attackPower()` |
| 심볼 목록 | `character.symbols.items` | `character.symbols().items()` |
| 심볼 레벨 | `symbol.level` | `symbol.level()` |
| 유니온 레벨 | `character.union.basic.level` | `character.union().basic().level()` |
| 유니온 배치 블록 | `character.union.raider.blocks` | `character.union().raider().blocks()` |

무기를 착용하지 않았다면 `weapon`은 `None` / `null`입니다. 장비 스탯은 API가 제공한 장비 자체 수치이며, 잠재능력을 합산한 캐릭터 최종 스탯은 아닙니다.

**장비를 글자로 보여주고 싶다면** 표시용 메서드를 사용하세요.

```python
item = character.equipment.weapon
if item is not None and item.stats is not None:
    print(item.stats.to_text())
```

```text
STR +385
DEX +349
공격력 +1122
...
```

Java에서는 `item.stats().toText()`입니다. 한 줄씩 다르게 꾸미려면 `to_rows()` / `toRows()`를 사용합니다.

**현재 장비와 프리셋은 별개입니다.** 현재 적용 장비는 `equipment.items`, 저장된 2번 프리셋은 `equipment.preset(2).items`, 현재 프리셋 번호는 `equipment.current_preset_number`입니다. Java는 각각 `items()`, `preset(2).items()`, `currentPresetNumber()`로 읽습니다.

## 4. 화면에 보여주기

직접 스탯 이름을 번역하거나 `+`, `%`를 붙일 필요 없이, 화면용 데이터를 받을 수 있습니다.

```python
page = character.to_view()              # 전체 화면용 dict
symbols = character.symbols.to_view()   # 심볼 섹션
item = character.equipment.weapon
tooltip = item.to_view() if item else None
```

```java
var page = character.toView();
var symbols = character.symbols().toView();
var item = character.equipment().weapon();
var tooltip = item == null ? null : item.toView();
```

백엔드에서 이 dict / Map을 JSON 응답으로 반환하면 됩니다. 프론트엔드가 받는 필드 이름은 두 언어 모두 같습니다. JSON 문자열이 필요하면 `to_json()` / `toJson()`을 사용하세요.

- **목록:** `name`, `icon_url`, `badges`, `potential.summary_text`
- **툴팁:** `display_name`, `stat_rows`, `potential.lines`, `soul`
- **심볼:** `level_text`, `force_text`, `stat_rows`, `growth`
- **유니온:** `basic.level`, `raider.blocks`, `artifact.crystals`, `champion.members`

**[이미지와 코드로 보는 프론트엔드 예제 →](frontend.md)**

## 다음에 필요한 것

| 궁금한 내용 | 찾아볼 곳 |
| --- | --- |
| 스킬·캐시·펫 등 다른 데이터도 가져오기 | [27개 소스와 간편 접근](reference.md#소스별-간편-접근) |
| 기본·추가옵션·스타포스 수치 나눠 읽기 | [장비 모델 상세](reference.md#장비-숫자-접근과-툴팁) |
| 과거 날짜 조회, OCID 재사용 | [조회 요청](reference.md#ocid-재사용과-과거-조회) |
| 일부 데이터만 실패했을 때 처리하기 | [실패와 진단](reference.md#실패와-진단) |
| 원본 필드와 미해석 옵션 확인하기 | [필드와 배열](reference.md#필드와-배열-읽기) · [옵션 해석](reference.md#옵션-해석과-비교) |

처음 사용할 때는 세 가지만 기억하세요. **선택하지 않은 소스는 읽을 수 없습니다.** 실제 빈 목록 `[]`와 데이터 없음 `null`은 다릅니다. 화면용 스탯 수치는 정밀도를 위해 문자열이므로, 표시할 때는 준비된 `text` / `value_text`를 쓰면 됩니다. 자세한 규칙은 [부재·오류·정밀도 계약](reference.md#부재오류정밀도-계약)에 있습니다.

서비스에는 **Data based on NEXON Open API** 출처를 표시하세요. [라이선스와 API 이용 안내](../README.md#라이선스-및-nexon-open-api-이용-안내)도 함께 확인할 수 있습니다.
