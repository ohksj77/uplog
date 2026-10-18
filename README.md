# 업로그 (uplog)

일 단위 스냅샷으로 투자 기록을 로그처럼 보여 주는 서비스. 설계는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
API 계약은 기능 A(ETF 시뮬레이션) [docs/api/simulation.md](docs/api/simulation.md), 기능 B(내 계좌 뷰) [docs/api/portfolio.md](docs/api/portfolio.md).

## 로컬 실행

### 백엔드

필요한 것: JDK 25. Docker는 없어도 된다.

```bash
# H2 메모리 DB(PostgreSQL 모드) + 합성 샘플 ETF 4종(SAMPLE01–04)으로 http://localhost:8080 에 뜬다
./gradlew :app:bootRun --args='--spring.profiles.active=local,api'

curl 'localhost:8080/api/etfs'
curl 'localhost:8080/api/simulations?etfs=SAMPLE01,SAMPLE02&period=1Y&frequency=MONTHLY&dayOfMonth=15&amountType=PER_PURCHASE&amount=300000'
curl 'localhost:8080/api/portfolio/summary'
curl 'localhost:8080/api/portfolio/insights/waterfall?period=YTD'
```

- `local` 프로필의 시세는 **실제 ETF가 아닌 합성 데이터**다(`sample: true`, 이름이 "샘플 "으로 시작). 가장 최근 평일에서 끝나는 약 4년치 평일 종가를 고정 시드 랜덤워크로 만든다. 재시작하면 DB는 비워지고 다시 시드된다.
- 기능 B(`/api/portfolio/**`)는 `local` 프로필에서만 합성 **데모 계좌**(`sample: true`)로 뜬다: 샘플 종목 8개(S00001–S00004, SMPT·SMPC·SMPS·SMPE), 합성 지수 SPX·KOSPI200, USD/KRW 환율 약 3년치와 약 2년치 입출금·체결·배당. 로그인한 사용자는 자기 연결 계좌를 본다.
- `local`에서는 같은 앱에 **가짜 토스 서버**(`/__fake-toss/**`, 명세와 같은 경로·형식, 결정적 합성 데이터)가 뜨고 토스 클라이언트가 그쪽을 부른다. 개발용 로그인 뒤 아무 키(예: `clientId=dev`, `clientSecret=dev`)로 연결하면 역산·첫 일일 수집이 몇 초 안에 끝난다. 키 규칙: `clientSecret=invalid` → 잘못된 키, `clientId`가 `ip-blocked`로 시작 → IP 미등록, `multi`로 시작 → 계좌 2개, `no-account`로 시작 → 종합매매 계좌 없음.
- 테스트·빌드: `./gradlew build` (경고도 오류로 본다: `-Xlint:all -Werror`).

PostgreSQL로 띄우기(운영과 같은 DB):

```bash
docker compose up -d                 # postgres:17, uplog/uplog@localhost:5432/uplog
./gradlew :app:bootRun               # 기본 프로필 api
```

접속 정보는 환경변수 `UPLOG_DB_URL`, `UPLOG_DB_USERNAME`, `UPLOG_DB_PASSWORD`로 바꾼다. 스키마는 시작할 때 Flyway가 만든다(각 모듈의 `src/main/resources/db/migration`: `market-data` V1–V99, `portfolio` V100–, `user-auth` V200–, `credential` V300–, `ingest` V400–).

### 로그인·계좌 연결 설정(환경변수)

