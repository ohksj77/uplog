# 업로그 (uplog)

일 단위 스냅샷으로 투자 기록을 로그처럼 보여 주는 서비스. 설계는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
기능 A(ETF 시뮬레이션) API 계약은 [docs/api/simulation.md](docs/api/simulation.md).

## 로컬 실행

### 백엔드

필요한 것: JDK 25. Docker는 없어도 된다.

```bash
# H2 메모리 DB(PostgreSQL 모드) + 합성 샘플 ETF 4종(SAMPLE01–04)으로 http://localhost:8080 에 뜬다
./gradlew :app:bootRun --args='--spring.profiles.active=local,api'

curl 'localhost:8080/api/etfs'
curl 'localhost:8080/api/simulations?etfs=SAMPLE01,SAMPLE02&period=1Y&frequency=MONTHLY&dayOfMonth=15&amountType=PER_PURCHASE&amount=300000'
```

- `local` 프로필의 시세는 **실제 ETF가 아닌 합성 데이터**다(`sample: true`, 이름이 "샘플 "으로 시작). 가장 최근 평일에서 끝나는 약 4년치 평일 종가를 고정 시드 랜덤워크로 만든다. 재시작하면 DB는 비워지고 다시 시드된다.
- 테스트·빌드: `./gradlew build` (경고도 오류로 본다: `-Xlint:all -Werror`).

PostgreSQL로 띄우기(운영과 같은 DB):

```bash
docker compose up -d                 # postgres:17, uplog/uplog@localhost:5432/uplog
./gradlew :app:bootRun               # 기본 프로필 api
```

접속 정보는 환경변수 `UPLOG_DB_URL`, `UPLOG_DB_USERNAME`, `UPLOG_DB_PASSWORD`로 바꾼다. 스키마는 시작할 때 Flyway가 만든다(`market-data/src/main/resources/db/migration`).

국내 ETF 실제 시세 수집(공공데이터포털 금융위원회_증권상품시세정보)은 `UPLOG_PUBLIC_DATA_SERVICE_KEY`(인증키, Decoding 값)를 넣었을 때만 켜진다. 평일 14:30(KST)에 최근 7일치를 다시 받아 덮어쓴다. 응답 형식은 아직 실제 API로 검증하지 않았다.

| 모듈 | 내용 |
|---|---|
| `core` | `Money`, 통화, 거래일 캘린더 |
| `calc-engine` | TWR, 벤치마크 리플레이, 시뮬레이션 (순수 자바) |
| `market-data` | `instrument`·`daily_price` 테이블, 시세 조회, 공공데이터 수집 잡, 로컬 샘플 시드 |
| `simulation` | `GET /api/simulations`, `GET /api/etfs`, 결과 캐시(Caffeine) |
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
