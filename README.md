# 업로그 (uplog)

일 단위 스냅샷으로 투자 기록을 로그처럼 보여 주는 맥 앱. 설계는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
API 계약은 기능 A(종목 시뮬레이션) [docs/api/simulation.md](docs/api/simulation.md), 기능 B(내 계좌 뷰) [docs/api/portfolio.md](docs/api/portfolio.md).

## 로컬 실행

업로그는 **서버 없이 맥에서만 도는 앱**이다(토스 Open API 키를 서비스 서버에 두지 않는다). 설계와 Electron·엔진·화면 사이의
실행 계약은 [docs/design/desktop-app.md](docs/design/desktop-app.md). 서버 시절 코드(카카오 로그인, 세션, 이어 하기, 서버 고정 IP)는
`server-archive` 브랜치에 있다.

### 엔진(백엔드) 개발 실행

필요한 것: JDK 25. Docker·환경변수는 필요 없다.

```bash
# 가짜 토스 서버 + dev-open(실행 토큰·Host 검사 끔) + 임시 data-dir. http://127.0.0.1:8080
./gradlew :app:bootRun --args='--spring.profiles.active=desktop,fake-toss --uplog.desktop.dev-open=true --uplog.desktop.data-dir=/tmp/uplog-dev'

curl 127.0.0.1:8080/__health
curl 127.0.0.1:8080/api/me
curl 127.0.0.1:8080/api/portfolio/summary          # 토스를 연결하기 전에는 데모(샘플) 계좌
curl -H 'Origin: http://localhost:3000' -H 'Content-Type: application/json' \
     -d '{"clientId":"dev","clientSecret":"dev"}' 127.0.0.1:8080/api/connections/toss   # 가짜 키로 연결 → 역산
```

- `desktop` 프로필(기본): 127.0.0.1에만 바인딩(다른 주소면 시작하지 않음), H2 파일 DB `{data-dir}/uplog.mv.db`(PostgreSQL 모드),
  로그 `{data-dir}/logs/engine.log`, `web-dir`의 정적 화면 서빙. `data-dir` 기본값은 `~/Library/Application Support/uplog`.
- 로그인 없음: 첫 실행 때 로컬 사용자 한 명을 만들고, 실행 쿠키가 맞는 요청은 모두 그 사용자다. 토스를 연결하기 전에는
  `/api/portfolio/**`가 합성 **데모 계좌**(`sample: true`)를 보여 준다. 연결하면 내 계좌.
- 보안(dev-open이 아닐 때): `GET /__launch?token=`이 실행 토큰을 `uplog_desktop` 쿠키(HttpOnly, SameSite=Strict)로 바꿔 주고,
  `/api/**`는 그 쿠키가 있어야 한다(없으면 403 `DESKTOP_LOCKED`). 모든 요청은 `Host`가 `127.0.0.1:{port}`·`localhost:{port}`여야 하고,
  상태를 바꾸는 요청은 같은 출처여야 한다. dev-open은 토큰·Host 검사를 끄고 `next dev`(localhost:3000) 출처를 허용한다.
- 증권사 키는 봉투 암호화(AES-256-GCM)로 DB에 두고, 마스터키는 **macOS 키체인**(서비스 `uplog`, 계정 `master-key`, 없으면 처음 쓸 때
  만든다)에 둔다. 키체인이 없는 환경은 `{data-dir}/master.key`(권한 600). 개발 중 키체인을 건드리지 않으려면
  `--uplog.credential.key-store=file`.
- 진짜 토스에 붙이려면 `fake-toss`를 빼고 띄운 뒤 연결 화면의 "내 공인 IP 확인"(`GET /api/desktop/public-ip`, api.ipify.org)으로
  이 맥의 공인 IP를 확인해 토스 WTS 허용 IP에 등록한다. 공유기·통신사가 IP를 바꾸면 다시 등록해야 한다.
