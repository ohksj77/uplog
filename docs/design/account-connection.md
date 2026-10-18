# 3단계 설계 — 로그인, 토스 계좌 연결, 일일 수집

데모 계좌만 보이던 서비스를 실제 사용자의 계좌로 바꾼다. 토스 API의 실제 동작은 [toss-openapi-notes.md](../toss-openapi-notes.md)를 따른다.

## 사용자 흐름

```
방문자 ──► / (데모 계좌, "샘플 계좌" 배지 + "내 계좌로 보기" 배너)
   │ 로그인 (카카오 / local 프로필은 개발용 로그인)
   ▼
로그인, 계좌 없음 ──► /connect: ① WTS에서 키 발급 안내 ② 우리 서버 IP 복사·등록 안내 ③ client_id·secret 입력
   │ 검증: 토큰 발급 + 계좌 목록 (실패 사유별 안내)
   ▼ 종합매매 계좌가 여럿이면 하나 고르기
연결됨, 역산 중 ──► 체결 전체 + 일봉 + 환율로 역산 (진행률 표시)
   ▼
연결됨 ──► / 내 계좌 로그. 매일 07:00 KST 수집. /settings/connection 에서 상태·누락일·다시 수집·연결 해제
```

- 비로그인은 데모 계좌를 본다(없으면 `ACCOUNT_NOT_CONNECTED`). 로그인했는데 연결이 없으면 `ACCOUNT_NOT_CONNECTED`이고, 화면은 연결 안내를 보여 준다.

## 모듈과 경계

| 모듈 | 담당 | 책임 |
|---|---|---|
| `user-auth` (신규) | 백엔드 A | 사용자·외부 계정, 세션(JWT, HttpOnly 쿠키), 카카오 OAuth2 로그인(env 설정 시), `local` 개발용 로그인, `CurrentUser` 해석 |
| `credential` (신규) | 백엔드 A | 키 봉투 암호화(AES-256-GCM, 비밀마다 데이터키, 데이터키는 마스터키로 암호화). `MasterKeyProvider`: env(base64) 구현 + KMS용 자리 |
| `portfolio` (기존) | 백엔드 A | `account.user_id`, 요청자 → 계좌 해석(비로그인 = 데모), 연결 API(`/api/me`, `/api/connections/toss/**`) |
| `broker-toss` (신규) | 백엔드 B | 토스 클라이언트(토큰 캐시, 그룹별 레이트리밋, 429 `Retry-After`, 나가는 요청 허용 목록), `BrokerPort`·`BrokerVerifier` 구현, `local`용 가짜 토스 서버 |
| `ingest` (신규) | 백엔드 B | 작업 큐(`ingest_job`, `SKIP LOCKED`), 역산 작업, 일일 수집 작업, 현금흐름 추정, 누락일 기록, `IngestScheduler` 구현 |
| `web` | 프론트 | 로그인, 연결 흐름, 연결 설정, 데모 배너, 계좌 없음 상태 |

Flyway 버전 대역: market-data V1–99, portfolio V100–199, user-auth V200–299, credential V300–399, ingest V400–499.

## 모듈 사이 인터페이스 (portfolio 모듈의 `com.uplog.portfolio.connect` 패키지)

백엔드 A가 쓰고, 백엔드 B가 구현한다. 두 에이전트가 시작하기 전에 이 파일들이 먼저 들어가 있다.