| 변수 | 내용 |
|---|---|
| `UPLOG_SESSION_SECRET` | 세션 JWT(HS256) 서명 키, 32바이트 이상. 없으면 `local`은 고정 개발 키, 그 밖에는 실행마다 무작위 키(재시작하면 로그아웃) |
| `UPLOG_SECURE_COOKIE` | `true`면 세션 쿠키에 Secure(운영 HTTPS) |
| `UPLOG_ALLOWED_ORIGINS` | POST·DELETE를 받아 줄 웹 출처(쉼표로 구분, 기본 `http://localhost:3000`). Origin 검사로 CSRF를 막는다 |
| `UPLOG_KAKAO_CLIENT_ID`, `UPLOG_KAKAO_CLIENT_SECRET` | 둘 다 있을 때만 카카오 로그인(`/oauth2/authorization/kakao`)이 켜진다 |
| `UPLOG_KAKAO_REDIRECT_URI` | 카카오 콘솔에 등록한 Redirect URI(기본 `{baseUrl}/login/oauth2/code/kakao`, `X-Forwarded-*` 반영) |
| `UPLOG_MASTER_KEY` | 증권사 키 암호화 마스터키(base64, 32바이트, 예: `openssl rand -base64 32`). 없으면 `local`은 고정 개발 키(경고), 그 밖에는 계좌 연결이 실패한다 |
| `UPLOG_TOSS_BASE_URL` | 토스 Open API 주소(기본 `https://openapi.tossinvest.com`, `local`은 가짜 서버) |
| `UPLOG_INGEST_WORKER_ENABLED` | 이 인스턴스가 수집 큐를 돌릴지(기본 `true`). 워커는 토스 허용 IP로 등록한 고정 IP 뒤에 둔다 |
| `UPLOG_EGRESS_IPS` | 토스를 호출하는 서버의 고정 IP(쉼표로 구분). 연결 안내에 보여 준다(`local` 기본 `127.0.0.1`) |

`local` 프로필에서는 `POST /api/auth/dev-login {"nickname":"개발자"}`로 로그인한다(다른 프로필에는 없다). 상태를 바꾸는 요청에 세션 쿠키를 실어 보낼 때는 `Origin` 헤더가 필요하다:

```bash
curl -c jar -H 'Origin: http://localhost:3000' -H 'Content-Type: application/json' -d '{"nickname":"개발자"}' localhost:8080/api/auth/dev-login
curl -b jar localhost:8080/api/me
```

국내 ETF 실제 시세 수집(공공데이터포털 금융위원회_증권상품시세정보)은 `UPLOG_PUBLIC_DATA_SERVICE_KEY`(인증키, Decoding 값)를 넣었을 때만 켜진다. 평일 14:30(KST)에 최근 7일치를 다시 받아 덮어쓴다. 응답 형식은 아직 실제 API로 검증하지 않았다.

| 모듈 | 내용 |
|---|---|
| `core` | `Money`, 통화, 거래일 캘린더 |
| `calc-engine` | TWR, 벤치마크 리플레이, 시뮬레이션, 계좌 리플레이·포지션·매도 채점·손익 폭포수·환율 분해·수중 구간 (순수 자바) |
| `market-data` | `instrument`·`daily_price`·`fx_rate` 테이블, 시세 조회, 공공데이터 수집 잡, 로컬 샘플 시드 |
| `simulation` | `GET /api/simulations`, `GET /api/etfs`, 결과 캐시(Caffeine) |
| `portfolio` | `GET /api/portfolio/**`(기능 B, 비로그인 = 데모 계좌), 계좌 원시 테이블·`account_day`, `BrokerPort`, 로컬 데모 계좌, 계산 캐시(Caffeine), `/api/me`·`/api/connections/toss/**` |
| `user-auth` | 사용자·세션(JWT 쿠키), 카카오 로그인, `local` 개발용 로그인, Spring Security |
| `credential` | 증권사 키 봉투 암호화 보관(AES-256-GCM, 마스터키) |
| `broker-toss` | 토스 Open API 클라이언트(토큰 캐시, 그룹별 레이트리밋, 허용 목록), `BrokerPort`·`BrokerVerifier` 구현, `local` 가짜 토스 서버 |
| `ingest` | 수집 큐·워커, 역산, 매일 07:00(KST) 일일 수집, 현금흐름 추정, 누락일 기록 |
| `app` | Spring Boot 진입점, 프로필 `api`(기본) / `batch` / `local` |

## 프론트엔드

`web/` (Next.js 16, Tailwind v4). `/api/*` 요청은 `API_ORIGIN`(기본 `http://localhost:8080`)으로 전달된다.

```
cd web
npm install
npm run dev          # http://localhost:3000
```

- 백엔드 없이 화면만 보려면 `web/.env.development.local`에 `NEXT_PUBLIC_USE_MOCK=1` (합성 샘플 데이터, env 파일은 커밋하지 않음)
- 화면: `/` 입력 폼, `/simulate?…` 결과(거치식·적립식 로그, 연속 기록, 평가금액 차트, 고통 지표)
