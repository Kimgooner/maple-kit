# 오류·수집 계약 v0.1

오류 객체: code, scope(REQUEST/DOCUMENT/FIELD/OPTION), documentKey?, fieldPointer?, upstreamCode?, httpStatus?, retryable, attempts, sanitizedMessage. 키·전체 요청 URL·OCID·응답 본문은 기본 메시지에 넣지 않는다. 언어별 예외는 같은 code를 제공한다.

| 코드 | 자동 재시도 | 의미 |
|---|---|---|
| DATE_NOT_SUPPORTED | 없음 | 공식 링 API 날짜 범위 밖. HTTP 없이 UNSUPPORTED 문서 반환 |
| DOCUMENT_UNAVAILABLE | 없음 | 오프라인 문서 view 접근 시 FETCHED가 아님 |
| INVALID_REQUEST | 없음 | 빈 target/빈 sources/잘못된 날짜·번호 |
| AUTHENTICATION_FAILED / PERMISSION_DENIED | 없음 | 인증·권한 오류 |
| INVALID_IDENTIFIER / UPSTREAM_INVALID_REQUEST | 없음 | upstream 00003/00004 등. 캐릭터 없음으로 단정하지 않음 |
| INVALID_API_ROUTE_OR_SCOPE | 없음 | upstream 00006. 경로 또는 게임 키 범위 |
| RATE_LIMITED | 제한적 | 429/00007; 일일 한도라고 명시되면 반복하지 않음 |
| DATA_NOT_READY | 기본 없음 | 00009. 자동 날짜 변경 없음; 호출자가 새 조회 가능 |
| UPSTREAM_UNAVAILABLE | 제한적 | 5xx, 00001/00010/00011 |
| TRANSPORT_ERROR / REQUEST_TIMEOUT | 제한적 | 일시 연결 실패/요청 timeout. 잘못된 URL·TLS 검증 실패는 재시도 안 함 |
| DEADLINE_EXCEEDED | 없음 | 전체 budget 소진 |
| INVALID_JSON / INVALID_DOCUMENT / INVALID_FIELD | 없음 | 파싱/구조/필드 오류. raw가 있을 때 보존 |
| RESOURCE_LIMIT_EXCEEDED | 없음 | 문서 크기/깊이/숫자 길이 한도. 조용히 잘라내지 않음 |
| SUBJECT_MISMATCH / INCOMPATIBLE_CONTRACT | 없음 | 비교 대상/버전 불일치 |
| UNKNOWN_OPTION / AMBIGUOUS_OPTION / SOURCE_DATE_MISMATCH / DUPLICATE_ENTITY_KEY / PARSER_VERSION_DIFFERENCE | 없음 | 진단. 수집 실패와 구별 |

upstream code mapping은 [공식 가이드](https://openapi.nexon.com/guide/ai-agent/)를 참고하며 endpoint별 응답 검증으로 확정한다. 00004는 여러 원인이 가능하므로 정상 부재로 일괄 변환하지 않는다. 등록 안 된 하위 자료가 정상 ABSENT임을 입증한 문서별 규칙만 예외로 둔다.

## 실행 정책 결정

- 기본 pacing 5 requests/s, burst 1, 동시 요청 최대 4. 동일 애플리케이션의 다른 클라이언트와 limiter를 공유해야 전체 한도를 지킬 수 있다. 프로세스 밖 quota를 보장하지 않으며 일일 한도를 자동 추정하지 않는다.
- 기본 connect timeout 5초, 요청 10초, 전체 조회 deadline 60초. 모두 설정 가능. retry 최대 2회(최초 포함 총3회), 300ms×2^attempt 상한3초 full jitter. Retry-After가 있으면 그 대기 시간 이상을 존중하되 deadline 밖이면 종료한다.
- 매 시도마다 permit을 획득한다. retry는 단일 수집 계층이 소유해 이중 retry를 막는다. 미래 대기와 jitter는 주입 scheduler/random으로 테스트한다.
- STRICT는 선택 문서 실패 시 원자적 시트 성공을 반환하지 않고 typed 오류와 안전한 실패 요약을 반환한다. 남은 작업은 취소한다. ALLOW_PARTIAL은 문서별 오류와 성공 자료를 반환하되 인증/권한 실패는 조회 전체 실패로 처리한다.
- 호출자 취소는 Java future cancellation/Python CancelledError로 전파한다. partial 성공이나 일반 오류로 삼키지 않는다. 예약·재시도·진행 HTTP를 best effort 취소하며 이미 서버가 처리한 호출 취소를 보장하지 않는다.
- 클라이언트는 재사용한다. 소유한 HTTP client/executor만 종료하고 외부 주입 자원은 종료하지 않는다. redirect는 기본 비활성화한다.
- 기본 문서 본문 한도16 MiB(압축 해제 후), JSON 깊이128, 숫자 토큰1024문자. 초과는 오류이고 본문 한도는 설정으로 축소만 가능하다. 실제 최대 장비 응답으로 한도를 검증하기 전 성능 보장하지 않는다.