```java
/** 키 검증과 계좌 목록. 실패는 BrokerException(사유 코드)로 던진다. */
public interface BrokerVerifier {
    List<BrokerAccount> verify(BrokerCredentials credentials);
}
public record BrokerCredentials(String clientId, String clientSecret) {}   // toString은 secret을 가린다
public record BrokerAccount(String accountSeq, String maskedAccountNo, String type) {}

public final class BrokerException extends RuntimeException {
    public enum Reason { INVALID_CREDENTIALS, IP_NOT_ALLOWED, NO_BROKERAGE_ACCOUNT, RATE_LIMITED, TOKEN_REVOKED, UNAVAILABLE }
}

/** 연결된 계좌의 수집 작업을 큐에 넣는다. */
public interface IngestScheduler {
    void scheduleBackfill(long accountId);
    void scheduleDailySync(long accountId, LocalDate asOf);
    IngestStatus status(long accountId);   // 역산 진행률, 마지막 수집, 최근 실패
}
public record IngestStatus(String backfillState /* PENDING|RUNNING|DONE|FAILED */, double backfillProgress,
                           Instant lastSyncedAt, String lastError, List<LocalDate> recentGaps) {}

/** 수집 작업이 복호화된 키를 받는 유일한 통로. 백엔드 A의 credential 모듈이 구현한다. */
public interface CredentialReader {
    BrokerCredentials read(long accountId);   // 연결 해제 뒤에는 CredentialNotFoundException
}
```

## 데이터 (추가·변경)

- `app_user(id, nickname, created_at)`, `user_identity(user_id, provider KAKAO|DEV, subject, unique(provider, subject))`
- `account`에 `user_id`(데모는 null), `broker_account_seq`, `masked_account_no`, `status`(`ACTIVE`|`DISCONNECTED`) 추가
- `broker_credential(account_id pk, client_id, secret_ciphertext, data_key_ciphertext, master_key_id, created_at)` — 연결 해제 시 행 삭제(즉시 파기)
- `cash_flow`에 `source`(`SEED`|`INFERRED`) 추가. 추정 유입은 `INFERRED`
- ingest: `ingest_job(id, account_id, kind BACKFILL|DAILY, as_of, state, attempts, next_run_at, locked_by, locked_at, last_error, progress)`, `holding_snapshot(account_id, date, symbol, quantity, avg_price, last_price, currency)`, `cash_snapshot(account_id, date, krw, usd, fx)`, `sync_cursor(account_id, last_order_at)`

## API 계약 (프론트·백엔드 A)

공통 오류 형식은 기존과 같다. 새 코드: `UNAUTHENTICATED`(401), `ORIGIN_NOT_ALLOWED`(403), `TOSS_INVALID_CREDENTIALS`(400), `TOSS_IP_NOT_ALLOWED`(400, 본문 최상위에 `egressIps`), `TOSS_NO_BROKERAGE_ACCOUNT`(400), `TOSS_RATE_LIMITED`(429), `TOSS_UNAVAILABLE`(502, 검증 중 token-revoked도 여기에 "다른 곳"이 들어간 메시지로), `ALREADY_CONNECTED`(409), `SYNC_TOO_SOON`(429, `Retry-After`), `INVALID_PARAMETER`(400).

```
GET  /api/me
  → { "user": { "id": 1, "nickname": "승진", "provider": "KAKAO" } | null,
      "connection": { "status": "NONE" | "BACKFILLING" | "ACTIVE" | "ERROR", "maskedAccountNo": "1234-**-**56" | null } }

POST /api/auth/dev-login { "nickname": "개발자" }     // local 프로필에서만. 세션 쿠키 발급
POST /api/auth/logout                                  // 쿠키 삭제
GET  /oauth2/authorization/kakao                       // 카카오 로그인 시작 (env에 키가 있을 때만)
GET  /api/auth/providers → { "kakao": true, "dev": true }

GET  /api/connections/toss/setup → { "egressIps": ["203.0.113.10"], "keyPageUrl": "https://…", "notes": [...] }   // 로그인 필요
POST /api/connections/toss { "clientId": "…", "clientSecret": "…" }
  → 200 { "status": "CHOOSE_ACCOUNT", "accounts": [ { "accountSeq": "1", "maskedAccountNo": "…", "type": "BROKERAGE" } ] }
  → 201 { "status": "BACKFILLING" }                   // 종합매매 계좌가 하나면 바로 연결
POST /api/connections/toss/account { "clientId", "clientSecret", "accountSeq" } → 201 { "status": "BACKFILLING" }
GET  /api/connections/toss
  → { "status": "BACKFILLING" | "ACTIVE" | "ERROR", "maskedAccountNo", "connectedAt" /* YYYY-MM-DD */,
      "backfill": { "state": "RUNNING", "progress": 0.42 }, "lastSyncedAt", "lastError": null | "…",
      "recentGaps": ["2026-10-12"] }
POST /api/connections/toss/sync → 202                  // 수동 다시 수집, 계좌당 1시간에 1번(넘으면 429 SYNC_TOO_SOON)
GET  /api/connections/toss (연결 없음) → 404 ACCOUNT_NOT_CONNECTED
DELETE /api/connections/toss?deleteData=false → 204    // 키는 항상 즉시 파기. deleteData=true면 계좌 기록도 삭제
```

