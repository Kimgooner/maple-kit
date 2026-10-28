# 폴더 구조

```text
maple-library/
├── AGENTS.md                  # 라이브러리 전담 작업 지침
├── README.md
├── .gitignore
├── .plan/general/             # 단계별 구현 계획
├── docs/                      # 범위·구조·향후 소비자 안내
├── contracts/                 # 언어 중립 모델·오류·커버리지·호환 계약
├── fixtures/
│   ├── raw/                   # 공통 합성 API 응답
│   ├── expected/              # 검토된 정규화 기대 결과
│   └── cases/                 # 입력/기대값/계약 버전 연결
├── java/
│   ├── src/main/java/         # 공개 API 및 내부 구현
│   ├── src/main/resources/    # 파서 사전 등 배포 리소스
│   ├── src/test/java/
│   ├── src/test/resources/    # Java 전용 테스트 리소스
│   └── examples/
├── python/
│   ├── src/                  # 이름 확정 후 import 패키지 생성
│   ├── tests/
│   └── examples/
└── tools/                     # 향후 교차 적합성·패키지 검증 도구
```

빈 디렉터리는 .gitkeep으로 유지한다. 현재 Java package는 org.whitedoggy.maplelibrary, Python import는 maple_library다. Gradle/pyproject, 합성 fixture, 교차 검증 도구가 구현되어 있다. CI와 HTTP 수집은 아직 없다.

contracts와 fixtures는 개발 시 공유한다. 배포물은 형제 디렉터리를 런타임에 읽지 않는다. 파서 사전을 공유하게 되면 각 패키지에 필요한 버전을 포함하고 패키징 검증으로 누락을 확인한다. Java/Python 간 브리지나 공통 런타임은 만들지 않는다.