- `fake-toss`는 **명시적으로** 켤 때만 쓴다. 같은 엔진에 **가짜 토스 서버**(`/__fake-toss/**`, 명세와 같은 경로·형식, 결정적 합성
  데이터)가 뜨고, 연결한 계좌는 "가짜 토스 계좌(개발용)" 샘플 계좌가 된다. 아무 키(예: `clientId=dev`, `clientSecret=dev`)로 연결하면
  역산·첫 일일 수집이 몇 초 안에 끝난다. 키 규칙: `clientSecret=invalid` → 잘못된 키, `clientId`가 `ip-blocked`로 시작 → IP 미등록,
  `multi`로 시작 → 계좌 2개, `no-account`로 시작 → 종합매매 계좌 없음, `market-blocked`로 시작 → 연결은 되고 시세 조회만 403.
- 기능 A(시뮬레이션)는 **실제 종목만** 다루고, 시세는 연결한 토스 키로 받는다. 연결 전에는 403 `SIMULATION_REQUIRES_CONNECTION`.
- 수집: 엔진이 켜져 있으면 매일 07:00(KST) 직전 거래일을 수집하고, 엔진이 뜰 때·30분마다 놓친 날을 따라잡는다.
- 테스트·빌드: `./gradlew build` (경고도 오류로 본다: `-Xlint:all -Werror`). 테스트는 `desktop`(+`fake-toss`) 프로필에 메모리 DB를 쓴다.
- 앱용 엔진: `./gradlew :app:bootJar` → `app/build/libs/app-0.1.0-SNAPSHOT.jar`.
- 맥 위젯 "UpLog 로그"(`widget/`, SwiftUI·WidgetKit): `widget/build.sh` → `widget/build/UplogWidget.appex`, 시험은 `widget/test-host.sh`.
  엔진이 `{data-dir}/widget/widget.json`을 쓰고 위젯이 읽는다([desktop-app.md "위젯"](docs/design/desktop-app.md)).

| 모듈 | 내용 |
|---|---|
| `core` | `Money`, 통화, 거래일 캘린더 |
| `calc-engine` | TWR, 벤치마크 리플레이, 시뮬레이션, 계좌 리플레이·포지션·매도 채점·손익 폭포수·환율 분해·수중 구간 (순수 자바) |
| `market-data` | `instrument`·`daily_price`·`fx_rate` 테이블, 시세 조회, 데모용 샘플 시드 |
| `simulation` | `GET /api/simulations`, `GET /api/etfs`, 결과 캐시(Caffeine) |
| `portfolio` | `GET /api/portfolio/**`(기능 B, 연결 전 = 데모 계좌), 계좌 원시 테이블·`account_day`, `BrokerPort`, 데모 계좌, `/api/me`·`/api/connections/toss/**`·`/api/desktop/public-ip` |
| `local-user` | 로컬 엔진 보안(실행 토큰·쿠키, Host·Origin 검사), `/__health`·`/__launch`, 로컬 사용자 한 명, `CurrentUser` |
| `credential` | 증권사 키 봉투 암호화 보관(AES-256-GCM, 마스터키는 macOS 키체인) |
| `broker-toss` | 토스 Open API 클라이언트(토큰 캐시, 그룹별 레이트리밋, 허용 목록), `BrokerPort`·`BrokerVerifier` 구현, `fake-toss` 프로필의 가짜 토스 서버 |
| `ingest` | 수집 큐·워커, 역산, 매일 07:00(KST) 일일 수집과 놓친 날 따라잡기, 현금흐름 추정, 누락일 기록 |
| `app` | Spring Boot 진입점(프로필 `desktop` / `fake-toss`), `web-dir` 정적 화면 서빙 |

## 프론트엔드

`web/` (Next.js 16, Tailwind v4). `/api/*` 요청은 `API_ORIGIN`(기본 `http://localhost:8080`)으로 전달된다.

```
cd web
npm install
npm run dev          # http://localhost:3000
```

- 백엔드 없이 화면만 보려면 `web/.env.development.local`에 `NEXT_PUBLIC_USE_MOCK=1` (합성 샘플 데이터, env 파일은 커밋하지 않음)
- 화면: `/` 입력 폼, `/simulate?…` 결과(거치식·적립식 로그, 연속 기록, 평가금액 차트, 고통 지표)