- 키는 요청 본문으로만 받고 로그·오류 메시지·응답에 절대 남기지 않는다. 검증에 실패한 키는 저장하지 않는다.
- `/api/portfolio/**`는 요청자의 계좌를 쓴다(비로그인 = 데모, `account.sample = true`).
- 세션 쿠키: `uplog_session`, HttpOnly, SameSite=Lax, Secure(운영), 14일. 상태를 바꾸는 POST/DELETE는 `Origin` 헤더 검사로 CSRF를 막는다(허용 출처: `uplog.auth.allowed-origins` / `UPLOG_ALLOWED_ORIGINS`).
- `GET /api/me`는 비로그인에도 200(`user: null`, `connection.status: "NONE"`).
- Next rewrites에 `/oauth2/**`, `/login/oauth2/**`를 추가해 같은 출처로 OAuth 왕복을 처리한다.

## 수집 규칙 (백엔드 B)

- **역산**(연결 직후 1회): 계좌 개설 이후 `CLOSED` 체결 전부 → 체결된 종목들의 일봉(첫 체결 60거래일 전부터) → 체결 시점 환율(`dateTime`) → `trade`, `daily_price`, `fx_rate`에 기록. 역산 구간에는 현금·입출금이 없어서 로그는 보유 종목 손익만 보인다.
- **일일**(07:00 KST, 직전 거래일 기준): 보유 종목 + 매수 가능 금액(KRW·USD) + 새 체결(커서 이후) + 환율 → 스냅샷과 `trade` 기록 → 현금흐름 추정(`INFERRED`) → `account_day` 갱신.
- **실패**: 지수 백오프로 3회 재시도, 그래도 실패하면 `collection_gap`에 기록하고 다음 날 작업은 계속한다. 401 `token-revoked`는 한 번만 다시 발급받고, 반복되면 `lastError`에 "다른 곳에서 같은 키를 사용 중"을 남긴다. 403 `ip-not-allowed`는 즉시 실패하고 사용자에게 IP 등록을 안내한다.
- **레이트리밋**: 그룹별 토큰 버킷(문서 수치)을 쓰고, 응답 헤더 `X-RateLimit-Remaining`이 낮으면 속도를 늦춘다. 워커가 여러 대가 되면 Redis로 옮긴다.
- **안전장치**: 토스 클라이언트는 `POST /oauth2/token`과 `GET /api/v1/{accounts,holdings,orders,buying-power,exchange-rate,candles,market-calendar}`만 보낼 수 있다. 그 밖의 요청은 보내기 전에 예외로 막는다(키가 주문 권한을 가지므로).
- **가짜 토스 서버**(`local`): 같은 앱의 `/__fake-toss/**`. 명세와 같은 경로·형식(숫자는 문자열). 결정적 데이터, `client_secret`이 `invalid`면 401, `client_id`가 `ip-blocked`로 시작하면 403 `ip-not-allowed`, 레이트리밋 헤더 포함. 개발용 로그인 → 연결 → 역산 → 로그 표시까지 로컬에서 끝까지 확인할 수 있어야 한다.
