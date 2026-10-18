# UpLog (업로그) — 아키텍처 설계

기획서: [UpLog 서비스 기획서](https://claude.ai/artifact/Xd6xDtN83bG4rf3auWhaev)

실시간이 아니라 **일 단위 스냅샷**을 중심에 둔 모듈형 모놀리스다. 정책상 토스 Open API 키를 서비스 서버에 둘 수 없어서 **서버 없이 맥에서만 도는 앱**이 됐다: 이 코드는 Electron 앱 안의 로컬 엔진(`desktop` 프로필, 127.0.0.1 전용, H2 파일 DB)으로 뜬다. 구조·실행 계약은 [design/desktop-app.md](design/desktop-app.md). 아래 1·3·4절의 서버 그림(PostgreSQL, api/batch 프로필, 고정 IP, KMS)은 서버 시절 설계이고 코드는 `server-archive` 브랜치에 있다.

## 1. 전체 그림

```
[클라이언트: 웹/PWA → 2단계에 앱+위젯]
          │ REST
┌─────────▼──────────────── api 프로필 ───────────────┐
│ auth · simulation(A) · portfolio-view(B) · share     │
└─────────┬────────────────────────────────────────────┘
          │ 읽기 전용(계산 결과 테이블)
┌─────────▼──────── PostgreSQL ────────────────────────┐
│ raw 스냅샷(불변) │ 파생 지표(재계산 가능) │ 시세·환율 │
└─────────▲────────────────────────────▲───────────────┘
          │ 쓰기                         │ 쓰기
┌─────────┴──── batch 프로필 ───────┐  ┌──┴── market-data 수집 ──┐
│ ingest(토스) → calc-engine        │  │ 공공데이터/외부 시세      │
│ 큐 + 레이트리미터, 고정 IP(NAT)    │  │ ETF·지수·환율·배당        │
└──────────┬────────────────────────┘  └─────────────────────────┘
           ▼
     Toss OpenAPI (KMS로 복호화한 키로만 호출)
```

## 2. 모듈

| 모듈 | 책임 | 단계 |
|---|---|---|
| `core` | `Money`, `CurrencyCode`, 거래일 캘린더(KR/US) | MVP |
| `calc-engine` | 일별 손익, TWR, 이동평균 실현손익, 벤치마크 리플레이, 시뮬레이션. 스프링·DB 의존 없는 순수 자바 | MVP |
| `local-user` | 로컬 엔진 보안: 실행 토큰 → `uplog_desktop` 쿠키(`/__launch`), `/api/**` 쿠키 검사(403 `DESKTOP_LOCKED`), `Host` 검사(DNS 리바인딩), 같은 출처 `Origin` 검사, `/__health`. 로그인 없음: 로컬 사용자 한 명(`app_user`, 첫 실행 때 생성), `CurrentUser` | MVP |
| `broker-toss` | `BrokerPort`·`BrokerVerifier`의 토스 구현(`TossBrokerAdapter`): clientId별 토큰 캐시(401 `token-revoked`면 한 번만 재발급), 그룹별 토큰 버킷 레이트리밋(`X-RateLimit-Remaining`이 낮으면 늦춤, 429는 `Retry-After`), 5xx 백오프, 나가는 요청 허용 목록(토큰 발급 + 조회 GET 9종 외에는 보내기 전에 차단). `fake-toss` 프로필의 가짜 토스 서버 `/__fake-toss/**` | MVP |
| `credential` | 증권사 키 봉투 암호화(`broker_credential`: 비밀마다 AES-256-GCM 데이터키, 데이터키는 `MasterKeyProvider`로 감쌈 — 마스터키는 macOS 키체인(`security`, 서비스 `uplog`·계정 `master-key`), 키체인이 없으면 data-dir의 키 파일(600)), `CredentialReader`·`CredentialStore` 구현, 해제 시 즉시 파기 | MVP |
| `market-data` | 공용 시세(instrument·daily_price·fx_rate). 기능 A 실제 종목: 시장별 종목 목록 동기화(하루 1회), 요청 구간 일봉 백필(확인 구간 `price_coverage` 밖만), 매수일 환율 백필. 시세 소스는 `MarketFeed` 포트(요청자 키로 만든 것, 구현은 ingest의 `BrokerMarketFeed`) | MVP |
| `ingest` | 작업 큐(`ingest_job`, `FOR UPDATE SKIP LOCKED`, 지수 백오프 3회 → 실패 시 `collection_gap`), 역산(체결 전부·일봉·환율, 역산 구간 최소 입금 추정), 일일 수집(07:00 KST: 새 체결·보유·현금 스냅샷, 현금흐름 추정 `INFERRED`, 보유 대사), 엔진 시작·30분마다 놓친 날 따라잡기, 한가할 때 폴링 늦춤, `IngestScheduler`·`AccountDataPurger` 구현 | MVP |
| `simulation` | 기능 A API, 파라미터 해시 캐시 | MVP |
| `portfolio` (계획상 `portfolio-view`) | 기능 B API(로그·보유·일자 상세·인사이트), 원시 테이블, `BrokerPort`, 데모 계좌, 요청자 → 계좌 해석(토스 연결 전 = 데모), 연결 API(`/api/me`, `/api/connections/toss/**`), 이 맥의 공인 IP 확인(`/api/desktop/public-ip`)와 모듈 사이 계약(`connect` 패키지). 계산은 `calc-engine`의 `com.uplog.calc.portfolio` | MVP |
| `share` | 공유 카드 이미지(SVG → PNG), 공유 링크 | MVP |
| `insight` | 기능 C(기여도, 드로다운, 환율 분리, 습관 등) | 2 |
| `notify` | 주간 리캡, 의미 있는 순간 알림 | 2 |
| `app` | Spring Boot 진입점(프로필 `desktop` / `fake-toss`), `uplog.desktop.web-dir`의 web 정적 export 서빙 | MVP |

기능 A(시뮬레이션)와 기능 B(내 계좌)는 같은 `calc-engine`을 쓴다. 적립식 시뮬레이션은 "가상의 현금흐름을 벤치마크에 리플레이"한 것이고, 벤치마크 비교는 "내 실제 현금흐름을 리플레이"한 것이다.

모듈은 구현할 때 하나씩 추가한다. 현재 저장소에는 `core`, `calc-engine`, `market-data`, `simulation`, `portfolio`, `local-user`, `credential`, `broker-toss`, `ingest`, `app`(로컬 엔진)과 `web`(Next.js 정적 export 화면)이 있다.
토스 Open API는 공개 명세(1.2.24)로 구현했고 실제 키로는 아직 호출해 보지 않았다(docs/toss-openapi-notes.md). `fake-toss` 프로필은 같은 엔진의 가짜 토스 서버로 연결 → 역산 → 로그까지 돈다.
일별 계좌 값은 체결·입출금·배당을 시세에 리플레이(`PortfolioReplay`)해 만들고, 이 함수가 그대로 가입 전 기간 역산(backfill)이 된다.
Flyway 버전은 모듈별 구간을 쓴다: `market-data` V1–V99, `portfolio` V100–V199, `local-user` V200–V299, `credential` V300–V399, `ingest` V400–V499(`out-of-order` 켬).
계좌 연결 설계는 [design/account-connection.md](design/account-connection.md), 맥 앱 설계는 [design/desktop-app.md](design/desktop-app.md).

## 3. 데이터 모델

원칙: **원시 데이터는 불변으로 쌓고, 파생 지표는 언제든 재계산 가능하게 둔다.**

**원시 (append-only)**
- `broker_connection`: user_id, broker, encrypted_secret, kms_key_id, scopes, status, connected_at
- `trade_execution`: user_id, broker_trade_id(unique), instrument_id, side, qty, price, currency, fee, tax, executed_at
- `cash_flow`: user_id, date, type(입금/출금/배당/환전), amount, currency
- `position_snapshot`: user_id, as_of_date, instrument_id, qty, avg_cost, price, value_local, value_krw — 월 단위 파티셔닝
- `account_snapshot`: user_id, as_of_date, cash_krw, cash_usd, total_value_krw, source(collected / backfilled / missing)

**시세 (공용)**
- `instrument`: 코드, 시장, 통화, 유형(주식/ETF/지수)
- `daily_price`: instrument_id, date, close, adj_close, dividend
- `fx_rate`: date, pair, rate

**파생 (calc_version 포함, 재계산 가능)**
- `daily_metric`: user_id, date, pnl_krw, daily_return, twr_index, deposit_net, realized_pnl, unrealized_pnl — 로그와 그래프는 이 테이블만 읽는다
- `benchmark_replay`: user_id, date, benchmark_id, value_krw
- `sim_result_cache`: params_hash, result_json, expires_at

## 4. 배치 흐름 (기능 B)

1. **스케줄**: KST 07:00 하루 1회(미국장 마감 후). 국내·해외 모두 직전 거래일 종가 기준으로 `as_of_date`를 정한다.
2. **큐**: `ingest_job` 테이블 + `SELECT … FOR UPDATE SKIP LOCKED`. Spring Batch는 잡 이력·재시작 관리용으로만 쓴다.
3. **레이트리밋**: API 그룹별 토큰 버킷(Bucket4j). 워커 1대일 땐 인메모리, 늘릴 때 Redis로 옮긴다.
4. **수집 → 계산**: 유저 한 명의 수집이 끝나면 즉시 그 유저의 `daily_metric`을 갱신한다. 지수 백오프로 재시도하고, 3회 실패하면 `missing`으로 표시해 로그에 빈 칸으로 보여준다.
5. **IP**: 토스는 허용 IP에서 온 요청만 받는다. 맥 앱은 사용자의 맥에서 토스를 부르므로, 사용자가 이 맥의 공인 IP를 WTS에 등록한다(바뀌면 다시 등록).

## 5. 계산 규칙 (`calc-engine`)

- **일별 수익률** (현금흐름은 장 마감 시점에 발생한다고 가정): `r_t = (V_t − CF_t) / V_{t−1} − 1`
- **TWR**: `Π(1 + r_t) − 1`. 누적 지수 `twr_index`를 저장해 두면 임의 구간 수익률은 `index_end / index_start − 1`이다.
- **연환산 수익률**: `twr_index ^ (365 / 경과일) − 1`
- **실현 손익**: 이동평균법(토스 표시와 일치시키기 위함). 수수료·세금은 평균단가에 넣지 않는다. 매도 쪽 비용은 실현손익에서 빼고, 모든 비용은 따로 쌓아 "숨은 비용" 집계에 쓴다.
- **벤치마크 리플레이**: 현금흐름을 그날 종가(휴장일이면 다음 거래일)로 벤치마크 매수·매도에 적용한다.
- **고통 지표**: 최대 손실 폭은 TWR 지수 기준 고점 대비 하락, 원금 회복 기간은 평가금액이 투입 원금 아래에 머문 가장 긴 구간, 가장 아팠던 날은 일별 수익률 최솟값.
- **연속 기록** (`Streaks`): 상승·하락은 일별 수익률이 0보다 큰·작은 거래일이 이어진 기록(0%는 끊김). 적립은 매수가 있었던 주기(거래일·주·월)가 이어진 기록이고, 마지막 주기에 아직 매수가 없으면 직전 주기까지를 현재 연속으로 본다. 기능 B의 실제 매수 기록에도 같은 함수를 쓴다.
- **원화 환산**: `value_krw = value_local × fx(as_of_date)`. 환율 분리(2단계)를 위해 local 값과 환율을 따로 저장한다.

## 6. 기능 A (시뮬레이션)

- `GET /api/simulations?etfs=…&period=1Y&frequency=WEEKLY&dayOfWeek=MONDAY&amountType=PER_PURCHASE&amount=…` (계약: [docs/api/simulation.md](api/simulation.md))
- 실제 종목(국내·미국 ETF·주식)만. 시세는 **요청자 자신이 연결한 토스 키로만** 받고, 미연결은 403 `SIMULATION_REQUIRES_CONNECTION`. 미국 종목은 그날 USD/KRW로 원화 환산.
- 3년 × 약 250거래일 × 최대 3개 종목이라서 요청 시 계산한다. 정규화한 파라미터 해시로 캐시한다.
- 공유 링크에는 결과가 아니라 파라미터를 담고, 열 때 다시 계산한다.
- 랭킹·추천 엔드포인트는 두지 않는다(규제 리스크).

## 7. API

```
GET    /api/me                           로컬 사용자와 연결 상태
GET    /api/desktop/public-ip            이 맥의 공인 IP(누를 때만 ipify 호출)
POST   /api/connections/toss             키 등록(검증 호출 1회 → 암호화 저장)
DELETE /api/connections/toss             해제 + 키 즉시 파기
GET    /api/portfolio/log?from&to        로그 (daily_metric)
GET    /api/portfolio/days/{date}        그날 종목별 기여 + 체결
GET    /api/portfolio/series?metric=value|twr&benchmark=SPX
GET    /api/portfolio/summary            TWR, 실현/미실현, 요일 패턴
GET    /api/simulations                  기능 A (토스 연결 필요)
POST   /api/shares  /  GET /s/{id}       공유 카드
```

## 8. 미정 사항

1. **프론트엔드**: MVP는 Next.js 웹/PWA, 홈 위젯이 들어가는 2단계에 React Native 또는 Flutter.
2. **기능 A 시세 소스**: 지금은 요청자 자신의 토스 키(연결 필수). 비로그인 체험이 필요하면 공공데이터포털(국내 ETF) 등 재배포 가능한 소스를 따로 붙인다.
3. 기획서 "확인 필요" 항목 중 **조회 전용 권한**과 **체결 내역 조회 가능 기간**은 `credential`과 backfill 설계를 직접 바꾸므로 먼저 확인한다.
