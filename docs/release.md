# 모노레포와 출시 절차

루트 java/와 python/은 독립 빌드·배포 단위다. contracts/와 fixtures/는 양쪽 구현의 공통 기준이며 docs/, tools/, .github/는 함께 관리한다. 저장소는 https://github.com/Kimgooner/maple-kit 이다.

이번 후보: Java0.1.0-dev.1 / Python0.1.0.dev1. contractVersion0.1 / parserRulesVersion0.2. 조건 문법·쉼표 분할·실응답 스키마 확장이 바뀌었으므로 이전 규칙 버전과 비교하면 기존 계약대로 수치 delta를 생략하고 파서 버전 차이를 진단한다.

로컬 검증:

```sh
(cd java && ./gradlew test build publish)
python -m pytest python/tests
python tools/check_conformance.py
python -m build python
python tools/release_check.py
```

release/artifacts.json은 현재 후보 파일의 SHA-256·크기·리소스 검증 결과다. 과거 버전 산출물이 남더라도 정확한 버전 파일만 확인한다. CI는 Python3.12/3.14, Java21을 대상으로 공통 테스트와 설치된 wheel 검증을 수행하고 산출물을 업로드한다. 원격 CI 실행 결과는 GitHub Actions에서 확인한다.

외부 게시 전 확정할 필드는 release/config.json에 null/false로 표시했다. 저장소 URL,  개발자 표기, Maven namespace 소유 확인, PyPI 프로젝트·게시자 설정이 필요하다. 현재 org.whitedoggy/maple-library는 로컬 식별자이며 공개 소유권을 확인했다는 뜻이 아니다. `python tools/release_check.py --public`은 이 필드와 개발 버전을 검사해 준비가 안 되었으면 실패한다. 성공하더라도 서명·원격 인증·실제 게시 성공을 대신 검증하지 않는다.

- PyPI: [공식 CI 게시 안내](https://packaging.python.org/en/latest/guides/publishing-package-distribution-releases-using-github-actions-ci-cd-workflows/)에 따라 저장소 생성 후 Trusted Publisher를 연결한다.
- Maven Central: [공식 요구사항](https://central.sonatype.org/publish/requirements/)의 namespace·POM 메타데이터·서명·sources/Javadoc을 갖춘다. 현재 publish는 로컬 build/repository만 사용한다.

라이선스 경계는 [API 이용 조건 검토](api-licensing.md)를 따른다. MIT는 적용했으며 저작권자 표기는 기존 Git 설정의 Kimgooner를 사용했다. 레포 생성은 사용자 담당이다.
