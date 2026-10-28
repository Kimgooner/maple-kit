# Maple Kit 사용 안내

이 문서는 라이브러리를 사용하는 개발자와 코드 에이전트를 위한 API 안내입니다. Java 21 / Python 3.12 이상을 지원하며 두 언어는 독립 설치합니다. 패키지 이름은 저장소 이름 `maple-kit`과 다릅니다: Python import는 `maple_library`, Java package는 `org.whitedoggy.maplelibrary`입니다.

## 목차

1. [설치와 임포트](#설치와-임포트)
2. [장비와 심볼 조회 예제](#장비와-심볼-조회-예제)
3. [전체·선택·그룹 조회](#전체선택그룹-조회)
4. [선택 가능한 소스 27개](#선택-가능한-소스-27개)
5. [간편 모델과 화면용 데이터](#간편-모델과-화면용-데이터)
6. [필드와 배열 읽기](#필드와-배열-읽기)
7. [프리셋 읽기](#프리셋-읽기)
8. [OCID 재사용과 과거 조회](#ocid-재사용과-과거-조회)
9. [실패와 진단](#실패와-진단)
10. [옵션 해석과 비교](#옵션-해석과-비교)
11. [클라이언트 수명과 호출 제한](#클라이언트-수명과-호출-제한)
12. [코드 에이전트용 체크리스트](#코드-에이전트용-체크리스트)

## 설치와 임포트

아직 PyPI/Maven Central에 게시하지 않았습니다. 아래는 이 저장소에서 설치하는 방법이며, `pip install maple-kit` 또는 Maven Central에서 바로 의존성을 찾는 방법이 아닙니다.

### Python

```sh
git clone https://github.com/Kimgooner/maple-kit.git
cd maple-kit
python -m venv .venv
# macOS/Linux. Windows에서는 .venv\Scripts\activate 사용
source .venv/bin/activate
python -m pip install ./python
```

```python
from maple_library import MapleClient, Source
```

런타임 의존성 HTTPX는 pip가 함께 설치합니다. 개발 중 수정 내용을 바로 쓰려면 `python -m pip install -e './python[test]'`를 사용합니다.

### Java

저장소 루트에서 로컬 Maven 저장소를 만듭니다.

```sh
./java/gradlew -p java publish
# Windows: java\gradlew.bat -p java publish
```

소비자 프로젝트의 `build.gradle.kts`에 다음을 넣습니다. 경로는 실제 clone 위치로 바꿉니다.

```kotlin
plugins { java }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories {
    maven { url = uri("/absolute/path/maple-kit/java/build/repository") }
    mavenCentral()
}
dependencies {
    implementation("org.whitedoggy:maple-library:0.1.0-dev.1")
}
```

```java
import org.whitedoggy.maplelibrary.MapleClient;
import org.whitedoggy.maplelibrary.CharacterRequest;
import org.whitedoggy.maplelibrary.Source;
import org.whitedoggy.maplelibrary.Document;
import org.whitedoggy.maplelibrary.FieldValue;
import java.util.Set;
```

JAR 하나만 수동으로 복사하면 Jackson 런타임 의존성이 빠질 수 있습니다. 위 Maven 의존성으로 가져오면 함께 해결됩니다. `publish`는 이 프로젝트의 `java/build/repository`에만 게시합니다.

## 장비와 심볼 조회 예제

실행 환경에 `MAPLE_OPEN_API_KEY`와 `MAPLE_CHARACTER_NAME`을 설정합니다. `.env`를 자동으로 읽지 않으므로 실행 시 환경변수로 주입하세요. 실행 파일은 [Python quickstart](../python/examples/quickstart.py), [Java Quickstart](../java/examples/Quickstart.java)입니다.

```python
"""Run with MAPLE_OPEN_API_KEY and MAPLE_CHARACTER_NAME in the environment."""
import asyncio
import os
from maple_library import MapleClient, Source


def print_summary(character):
    for item in character.equipment.items:
        print(item.slot, item.name)
    for symbol in character.symbols.items:
        print(symbol.name, symbol.level)


async def main():
    async with MapleClient(os.environ['MAPLE_OPEN_API_KEY']) as maple:
        character = await maple.character(
            os.environ['MAPLE_CHARACTER_NAME'],
            sources=[Source.EQUIPMENT, Source.SYMBOLS, Source.UNION],
        )
        print_summary(character)
        print(character.union.basic.level)
        # A web handler can return this dict directly as its JSON response.
        view = character.to_view()
        print(view['equipment']['items'])


if __name__ == '__main__':
    asyncio.run(main())
```

```java
import java.util.Set;
import org.whitedoggy.maplelibrary.MapleCharacter;
import org.whitedoggy.maplelibrary.MapleClient;
import org.whitedoggy.maplelibrary.Source;

public class Quickstart {
    public static void printSummary(MapleCharacter character) {
        for (var item : character.equipment().items()) {
            System.out.println(item.slot() + " " + item.name());
        }
        for (var symbol : character.symbols().items()) {
            System.out.println(symbol.name() + " " + symbol.level());
        }
    }

    public static void main(String[] args) {
        try (var maple = new MapleClient(System.getenv("MAPLE_OPEN_API_KEY"))) {
            var character = maple.character(
                System.getenv("MAPLE_CHARACTER_NAME"),
                Set.of(Source.EQUIPMENT, Source.SYMBOLS, Source.UNION)
            ).join();
            printSummary(character);
            System.out.println(character.union().basic().level());
            // Return this Map from a web handler, or use toJson() for a JSON string.
            var view = character.toView();
            System.out.println(view.get("equipment"));
        }
    }
}
```

## 간편 모델과 화면용 데이터

`maple.character(...)`는 Python `Character`, Java `MapleCharacter`를 반환합니다. 속성을 읽거나 `to_view()`를 호출해도 추가 HTTP 요청은 발생하지 않습니다. 기존 `get_character()` / `getCharacter()`는 그대로 `CharacterSheet`를 반환하며, `sheet.as_character()` / `sheet.asCharacter()`로 같은 간편 모델을 얻습니다.

```python
character = await maple.character(
    "캐릭터명", sources=[Source.EQUIPMENT, Source.SYMBOLS, Source.UNION]
)
item = character.equipment.weapon
if item is not None:
    print(item.name, item.stats.attack_power)       # str, Decimal
    print(item.stats.to_text())                    # STR +385\n공격력 +1122 ...
    tooltip = item.to_view()                       # JSON 직렬화 가능한 dict
    json_text = item.to_json()                     # JSON 문자열

symbol = character.symbols.items[0]
print(symbol.name, symbol.level, symbol.growth.current)
print(character.union.basic.level)
page = character.to_view()                         # 선택한 소스들을 한 번에 화면용으로
```

```java
var character = maple.character("캐릭터명",
    Set.of(Source.EQUIPMENT, Source.SYMBOLS, Source.UNION)).join();
var item = character.equipment().weapon();
if (item != null) {
    System.out.println(item.stats().attackPower()); // BigDecimal
    System.out.println(item.stats().toText());
    var tooltip = item.toView();                    // Map<String, Object>
    String json = item.toJson();
}
var symbol = character.symbols().items().getFirst();
System.out.println(symbol.growth().current());
System.out.println(character.union().basic().level());
var page = character.toView();
```

Java의 `toView()` Map과 Python의 `to_view()` dict는 동일한 snake_case JSON 키를 사용합니다. Java에서는 웹 프레임워크가 Map을 직렬화하게 하거나, 의존성 추가 없이 `toJson()`을 쓸 수 있습니다. Python은 `json.dumps(view, ensure_ascii=False)`도 가능합니다. 모델과 원본은 불변이며, 반환된 화면용 객체를 수정해도 모델은 바뀌지 않습니다.

### 소스별 간편 접근

다음 경로는 Python 기준입니다. Java에서는 `hyper_stats` → `hyperStats()`처럼 camelCase 메서드로 읽습니다. 아래 소스를 선택하지 않으면 해당 속성에 접근할 때 `DOCUMENT_UNAVAILABLE`입니다. `Source.UNION`은 유니온 4개, `Source.SKILLS`는 스킬 5개를 선택합니다.

| 선택 소스 | 간편 접근 | 주요 데이터 |
| --- | --- | --- |
| `BASIC` | `character.basic` | `name`, `world`, `job`, `level`, `exp`, `guild`, `image` |
| `REPORTED_STATS` | `character.stats` | `entries`, `get("STR")`, `remaining_ap`; 항목의 `name`, 원문 `value` |
| `EQUIPMENT` | `character.equipment` | `items`, `weapon`, `secondary`, `emblem`, `get("반지1")`, `title`, `dragon_items`, `mechanic_items` |
| `SYMBOLS` | `character.symbols` | `items`, `get("심볼 이름")` |
| `ABILITIES` | `character.abilities` | `grade`, `lines`, `remaining_fame`, `preset(n)` |
| `HYPER_STATS` | `character.hyper_stats` | `items`(현재 프리셋), `preset(n)`, `current_preset_number`, `available_points` |
| `PROPENSITY` | `character.propensity` | `charisma`, `sensibility`, `insight`, `willingness`, `handicraft`, `charm` |
| `CASH` | `character.cash` | `items`, `additional_items`, `shared_master_label`, `preset(n)`, `additional_preset(n)` |
| `ANDROID` | `character.android` | `name`, `hair`, `face`, `skin`, `cash_items`, `preset(n)` |
| `PETS` | `character.pets` | `items`, `shared`, `shared_equipment`, `petite_luna_skills` |
| `SET_EFFECTS` | `character.set_effects` | `items` → `name`, `equipped_count`, `active_tiers`, `all_tiers` |
| `SKILL_0` | `character.skills.zero` | `grade`, `items` |
| `SKILL_HYPER_PASSIVE` | `character.skills.hyper_passive` | `grade`, `items` |
| `SKILL_HYPER_ACTIVE` | `character.skills.hyper_active` | `grade`, `items` |
| `SKILL_5` | `character.skills.fifth` | `grade`, `items` |
| `SKILL_6` | `character.skills.sixth` | `grade`, `items` |
| `LINK_SKILLS` | `character.link_skills` | `items`, `owned`, `preset(n)`, `owned_preset(n)` |
| `V_MATRIX` | `character.v_matrix` | `cores`, `remaining_slot_points`, `preset(n)` |
| `HEXA_CORES` | `character.hexa.cores` | `cores` → `name`, `level`, `type`, `linked_skills` |
| `HEXA_STATS` | `character.hexa.stats` | `cores`, `cores_2`, `cores_3`, `preset_cores`, `preset_cores_2`, `preset_cores_3` |
| `OTHER_STATS` | `character.other_stats` | `groups` → `name`, `entries` |
| `RING_EXCHANGE` | `character.special_rings.exchange` | `name`, `level`, `icon`, `description` |
| `RING_RESERVE` | `character.special_rings.reserve` | `name`, `level`, `icon`, `description` |
| `UNION_BASIC` | `character.union.basic` | `level`, `grade`, `artifact_level`, `artifact_exp`, `artifact_points` |
| `UNION_RAIDER` | `character.union.raider` | `member_effects`, `state_effects`, `occupied_effects`, `blocks`, `inner_regions`, `preset(n)` |
| `UNION_ARTIFACT` | `character.union.artifact` | `effects`, `crystals`, `remaining_ap` |
| `UNION_CHAMPION` | `character.union.champion` | `members`, `badge_effects` |

각 소스 모델과 그 안의 장비·심볼·블록·코어·효과 항목에 `to_view()` / `toView()`가 있습니다. 화면에는 간편 이름의 필드, `icon_url`, 레벨이 있는 항목의 `level_text`, 옵션 원문과 `parse_status`가 전달됩니다. 숫자 레벨을 효과량으로 바꾸거나 만료된 항목을 자동 제거하지 않습니다. 상세 속성의 정확한 이름은 타입이 명시된 [Python 모델](../python/src/maple_library/friendly.py)과 [Java Models](../java/src/main/java/org/whitedoggy/maplelibrary/Models.java)에서도 확인할 수 있습니다.

### 장비: 숫자 접근과 툴팁

| 접근 | 의미 |
| --- | --- |
| `equipment.items` | API가 반환한 현재 적용 장비 |
| `equipment.current_preset_number` | 현재 프리셋 번호. 장비 목록이 아님 |
| `equipment.preset(2).items` | 저장된 2번 프리셋. 현재 적용 장비와 다를 수 있음 |
| `equipment.title_preset(1)` | 저장된 칭호. 누락 시 현재 칭호로 대체하지 않음 |
| `item.stats` | `item_total_option`의 장비 자체 합계. 잠재·소울을 추가 합산하지 않음 |
| `item.options.base` | 기본 옵션 |
| `item.options.additional` | 추가옵션 |
| `item.options.starforce` | 스타포스 옵션 |
| `item.options.scroll` | API `item_etc_option`. 표시 라벨은 **주문서 등 기타**이며 전부 주문서로 단정하지 않음 |
| `item.options.exceptional` | 익셉셔널 옵션 |
| `item.potential`, `item.additional_potential` | 등급, 원문 `lines`, 각 줄의 `effects`와 `parse_status` |
| `item.soul` | 이름, 원문 `option_text`, 별도 `attack_power` / `magic_power`, 소울 잠재능력 |

스탯 블록에는 `str`, `dex`, `int`, `luk`, `max_hp`, `max_mp`, `attack_power`, `magic_power`, `defense`, `speed`, `jump`, `all_stat_percent`, `max_hp_percent`, `max_mp_percent`, `damage_percent`, `boss_damage_percent`, `ignore_defense_percent`가 있습니다. Python의 `int`는 Java에서 `intelligence()`입니다. `%` 값 6은 6%이며 0.06이 아닙니다. 알려지지 않은 필드는 `detail` 원본에 남습니다.

`item.stats.to_rows()` / `toRows()`는 표시 순서·라벨·부호·단위를 넣은 행을 반환합니다. 기본적으로 0과 부재 값은 숨기며, Python `include_zero=True`, Java `toRows(true)` / `toText(true)`로 0도 표시할 수 있습니다.

`item.to_view()`의 핵심 필드는 다음과 같습니다. 아래 수치는 합성 예제입니다.

```json
{
  "name": "합성 튜너",
  "display_name": "합성 튜너 (+9)",
  "icon_url": "https://example.invalid/icon.png",
  "category": "튜너",
  "required_level": 250,
  "badges": [{"key": "STARFORCE", "label": "스타포스", "value": 25, "text": "25성"}],
  "stat_rows": [{
    "key": "STR", "label": "STR", "value": "385", "unit": "FLAT",
    "value_text": "+385", "text": "STR +385", "breakdown_text": "190 +159 +36",
    "components": [
      {"source": "BASE", "label": "기본", "value": "190", "text": "190"},
      {"source": "STARFORCE", "label": "스타포스", "value": "159", "text": "+159"},
      {"source": "ADDITIONAL", "label": "추가옵션", "value": "36", "text": "+36"}
    ]
  }],
  "potential": {
    "title": "잠재능력", "grade": "LEGENDARY", "grade_label": "레전드리",
    "grade_short_label": "레전", "summary_text": "공격력 +13%",
    "lines": [{"slot": 1, "text": "공격력 +13%", "parse_status": "KNOWN"}]
  },
  "additional_potential": null,
  "soul": null,
  "diagnostics": []
}
```

전체 결과에는 강화 정보 `enhancement`, 외형 `appearance`, 성장 `growth`, 만료 원문 등도 있습니다. `stat_rows`의 합계는 원본 값이며, 구성 요소의 합으로 다시 계산하지 않습니다. `item.stats`와 `item.options`는 모델에서 읽고, 장비 화면 DTO에서는 중복 대신 `stat_rows`를 사용합니다. 색상·HTML·CSS는 넣지 않으므로 서비스가 `source`, `grade`를 기준으로 스타일을 정하면 됩니다.

### 심볼과 유니온

```python
symbol = character.symbols.items[0]
symbol.stats.str                    # Decimal 또는 None
symbol.growth.current               # 보유 성장 수량
symbol.growth.required              # API가 제공한 필요 수량
symbol.to_view()['stat_rows']       # STR +700 같은 표시 행

raider = character.union.raider
for block in raider.blocks:
    print(block.job, block.level, block.control_point.x)
    for point in block.positions:
        print(point.x, point.y)
for line in raider.member_effects:
    print(line.text, line.effects)   # 조건을 보존한 해석; 합산하지 않음

crystal = character.union.artifact.crystals[0]
print(crystal.name, crystal.option_1, crystal.expires_at)
member = character.union.champion.members[0]
print(member.name, member.grade, member.badges[0].text)
```

심볼의 `stat_rows`, `level_text`, `force_text`, `growth`를 바로 렌더링할 수 있습니다. 심볼의 드롭률·메소·경험치 옵션은 각각 `drop_rate_percent`, `meso_rate_percent`, `exp_rate_percent`로 접근합니다. 유니온 배치 좌표는 실제 x/y이고, 블록 이동이나 보드 좌표 변환은 하지 않습니다. `validity_flag`, 잠재 `flag` 등 API 플래그는 원문 문자열을 유지합니다.

### 프론트엔드에서 렌더링

백엔드가 `character.to_view()` / `character.toView()`를 JSON 응답으로 반환하면, 프론트엔드는 같은 형태로 받습니다. 아래는 React의 간단한 장비 툴팁 예제입니다.

```jsx
function ItemTooltip({ item }) {
  return <section>
    <h3>{item.display_name}</h3>
    {item.icon_url && <img src={item.icon_url} alt={item.name ?? "장비"} />}
    {item.badges.map(b => <span key={b.key}>{b.text}</span>)}
    {item.stat_rows?.map(row => <div key={row.key}>
      <span>{row.text}</span>
      {row.components.length > 0 && <span> (
        {row.components.map(c => <span key={c.source} className={`stat-${c.source.toLowerCase()}`}>
          {c.text}{" "}
        </span>)}
      )</span>}
    </div>)}
    {[item.potential, item.additional_potential, item.soul?.potential]
      .filter(Boolean).map(section => <div key={section.title}>
        <strong className={`grade-${section.grade?.toLowerCase()}`}>
          {section.title}: {section.grade_label}
        </strong>
        {section.lines.map(line => <div key={line.slot}>{line.text}</div>)}
      </div>)}
    {item.soul && <div>{item.soul.summary_text}</div>}
  </section>;
}
```

목록에서는 `item.name`, `badges`, `potential.summary_text`를, 상세 화면에서는 `display_name`, `stat_rows`, `potential.lines`를 사용하면 됩니다. 해석하지 못한 문장도 원문으로 표시할 수 있습니다. 전투력 증감, 추가옵션의 ‘1추’ 같은 등급 판정은 제공하지 않습니다.

### 부재·오류·정밀도 계약

- 미선택·수집 실패·날짜 미지원 소스를 직접 읽으면 `DOCUMENT_UNAVAILABLE`입니다. `character.sheet.document(Source...)`로 원인과 상태를 확인할 수 있습니다.
- `character.to_view()`는 `view_version: "1"`, `sources`의 상태·`error_code`·진단과 데이터 섹션을 반환합니다. 사용 불가능한 섹션은 `null`, 상태는 각각 `NOT_REQUESTED`, `FETCH_FAILED`, `UNSUPPORTED`입니다. 실제 빈 목록 `[]`와 구분하세요.
- 수집한 객체 안의 누락·null 필드는 간편 모델에서 `None` / `null`입니다. 목록 필드도 마찬가지이며, 정상적인 빈 목록은 Python `()`, Java 빈 List, JSON `[]`입니다. `item.detail.field(...)` / `detail().field(...)`로 원래 상태를 구분합니다.
- 숫자·객체·배열 형식이 잘못되면 직접 접근은 `INVALID_FIELD`입니다. 화면 변환은 해당 필드를 `null`로 두고 `diagnostics`에 경로를 남깁니다. 중첩 진단은 해당 하위 객체에도 있으며, 0으로 보정하지 않습니다.
- 없는 프리셋은 `FIELD_UNAVAILABLE`, 번호 0 등 잘못된 요청은 `INVALID_REQUEST`입니다. 프리셋의 명시적인 null은 null로 유지합니다.
- 계산용 수치는 Python Decimal / Java BigDecimal입니다. JSON에서는 정밀도 유지를 위해 스탯·경험치·성장 수량 등을 **문자열**로 보냅니다. 레벨·프리셋 번호·좌표처럼 계약이 정수인 필드는 JSON 정수입니다. 표시에는 `value_text`, `text`, `level_text`를 쓰면 됩니다.
- 모든 전용 모델은 API 필드를 읽는 편의 계층입니다. 아직 전용 속성이 없는 신규 필드도 `detail` 및 기존 Document의 원본에서 접근할 수 있습니다. 자연어 해석은 기존 보수적 파서를 사용하며, 모든 문장 해석을 보장하지 않습니다.

기존 원본 중심 API는 아래에 그대로 설명합니다. 새 서비스는 위 간편 API로 시작하고, 특별한 원본 필드나 세부 실패 상태가 필요할 때 사용하면 됩니다.

## 전체·선택·그룹 조회

아래 조각의 `client`는 이미 생성한 클라이언트이며, Python에서는 `async def` 안에서 실행합니다.

### 선택 조회

```python
request = CharacterRequest(nickname="캐릭터명")
sheet = await client.get_character_selected(
    request, {Source.EQUIPMENT, Source.SYMBOLS, Source.ABILITIES}
)
```

```java
var request = CharacterRequest.nickname("캐릭터명");
var sheet = client.getCharacterSelected(
    request, Set.of(Source.EQUIPMENT, Source.SYMBOLS, Source.ABILITIES)
).join();
```

- 소스는 문자열 대신 `Source` enum을 전달합니다. 빈 집합은 `INVALID_REQUEST`입니다.
- 선택한 소스만 조회합니다. 심볼만 선택하면 기본 정보·장비·스킬을 추가 조회하지 않습니다.
- 닉네임 입력은 이름→OCID 조회가 1회 추가됩니다. OCID 입력은 이를 생략합니다.
- 하나의 소스를 선택하면 그 API가 제공하는 current·프리셋·추가 슬롯 등을 함께 보존합니다. 프리셋마다 따로 요청하지 않습니다.
- 조회는 여러 HTTP 요청으로 이루어지며, 모든 문서가 완전히 같은 순간의 상태임을 보장하지 않습니다.

### 전체 조회

```python
sheet = await client.get_character(CharacterRequest(nickname="캐릭터명"))
for source, document in sheet.documents.items():
    print(source.value, document.status.value)
```

```java
var sheet = client.getCharacter(CharacterRequest.nickname("캐릭터명")).join();
sheet.documents().forEach((source, document) ->
    System.out.println(source + " " + document.status()));
```

전체 요청은 아래 27개 소스를 포함합니다. 날짜 범위 밖 소스는 `UNSUPPORTED`로 반환하며 호출하지 않습니다. 구현된 링 날짜 규칙상 현재(2026-03-19 이후)는 26개 문서 HTTP 요청 + 닉네임 해석 1회입니다. 재시도는 추가 호출입니다. 결과의 개별 상태를 확인하세요.

### 그룹 조회

| 그룹 | 실제 조회하는 소스 | 포함하지 않는 소스 |
|---|---|---|
| `Source.SKILLS` | `SKILL_0`, `SKILL_HYPER_PASSIVE`, `SKILL_HYPER_ACTIVE`, `SKILL_5`, `SKILL_6` | `LINK_SKILLS`, `HYPER_STATS`, `V_MATRIX`, `HEXA_CORES`, `HEXA_STATS`, 1~4차 스킬 |
| `Source.UNION` | `UNION_BASIC`, `UNION_RAIDER`, `UNION_ARTIFACT`, `UNION_CHAMPION` | 캐릭터 스킬·장비 등 나머지 소스 |

```python
sheet = await client.get_character_selected(
    CharacterRequest(nickname="캐릭터명"),
    {Source.SKILLS, Source.LINK_SKILLS, Source.UNION},
)
sixth = sheet.document(Source.SKILL_6)
raider = sheet.document(Source.UNION_RAIDER)
```

```java
var sheet = client.getCharacterSelected(
    CharacterRequest.nickname("캐릭터명"),
    Set.of(Source.SKILLS, Source.LINK_SKILLS, Source.UNION)
).join();
var sixth = sheet.document(Source.SKILL_6);
var raider = sheet.document(Source.UNION_RAIDER);
```

`SKILLS`와 `UNION`은 요청을 펼치는 그룹입니다. **`sheet.document(Source.SKILLS)`처럼 그룹으로 문서를 읽으면 안 됩니다.** 결과에는 개별 소스만 존재합니다. `SKILLS`와 `SKILL_6`를 같이 선택해도 6차 요청은 한 번입니다. 확장 목록이 필요하면 Python의 `expand_sources({Source.SKILLS})` 또는 Java의 `SourceRegistry.expand(Set.of(Source.SKILLS))`를 사용합니다.

## 선택 가능한 소스 27개

두 언어의 `Source` 이름은 같습니다. 먼저 `doc = sheet.document(Source.원하는소스)`로 문서를 꺼내고 `FETCHED`인지 확인하세요. 아래 `v`는 Python의 `doc.view`, Java의 `doc.view()`입니다. 표의 `text`, `number`, `object`, `collection`은 두 언어 모두 같은 이름의 메서드이며 **FieldValue를 반환하므로 상태를 확인한 뒤 value를 읽습니다.**

### 기본 정보·스탯·장비

| Source | 가져오는 내용 | 주요 접근 방법 |
|---|---|---|
| `BASIC` | 이름, 직업, 레벨, 월드, 길드 등 기본 정보 | `v.text("character_name")`, `v.text("character_class")`, `v.number("character_level")`, `v.text("world_name")` |
| `REPORTED_STATS` | API가 보고한 최종 스탯과 잔여 AP | `doc.collection("final_stat")` → 각 원소의 `text("stat_name")`, `number("stat_value")`; `v.number("remain_ap")`. 라이브러리가 재계산한 값이 아님 |
| `HYPER_STATS` | 하이퍼스탯 각 프리셋, 사용 번호, 잔여 포인트 | `doc.collection("hyper_stat_preset_1")`; `v.number("use_preset_no")`, `v.number("hyper_stat_preset_1_remain_point")` |
| `PROPENSITY` | 6종 성향 레벨 | `v.number("charisma_level")` 카리스마, `sensibility_level` 감성, `insight_level` 통찰력, `willingness_level` 의지, `handicraft_level` 손재주, `charm_level` 매력 |
| `ABILITIES` | 어빌리티 등급·현재 줄·프리셋·명성치 | `doc.collection("ability_info")`, `v.text("ability_grade")`, `v.number("remain_fame")`; `v.object("ability_preset_1")` |
| `EQUIPMENT` | 일반 장비, 칭호, 장비 프리셋, 직업별 추가 장비 | `doc.equipment()` 현재 장비, `doc.equipment(1)` 프리셋1; `v.object("title")`, `doc.collection("dragon_equipment")`, `doc.collection("mechanic_equipment")` |
| `CASH` | 캐시 장비 기본/프리셋, 추가 외형, 월드 공유 마스터라벨 | `doc.collection("cash_item_equipment_base")`, `doc.collection("cash_item_equipment_preset_1")`, `doc.collection("additional_cash_item_equipment_base")`, `doc.collection("world_share_master_label_base")` |
| `SYMBOLS` | 심볼 이름·레벨·포스·성장·보고 스탯 | `doc.symbols()` 또는 `doc.collection("symbol")` → 각 원소의 `text("symbol_name")`, `number("symbol_level")`, `number("symbol_force")`, `number("symbol_str")` 등 |
| `SET_EFFECTS` | 세트 효과 및 장착 정보 | `doc.collection("set_effect")`; 각 원소의 상세 필드는 `field`/`object`/`entities`로 접근. 효과를 자동 합산하지 않음 |
| `ANDROID` | 안드로이드 정보·외형·캐시 장비·프리셋 | `v.text("android_name")`, `v.object("android_hair")`, `doc.collection("android_cash_item_equipment")`, `v.object("android_preset_1")` |
| `PETS` | 펫 슬롯1~3, 펫 장비·자동 스킬, 월드 공유 펫 | `v.text("pet_1_name")`, `v.object("pet_1_equipment")`, `v.object("pet_1_auto_skill")`; 숫자를2·3으로 바꿔 다른 슬롯 접근. `v.object("world_share_pet_1_equipment")`, `doc.collection("world_share_pet_equipment_preset")` |

`equipment()`는 **EQUIPMENT 전용**, `symbols()`는 **SYMBOLS 전용**입니다. CASH나 PETS에 `equipment()`를 호출하지 마세요. 펫 전체를 담는 `pet_equipment` 배열도 없습니다. 펫 슬롯의 실제 필드 이름을 사용합니다.

### 스킬·코어·기타

| Source | 가져오는 내용 | 주요 접근 방법 |
|---|---|---|
| `SKILL_0` | 0차 스킬, 요청 등급 `0` | `doc.collection("character_skill")` → `text("skill_name")`, `number("skill_level")`, `text("skill_effect")` |
| `SKILL_HYPER_PASSIVE` | 하이퍼 패시브 스킬, 등급 `hyperpassive` | `doc.collection("character_skill")`; 필드 접근은 `SKILL_0`과 같음 |
| `SKILL_HYPER_ACTIVE` | 하이퍼 액티브 스킬, 등급 `hyperactive` | `doc.collection("character_skill")`; 필드 접근은 `SKILL_0`과 같음 |
| `SKILL_5` | 5차 스킬, 등급 `5` | `doc.collection("character_skill")`; 코어 장착 구조는 별도 `V_MATRIX` 선택 |
| `SKILL_6` | 6차 스킬, 등급 `6` | `doc.collection("character_skill")`; HEXA 코어·스탯 구조는 별도 소스 선택 |
| `LINK_SKILLS` | 적용 링크 스킬, 본인 링크 스킬, 각 프리셋 | `doc.collection("character_link_skill")`, `v.object("character_owned_link_skill")`, `doc.collection("character_link_skill_preset_1")` |
| `V_MATRIX` | V 코어 장착 정보·프리셋·잔여 슬롯 강화 포인트 | `doc.collection("character_v_core_equipment")`, `doc.collection("character_v_core_equipment_preset_1")`, `v.number("character_v_matrix_remain_slot_upgrade_point")` |
| `HEXA_CORES` | HEXA 매트릭스 코어 장착 정보 | `doc.collection("character_hexa_core_equipment")` |
| `HEXA_STATS` | HEXA 스탯 코어 및 저장 프리셋 | `doc.collection("character_hexa_stat_core")`, `character_hexa_stat_core_2`, `character_hexa_stat_core_3`; 저장 프리셋은 `preset_hexa_stat_core`, `_2`, `_3` |
| `OTHER_STATS` | API에서 제공하는 기타 스탯 자료 | `doc.collection("other_stat")`; 각 원소의 하위 구조는 `field`/`object`/`entities`로 접근 |
| `RING_EXCHANGE` | 과거 링 익스체인지 등록 특수 반지 | `v.text("special_ring_exchange_name")`, `v.number("special_ring_exchange_level")`; **2025-08-21~2026-03-18**만 지원 |
| `RING_RESERVE` | 예비 특수 반지 | `v.text("special_ring_reserve_name")`, `v.number("special_ring_reserve_level")`; **2026-03-19 이후** 지원 |

1~4차 및 중간 전직 스킬은 현재 라이브러리의 선택 목록에 없습니다. 스킬 등급 query parameter는 라이브러리가 Source에 맞춰 넣으므로 직접 전달하지 않습니다. 과거 스킬을 현재 데이터로 대체하지 않습니다.

### 유니온

| Source | 가져오는 내용 | 주요 접근 방법 |
|---|---|---|
| `UNION_BASIC` | 유니온 레벨·등급, 아티팩트 레벨/경험치/포인트 | `v.number("union_level")`, `v.text("union_grade")`, `v.number("union_artifact_level")` |
| `UNION_RAIDER` | 공격대원 효과·점령 효과·블록 배치·프리셋 | `v.field("union_raider_stat")`, `v.field("union_occupied_stat")`는 **문자열 배열**; `doc.collection("union_block")`는 객체 배열; `v.object("union_raider_preset_1")` |
| `UNION_ARTIFACT` | 아티팩트 효과·크리스탈·잔여 AP | `doc.collection("union_artifact_effect")`, `doc.collection("union_artifact_crystal")`, `v.number("union_artifact_remain_ap")` |
| `UNION_CHAMPION` | 유니온 챔피언과 배지 종합 정보 | `doc.collection("union_champion")`, `doc.collection("champion_badge_total_info")` |

유니온 API도 캐릭터의 OCID로 조회합니다. 별도 유니온 ID는 입력하지 않습니다.

## 필드와 배열 읽기

객체 접근 메서드의 대응은 다음과 같습니다.

| 목적 | Python | Java |
|---|---|---|
| 소스 문서 | `sheet.document(Source.BASIC)` | `sheet.document(Source.BASIC)` |
| root view | `doc.view` 또는 `doc.primary()` | `doc.view()` 또는 `doc.primary()` |
| 문자열 | `view.text("field")` | `view.text("field")` |
| 정확한 수치 | `view.number("field")` → `Decimal` | `view.number("field")` → `BigDecimal` |
| offset 포함 시각 | `view.timestamp("field")` | `view.timestamp("field")` |
| 하위 객체 | `view.object("field")` → `FieldValue[EntityView]` | `view.object("field")` → `FieldValue<EntityView>` |
| 배열의 원소 view | `view.entities("field")` | `view.entities("field")` |
| root 배열의 원소 view | `doc.collection("field")` | `doc.collection("field")` |
| 원본 트리 | `doc.raw` | `doc.raw()` |
| 원본 필드 | `view.field("field")` | `view.field("field")` |

`primary()`는 대표 배열을 자동 선택하는 함수가 아니라 root view입니다. 필드 메서드는 `/title/title_name` 같은 경로를 받지 않습니다. `object("title")`로 내려가서 `text("title_name")`을 호출합니다.

### FieldValue 상태

| 상태 | 뜻 | 처리 |
|---|---|---|
| `VALUE` | 요청한 타입으로 읽을 수 있는 값. `0`, 빈 문자열, 빈 배열도 포함 | `.value` / `.value()` 사용 |
| `MISSING` | 응답에 필드 자체가 없음 | 없음으로 처리; 임의의0으로 바꾸지 않음 |
| `NULL` | 응답이 JSON null | MISSING과 구분 |
| `INVALID` | 원본은 있지만 요청한 타입으로 읽을 수 없음 | `.raw` / `.raw()`와 `.pointer` / `.pointer()`로 확인 |

Python은 `field.state is FieldState.VALUE`, Java는 `field.state() == FieldValue.State.VALUE`입니다. `if field.value`처럼 값의 참/거짓으로 상태를 판단하면 정상적인0을 놓칩니다.

### 장비의 하위 옵션 읽기

다음 조각은 이미 조회한 `equipment` 문서가 FETCHED인 상황입니다.

```python
items = equipment.equipment()
if items.state is FieldState.VALUE:
    for item in items.value:
        base = item.object("item_base_option")
        if base.state is FieldState.VALUE:
            strength = base.value.number("str")
            if strength.state is FieldState.VALUE:
                print(strength.value, strength.pointer)
```

```java
var items = equipment.equipment();
if (items.state() == FieldValue.State.VALUE) {
    for (var item : items.value()) {
        var base = item.object("item_base_option");
        if (base.state() == FieldValue.State.VALUE) {
            var strength = base.value().number("str");
            if (strength.state() == FieldValue.State.VALUE) {
                System.out.println(strength.value() + " " + strength.pointer());
            }
        }
    }
}
```

장비 옵션 객체로는 `item_base_option`, `item_add_option`, `item_etc_option`, `item_starforce_option`, `item_total_option`, `item_exceptional_option`을 읽을 수 있습니다. 응답에 없는 필드는 MISSING이며, 특정 객체가 항상 존재한다고 가정하지 않습니다. 잠재능력 문구는 `item.text("potential_option_1")`, 추가잠재는 `item.text("additional_potential_option_1")`처럼 읽습니다.

### 문자열 배열과 원본 타입

`collection()` / `entities()`의 각 원소는 EntityView입니다. 원소가 문자열이면 `.text("name")`처럼 객체 필드를 읽을 수 없습니다. 유니온 문구 배열은 원본 필드에서 읽을 수 있습니다.

```python
from maple_library import Kind

lines = raider.view.field("union_raider_stat")
if lines.state is FieldState.VALUE and lines.raw.kind is Kind.ARRAY:
    for raw in lines.value:
        if raw.kind is Kind.STRING:
            print(raw.value)
```

```java
import org.whitedoggy.maplelibrary.RawValue;

// raider는 FETCHED인 UNION_RAIDER 문서
var lines = raider.view().field("union_raider_stat");
if (lines.state() == FieldValue.State.VALUE && lines.raw() instanceof RawValue.ArrayValue array) {
    for (var raw : array.values()) {
        if (raw instanceof RawValue.StringValue text) {
            System.out.println(text.value());
        }
    }
}
```

`field()`의 일반 값 표현은 언어별로 다릅니다. Python은 원본 scalar 또는 RawValue 원소의 tuple/mapping, Java는 RawValue입니다. 두 언어 모두 `.raw` / `.raw()`가 원본 노드입니다. 숫자는 원래 token을 보존하며 Python의 raw 숫자 `.value`도 문자열 token입니다. 계산용 수치가 필요하면 해당 필드의 `number()`를 사용하세요. raw는 일반 dict/JSON 문자열이 아니며 내장 저장·복원 API는 없습니다.

## 프리셋 읽기

프리셋 선택은 **수집 후 로컬 데이터 접근**입니다. `equipment(2)`를 읽어도 게임의 장비 설정이 바뀌거나 추가 API 요청이 발생하지 않습니다.

```python
current_items = equipment.equipment()
preset_two = equipment.equipment(2)
current_number = equipment.current_preset_number()
for field_name, value in equipment.presets().items():
    print(field_name, value.state)
```

```java
var currentItems = equipment.equipment();
var presetTwo = equipment.equipment(2);
var currentNumber = equipment.currentPresetNumber();
equipment.presets().forEach((fieldName, value) ->
    System.out.println(fieldName + " " + value.state()));
```

`presets()`는 **실제 필드 이름 → FieldValue**입니다. 숫자1·2·3을 key로 쓰는 통합 모델이 아닙니다. 이름에 `preset`이 들어간 필드를 반환하므로 프리셋 번호·잔여 포인트도 포함할 수 있습니다. 값이 배열인지 객체인지 확인해서 읽으세요.

| 소스 | 실제 프리셋 필드 | 읽는 방법 |
|---|---|---|
| `EQUIPMENT` | `item_equipment_preset_1` ~ `_3` | `doc.equipment(1)` 또는 `doc.collection("item_equipment_preset_1")` |
| `EQUIPMENT` 칭호 | `title_preset1` ~ `title_preset3` | `doc.view.object("title_preset1")` / Java `doc.view().object(...)`. **preset 뒤 밑줄 없음** |
| `HYPER_STATS` | `hyper_stat_preset_1` ~ `_3` | `doc.collection("hyper_stat_preset_1")`; 잔여 포인트는 `view.number("hyper_stat_preset_1_remain_point")` |
| `ABILITIES` | `ability_preset_1` ~ `_3` | `view.object("ability_preset_1")`, 성공 시 그 객체의 `entities("ability_info")` |
| `CASH` | `cash_item_equipment_preset_1` ~ `_3`, `additional_cash_item_equipment_preset_1` ~ `_3` | 각 필드에 `doc.collection(...)` |
| `ANDROID` | `android_preset_1` ~ `_3` | `view.object("android_preset_1")` |
| `PETS` | `world_share_pet_equipment_preset` | `doc.collection(...)` 후 각 원소의 `number("preset_no")`, `number("slot_no")`로 구분 |
| `LINK_SKILLS` | `character_link_skill_preset_1` ~ `_3` | `doc.collection(...)` |
| `LINK_SKILLS` 본인 링크 | `character_owned_link_skill_preset_1` ~ `_3` | `view.object(...)` |
| `V_MATRIX` | `character_v_core_equipment_preset_1` ~ `_5` | `doc.collection(...)` |
| `HEXA_STATS` | `preset_hexa_stat_core`, `preset_hexa_stat_core_2`, `preset_hexa_stat_core_3` | 각 필드에 `doc.collection(...)`; 첫 필드는 `_1`이 붙지 않음 |
| `UNION_RAIDER` | `union_raider_preset_1` ~ `_5` | `view.object(...)`, 성공 시 하위 필드를 읽음 |

현재 번호의 편의 접근 `current_preset_number()` / `currentPresetNumber()`는 HYPER_STATS·UNION_RAIDER에서 `use_preset_no`, 그 외에서 `preset_no`를 읽습니다. 대응 필드가 없는 시스템에는 MISSING을 반환하며 현재 번호를 추정하지 않습니다. 펫의 개별 사용 번호는 `world_share_pet_1_equipment_preset_no`처럼 슬롯별 필드를 직접 읽습니다.

프리셋이 없을 때 current로 대체하지 않습니다. 표의 번호는 현재 알려진 응답 이름이며 새 필드는 raw에 보존됩니다. 전역적으로 모든 시스템에 프리셋이3개라고 가정하지 마세요.

## OCID 재사용과 과거 조회

이미 OCID가 있으면 이름 조회를 생략할 수 있습니다. 하나의 요청에 nickname과 ocid를 동시에 넣지 않습니다.

```python
from datetime import date

previous = await client.get_character_selected(
    CharacterRequest(ocid=sheet.ocid, on_date=date(2026, 10, 1)),
    {Source.EQUIPMENT, Source.SYMBOLS},
)
```

```java
import java.time.LocalDate;

var previous = client.getCharacterSelected(
    CharacterRequest.ocid(sheet.ocid()).on(LocalDate.of(2026, 10, 1)),
    Set.of(Source.EQUIPMENT, Source.SYMBOLS)
).join();
```

- 날짜를 생략하면 현재 조회입니다. 오늘 날짜를 명시하는 것과 다릅니다.
- 날짜는 KST 기준 달력 날짜이며 오늘·미래는 `INVALID_REQUEST`로 거절합니다.
- 과거 지원 여부는 소스와 날짜에 따라 다릅니다. 지원하지 않는 자료를 현재 자료로 채우지 않습니다.
- 링 교환/예비 특수 반지의 명시적 지원 기간 밖은 HTTP 없이 `UNSUPPORTED`, 오류 코드 `DATE_NOT_SUPPORTED`, attempts=0입니다. strict 모드에서도 이 상태로 반환합니다.
- 응답 날짜 차이는 `sheet.fetch_diagnostics` / `sheet.fetchDiagnostics()`의 `SOURCE_DATE_MISMATCH` 등으로 확인합니다.
- 문서별 수집 시각은 `doc.fetched_at` / `doc.fetchedAt()`입니다. 클라이언트의 수집 시각과 서버 응답의 `date` 필드는 구분합니다.

## 실패와 진단

### 요청 정책

기본은 strict입니다. 선택한 문서의 HTTP 실패·잘못된 구조가 있으면 조회 전체가 예외로 종료됩니다. partial 모드에서는 성공 자료와 실패 문서를 함께 반환합니다. 인증·권한 오류는 partial에서도 전체 실패입니다.

```python
from maple_library import ApiError

try:
    sheet = await client.get_character_selected(
        CharacterRequest(nickname="캐릭터명", allow_partial=True),
        {Source.EQUIPMENT, Source.SYMBOLS},
    )
    for source, doc in sheet.documents.items():
        if doc.status is DocumentStatus.FETCHED:
            print(source.value, "사용 가능")
        else:
            print(source.value, doc.status.value, doc.error.code if doc.error else None)
except ApiError as error:
    print(error.code, error.source, error.attempts)
```

```java
import java.util.concurrent.CompletionException;
import org.whitedoggy.maplelibrary.ApiException;

try {
    var sheet = client.getCharacterSelected(
        CharacterRequest.nickname("캐릭터명").partial(),
        Set.of(Source.EQUIPMENT, Source.SYMBOLS)
    ).join();
    sheet.documents().forEach((source, doc) -> {
        if (doc.status() == Document.Status.FETCHED) {
            System.out.println(source + " 사용 가능");
        } else {
            System.out.println(source + " " + doc.status() + " " +
                (doc.error() == null ? "" : doc.error().code()));
        }
    });
} catch (CompletionException error) {
    if (error.getCause() instanceof ApiException api) {
        System.out.println(api.code() + " " + api.source() + " " + api.attempts());
    } else {
        throw error;
    }
}
```

잘못된 입력은 Python `MapleError`, Java `MapleException`으로 호출 즉시 발생할 수도 있습니다. 비동기 수집 실패와 구분하세요. Python 취소는 `asyncio.CancelledError`, Java 취소는 future cancellation이며 partial 성공으로 바꾸지 않습니다.

### 문서 상태

| 상태 | 의미 |
|---|---|
| `FETCHED` | 조회·문서 구조 확인 성공. 개별 필드·옵션 진단은 별도로 존재할 수 있음 |
| `NOT_REQUESTED` | 선택하지 않은 소스를 `sheet.document(...)`로 읽은 결과. 요청 자체가 없었음 |
| `FETCH_FAILED` | partial에서 수집/구조 실패. `error`와 `attempts` 확인 |
| `UNSUPPORTED` | 구현이 명시적으로 아는 날짜 지원 범위 밖. 없는 데이터를 의미하는 빈 성공 문서가 아님 |

`sheet.documents` / `sheet.documents()`에는 수집 대상 문서만 있습니다. 미선택 소스를 문서 목록에서 자동 생성하지 않습니다. `FETCHED`가 아닌 문서에 `.view`/`.view()`를 쓰면 `DOCUMENT_UNAVAILABLE`입니다. 파싱 가능한 구조 오류라면 `FETCH_FAILED.raw`에 원본이 남을 수 있지만, 네트워크 실패·잘못된 JSON은 raw가 없을 수 있습니다.

주요 오류 코드는 `AUTHENTICATION_FAILED`, `PERMISSION_DENIED`, `INVALID_IDENTIFIER`, `UPSTREAM_INVALID_REQUEST`, `RATE_LIMITED`, `DATA_NOT_READY`, `UPSTREAM_UNAVAILABLE`, `REQUEST_TIMEOUT`, `DEADLINE_EXCEEDED`, `INVALID_JSON`, `INVALID_DOCUMENT`, `RESOURCE_LIMIT_EXCEEDED`입니다. 예외 메시지에 키나 응답 본문을 넣지 않습니다. API의400 응답을 모두 “캐릭터 없음”이나 정상적인 빈 자료로 바꾸지 마세요.

### 해석·스키마 진단과 품질

`doc.diagnostics()`는 두 언어 모두 제공합니다.

- `UNKNOWN_FIELD`: 아직 알려지지 않은 필드. raw에는 남아 있습니다.
- `SCHEMA_TYPE_DIFFERENCE`: 기대한 타입과 실제 타입이 다릅니다.
- `OBSERVED_SCHEMA_EXTENSION`: 공식 명세와 다르지만 실응답 확인으로 별도 지원하는 구조입니다.

`sheet.quality()`는 두 언어 모두 아래 문자열 key를 가진 map을 반환합니다.

| key | true 조건 |
|---|---|
| `requested_complete` | 요청한 모든 개별 문서가 FETCHED |
| `structure_valid` | 요청한 모든 문서가 구조 검사 통과 |
| `all_sources` | 요청을 펼친 소스 집합이 전체27개와 같음 |
| `interpretation_complete` | 요청 문서가 모두 FETCHED이고 발견한 옵션이 모두 KNOWN |

전체 조회에 기간 밖 링이 포함되면 `all_sources=true`여도 `requested_complete=false`일 수 있습니다. 품질 map만 보고 전체 요청의 HTTP 성공/실패를 단정하지 말고 문서 상태를 함께 확인하세요. `interpretation_complete`는 임의의 모든 자연어를 이해했다는 보장이 아닙니다.

## 옵션 해석과 비교

### 옵션 해석

개별 문구를 직접 해석할 수 있습니다. API 요청은 발생하지 않습니다.

```python
from maple_library import decode_options

option = decode_options("공격 시 20% 확률로 데미지 16% 증가")[0]
print(option.status, option.targets, option.value, option.unit)
print(option.conditions, dict(option.expression))
# KNOWN, ('DAMAGE',), Decimal('16'), PERCENT
# ON_ATTACK 및 CHANCE_PERCENT=20; 기대값 3.2로 계산하지 않음
```

```java
import org.whitedoggy.maplelibrary.OptionDecoder;

var option = OptionDecoder.decode("공격 시 20% 확률로 데미지 16% 증가").getFirst();
System.out.println(option.status() + " " + option.targets() + " " + option.value() + " " + option.unit());
System.out.println(option.conditions() + " " + option.expression());
```

문서에서 옵션을 모으려면 Python `doc.decoded_options()`, Java `doc.decodedOptions()`를 사용합니다. 각 LocatedOption의 `pointer`는 원본 필드 경로이고 `option`은 해석 결과입니다. Python에서는 속성, Java에서는 `pointer()`·`option()` 접근입니다.

`KNOWN`, `UNKNOWN`, `AMBIGUOUS`를 구분합니다. 모르는 문구를0이나 무조건부 효과로 바꾸지 마세요. `raw_text` / `rawText()`와 `start`, `end`는 원문과 Unicode code-point 구간입니다. Java 문자열의 substring 인덱스와는 다를 수 있습니다. AP 비례·레벨 비례·중첩당 효과는 expression으로 남으며 값이 없을 수도 있습니다. 조건 충족·만료·확률 평균·캐릭터 총 스탯 계산은 소비자 책임입니다.

### 시트 비교

이미 수집한 동일 OCID의 두 시트를 비교합니다. 추가 API 호출이 없으며 방향은 **after − before**입니다.

```python
diff = previous.compare_to(sheet)
for change in diff.changes:
    print(change.source.value, change.before_pointer, change.after_pointer, change.delta)
print(diff.skipped_documents, diff.diagnostics)
```

```java
var diff = previous.compareTo(sheet);
for (var change : diff.changes()) {
    System.out.println(change.source() + " " + change.beforePointer() + " " +
        change.afterPointer() + " " + change.delta());
}
System.out.println(diff.skippedDocuments() + " " + diff.diagnostics());
```

미수집·실패 문서는 삭제로 취급하지 않고 비교에서 제외합니다. `delta`는 선언된 숫자 필드에서만 제공하고, 다른 OCID나 계약 버전은 비교를 거절합니다. 파서 규칙 버전이 다르면 원본 변경은 반환하되 수치 delta는 생략합니다. 원본의 수치 변화는 캐릭터 전투력 변화가 아닙니다.

## 클라이언트 수명과 호출 제한

클라이언트는 조회마다 새로 만들기보다 재사용하고, Python `async with` / Java `try-with-resources`로 종료합니다. 종료 시 진행 요청을 취소하고 자신이 소유한 통신 자원을 정리합니다. 생성자가 API 키를 받으며 시트 객체에 키를 저장하지 않습니다.

| 기본 설정 | 값 |
|---|---|
| pacing | 5회/초, 매 재시도에도 permit 획득 |
| 동시 요청 | 최대4개 |
| 연결 timeout | 5초 |
| 요청 timeout | 10초 |
| 전체 조회 deadline | 60초 |
| 재시도 | 최대2회 추가, Retry-After 및 jitter 반영 |
| 본문 제한 | 16MiB, 설정으로 축소 가능 |

Python의 시간 설정 단위는 초, Java는 `Duration`입니다. 아래는 설정만 바꾸는 예제입니다.

```python
from maple_library import ClientConfig, RateLimiter

limiter = RateLimiter(requests_per_second=5)
config = ClientConfig(concurrency=2, request_timeout=15, deadline=90, retries=1)
# 같은 이벤트 루프에서 같은 키의 다른 클라이언트에도 limiter를 전달
client = MapleClient(api_key, config=config, limiter=limiter)
# 사용 완료 후 await client.aclose()
```

```java
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.whitedoggy.maplelibrary.ClientConfig;
import org.whitedoggy.maplelibrary.PacingRateLimiter;

var config = new ClientConfig(2, Duration.ofSeconds(15), Duration.ofSeconds(5),
    Duration.ofSeconds(90), 1, 16 * 1024 * 1024);
try (var limiter = new PacingRateLimiter(5);
     var client = new MapleClient(apiKey, config, limiter, null,
         Clock.systemUTC(), Math::random, URI.create("https://open.api.nexon.com"))) {
    // 사용. null transport는 라이브러리가 기본 JDK transport를 소유하도록 지정
}
```

여러 클라이언트가 같은 키를 쓰면 limiter도 공유해야 합산 호출량을 제어할 수 있습니다. 다른 프로세스나 다른 서비스의 호출량까지 자동으로 제어하지 않습니다. 일일 quota를 자동 보장하지도 않습니다. 주입한 transport/limiter의 종료는 호출자 책임입니다. Python은 HTTPX AsyncBaseTransport, Java는 HttpTransport를 주입해 네트워크 없는 테스트를 만들 수 있습니다.

## 코드 에이전트용 체크리스트

1. Python은 `maple_library`, Java는 `org.whitedoggy.maplelibrary`에서 실제 공개 타입을 import합니다. 프레임워크 전용 SDK 클래스나 `get_equipment()` 같은 없는 API를 만들지 않습니다.
2. 필요한 Source를 명시하고, 사용자 요구가 전체일 때만 전체 조회를 사용합니다. 스킬·유니온 그룹은 결과 문서 key가 아닙니다.
3. 새 사용 코드는 `character()`와 간편 모델부터 사용합니다. 기존 raw API를 쓸 때만 Document 상태와 FieldValue 상태를 직접 검사합니다. 0·빈 배열·null·missing을 구분합니다.
4. 화면에는 `to_view()` / `toView()`를 쓰고, 특별한 원본 접근에는 표의 실제 필드 이름을 씁니다. CASH/PETS를 EQUIPMENT와 같은 배열로 가정하지 않으며, 객체·문자열 배열을 구분합니다.
5. 프리셋은 수집 결과에서 읽습니다. 누락 시 current로 대체하거나 현재 번호를 추정하지 않습니다.
6. 키는 실행 시 주입합니다. 코드·예제·로그에 넣지 않습니다. 실제 API 이용조건과 출처 표기는 루트 README를 따릅니다.
7. 알려지지 않은 옵션은 원문과 상태를 유지합니다. 합산·적용 판단·만료 필터·전투력 계산을 이 라이브러리가 수행한다고 가정하지 않습니다.
8. 설치 가능한 버전은 현재 로컬 개발 패키지입니다. 저장소 공개와 PyPI/Maven Central 게시를 구분합니다.

## 네트워크 없이 확인하기

두 언어 모두 `Document.parse(Source.SYMBOLS, json)`으로 보유 JSON을 읽을 수 있습니다. [Python 오프라인 예제](../python/examples/offline.py), [Java 오프라인 예제](../java/examples/Offline.java)를 참고하세요.

저장소 개발 테스트는 Python `python -m pytest python/tests`, Java `./java/gradlew -p java test`로 실행합니다. Python 테스트 의존성은 `python -m pip install -e './python[test]'`로 설치합니다. 두 테스트 후 `python tools/check_conformance.py`로 공통 expected와 결과를 비교하고, 양쪽 패키지를 빌드한 뒤 `python tools/check_packages.py`로 리소스·라이선스 포함을 확인합니다. 기본 테스트에는 API 키가 필요하지 않습니다.

모델을 수정할 때는 `python/src/maple_library/presentation.json`의 공통 매핑과 `tools/generate_models.py`를 수정한 뒤 `python tools/generate_models.py`를 실행합니다. 생성된 Java/Python 접근자와 리소스는 저장소에 함께 포함되므로 사용자가 생성기를 실행할 필요는 없습니다. CI는 `--check`로 생성 파일의 일치를 검사합니다.
