# 기능 A — 시뮬레이션 API 계약 (v2: 실제 종목)

프론트(`web/`)와 백엔드(`simulation`, `market-data`)가 함께 따르는 계약이다. 바꾸려면 이 문서와 `web/src/lib/api/types.ts`를 함께 고친다.

## 공통

- **실제 종목만** 다룬다: 국내(KOSPI·KOSDAQ)와 미국(NYSE·NASDAQ·AMEX)의 ETF와 주식. 합성 샘플 종목(데모 계좌용, `sample=true`)은 검색에도 나오지 않고 요청해도 없는 종목(404)이다.
- **시세는 요청한 사용자 자신이 연결한 토스 키로만 받는다.** 한 사용자의 키로 받은 시세를 키가 없는 사람에게 보여 주지 않기 위해, 비로그인이거나 ACTIVE 연결 계좌가 없으면 검색·시뮬레이션 모두 403 `SIMULATION_REQUIRES_CONNECTION`이다(다른 데이터로 대신하지 않음).
  - 받은 일봉·환율·종목 목록은 공용 시세 테이블에 저장해 두고, 다음 요청은 모자란 구간만 받는다. 대신 요청자의 키가 지금 시세를 받을 수 있는지 10분마다 가벼운 호출(종목 상세 1건)로 확인한다. 키가 막혀 있으면 이미 저장된 시세여도 보여 주지 않고 502를 낸다.
- 금액: 응답의 `currency` 기준 JSON number.
  - `KRW`(기본): 원화 정수(원 단위 반올림). 미국 종목도 매수 금액·평가금액·종가를 **원화로 환산**해 돌려준다.
  - `USD`: 달러, 소수 둘째 자리(센트 단위 반올림, 예: `1234.56`). 아래 "달러 기준" 참고.
- 비율: 소수(0.1234 = 12.34%).
- 날짜: `YYYY-MM-DD`.
- 오류: HTTP 4xx/5xx + `{"code": "INVALID_PARAMETER", "message": "사람이 읽을 문장"}`
  - `INVALID_PARAMETER`(400)
  - `SIMULATION_REQUIRES_CONNECTION`(403): 비로그인·미연결. 메시지 "실제 종목 시세는 연결한 토스증권 키로 받아요. 계좌를 연결하면 시뮬레이션할 수 있어요."
  - `ETF_NOT_FOUND`(404): 없는 종목, 샘플 종목, ETF·주식이 아닌 종목(ETN·지수 등). 이름은 v1 그대로다.
  - `NO_PRICE_DATA`(422): 기간 안에 시세가 없음, 증권사가 일봉을 주지 않음(상장폐지 등), 달러 종목인데 환율이 없음, 달러 기준인데 기간의 환율이 없음
  - `MARKET_DATA_UNAVAILABLE`(502): 요청자 키로 시세를 받지 못함. 메시지가 사유를 말한다(허용 IP 미등록, 키 무효, 다른 곳에서 같은 키 사용, 요청 한도, 토스 장애)

## GET /api/instruments?query={검색어}&limit={기본 20, 최대 100}

`GET /api/etfs`는 같은 API의 v1 이름이다(호환용 별칭).

이름 또는 종목코드 부분 일치(대소문자 무시). 순서: 코드가 검색어와 정확히 같은 종목 → ETF → 주식, 같은 무리 안에서는 코드·이름이 검색어로 시작하는 것 → 이름순. `query`가 비면 ETF부터 이름순.

```json
[
  { "code": "069500", "name": "KODEX 200", "market": "KR", "type": "ETF", "currency": "KRW",
    "firstDate": "2025-10-06", "lastDate": "2026-10-16" },
  { "code": "SPY", "name": "SPDR S&P 500 ETF", "market": "US", "type": "ETF", "currency": "USD",
    "firstDate": null, "lastDate": null }
]
```

- `market`: `KR` | `US`, `type`: `ETF` | `STOCK`(토스 `ETF`·`FOREIGN_ETF` → ETF, `STOCK`·`FOREIGN_STOCK`·`DEPOSITARY_RECEIPT`·`REIT` → STOCK, ETN·신주인수권·인프라펀드는 뺀다).
- `firstDate`/`lastDate`: 지금 저장된 시세 구간. 아직 시세를 받지 않은 종목은 `null`이다(시뮬레이션할 때 받는다).
- 종목 목록은 시장(KOSPI, KOSDAQ, NYSE, NASDAQ, AMEX)마다 하루(KST) 한 번 토스 `GET /api/v1/stocks/all`(초당 1회)로 받는다. 그날 첫 검색은 몇 초 걸릴 수 있다.

## GET /api/simulations

| 파라미터 | 필수 | 값 |
|---|---|---|
| `etfs` | O | 종목코드 쉼표 구분, 1–3개. 토스 심볼 문자(영문·숫자·`.`·`-`) 1–12자, 대문자로 맞춘다(국내 6자리 코드, 미국 티커 예: `SPY`, `BRK.B`) |
| `period` | O | `6M` \| `1Y` \| `3Y` — 종료일은 시세 마지막 날(그 시장의 어제까지, 장중인 오늘 봉은 쓰지 않음), 시작일은 그로부터 기간만큼 전 |
| `frequency` | O | 적립 주기 `DAILY` \| `WEEKLY` \| `MONTHLY` |
| `dayOfWeek` | WEEKLY일 때 | `MONDAY` … `FRIDAY` |
| `dayOfMonth` | MONTHLY일 때 | 1–31 (없는 날짜면 말일) |
| `amountType` | O | `TOTAL`(총 금액을 회차로 나눔) \| `PER_PURCHASE`(회차당 금액) |
| `amount` | O | 원, 1,000 이상 10억 이하 정수 |
| `currency` | | 결과 금액의 기준 통화 `KRW`(기본) \| `USD`. 대소문자 무시, 그 밖의 값은 400 |

한 번의 요청으로 종목마다 **거치식과 적립식을 같은 총 투입금으로** 계산해 돌려준다(기획서: 두 로그를 위아래로 비교).

- `TOTAL`: 적립식 회차당 금액 = amount / 회차 수(원 단위 내림), 거치식 = 적립식 실제 총 투입금
- `PER_PURCHASE`: 적립식 회차당 금액 = amount, 거치식 = amount × 회차 수

```json
{
  "from": "2025-10-16",
  "to": "2026-10-16",
  "currency": "KRW",
  "results": [
    {
      "etf": { "code": "069500", "name": "KODEX 200", "market": "KR", "type": "ETF", "currency": "KRW" },
      "lumpSum":   { "...": "SimulationResult" },
      "recurring": { "...": "SimulationResult" }
    }
  ],
  "disclaimer": "과거 수익률은 미래 수익을 보장하지 않습니다. 이 계산은 투자 권유가 아닙니다."
}
```

- `currency`(최상위): 응답 금액의 기준 통화. 요청의 `currency`(없으면 `KRW`).
- `etf`: 결과 종목(이름은 v1 그대로, 주식도 여기에 온다). `etf.currency`는 종목의 거래 통화이고, 응답 금액은 종목 통화와 상관없이 최상위 `currency` 기준이다.
- 시세 준비: 종목마다 기간(+10일) 일봉을 토스 `GET /api/v1/candles?interval=1d&adjusted=true`(수정주가, 200개씩)로 받아 둔다. 이미 확인한 구간 밖만 받는다: 앞쪽이 모자라면 확인 구간 앞부터, 뒤쪽은 하루 한 번 최신부터(마지막 7일은 정정 반영으로 다시). 저장된 종목에 없는 코드는 `GET /api/v1/stocks?symbols=`로 확인해 넣는다.
- **원화 환산**(미국 종목): `close_KRW(d) = close_USD(d) × USDKRW(d)`로 바꾼 종가에 원화 금액을 리플레이한다. 매수·평가 모두 그날 환율을 쓰므로 수익률에 환율 변동이 들어간다.
  - 환율은 `fx_rate`의 `USDKRW`(서울 외환시장 15:30 기준 매매기준율). 그날 환율이 없으면 직전 환율, 기간 첫날 앞에 환율이 없으면 첫 환율을 쓴다.
  - 매수일·종료일 중 7일 안에 환율이 없는 날만 요청자의 키로 `GET /api/v1/exchange-rate?dateTime`을 불러 채운다. 최대 40일(넘으면 달마다 첫 날과 마지막 날만). 받아도 하나도 없으면 422 `NO_PRICE_DATA`.
  - 보통은 계좌 수집 작업이 일별 USDKRW를 미리 채워 둔다.
- **달러 기준**(`currency=USD`): **같은 매수**(같은 날 같은 원화 금액, 그래서 같은 수량)를 달러로 나타낸다. 무엇을 샀는지는 바뀌지 않고, 환율 변동 효과만 빠진다.
  - 현금흐름: `cf_USD = cf_KRW / USDKRW(매수일)`, `invested` = 그 합.
  - 평가금액: `value_USD(d) = value_KRW(d) / USDKRW(d)`(미국 종목은 수량 × 달러 종가와 같다).
  - `close`: 미국 종목은 원래 달러 종가, 국내 종목은 `close_KRW / USDKRW(d)`.
  - 일별 수익률·TWR·요약·고통 지표·연속 기록은 이 달러 시계열로 다시 계산한다. 예: 달러 가격이 그대로이고 원화가 강해지면(환율 하락) 원화 기준은 손실, 달러 기준은 0%.
  - 환율은 원화 환산과 같은 `USDKRW`·같은 규칙(그날 없으면 직전, 첫날 앞이면 첫 환율)이고, 국내 종목도 매수일·종료일 환율을 같은 방식으로 채운다. 기간에 환율이 하나도 없으면 422 `NO_PRICE_DATA`.

### SimulationResult

```json
{
  "summary": {
    "finalValue": 13400000, "invested": 12000000, "profit": 1400000,
    "totalReturn": 0.1167, "twr": 0.0812, "annualizedTwr": 0.0812
  },
  "pain": {
    "maxDrawdown": { "rate": -0.183, "peak": "2026-03-04", "trough": "2026-04-09" },
    "maxDrawdownAmount": { "amount": -1830000, "peak": "2026-03-04", "trough": "2026-04-09" },
    "underwater":  { "start": "2026-03-20", "end": "2026-06-11", "recovered": true, "days": 83 },
    "worstDay":    { "date": "2026-04-07", "dailyReturn": -0.052 },
    "worstDayAmount": { "date": "2026-04-07", "pnl": -612000 }
  },
  "streaks": {
    "gain": { "longest": { "length": 7, "start": "2026-01-05", "end": "2026-01-13" }, "current": null },
    "loss": { "longest": { "length": 5, "start": "2026-04-01", "end": "2026-04-07" },
              "current": { "length": 2, "start": "2026-10-15", "end": "2026-10-16" } },
    "purchase": { "unit": "MONTH",
                  "longest": { "length": 12, "start": "2025-10-16", "end": "2026-09-15" },
                  "current": { "length": 12, "start": "2025-10-16", "end": "2026-09-15" } }
  },
  "days": [
    { "date": "2025-10-16", "close": 41250, "value": 1000000, "invested": 1000000,
      "dailyReturn": 0.0, "purchased": true }
  ]
}
```

- `pain`의 하락 폭과 가장 아팠던 날은 두 기준. 화면에서 사용자가 고른다.
  - 수익률 기준 `maxDrawdown`, `worstDay`: TWR(입출금 제외). 같은 종목이면 거치식·적립식이 같다.
  - 금액 기준 `maxDrawdownAmount`(평가손익 고점 대비 감소액, 기준 통화, 0 이하), `worstDayAmount`(하루 손익 = 평가금액 − 그날 현금흐름 − 전날 평가금액, 기준 통화). 방식마다 실제로 잃은 돈이 다르다.
- `pain.underwater`: 평가금액이 원금 아래로 내려간 적이 없으면 `null`. `recovered: false`면 `end` = 마지막 날.
- `streaks`: 연속 기록. 기록이 없으면 `longest`가 `null`, 마지막 날까지 이어지는 연속이 없으면 `current`가 `null`.
  - `gain` / `loss`: 그날 수익률(`dailyReturn`)이 0보다 큰 / 작은 거래일이 이어진 기록(로그 "일별 손익" 뷰의 빨간 / 파란 칸). 0%인 날은 연속을 끊는다.
  - `purchase`: 매수가 있었던 주기가 이어진 기록. `unit` = `DAY`(거래일) \| `WEEK` \| `MONTH`(적립 주기). `start`/`end`는 첫·마지막 매수일. 마지막 주기에 아직 매수가 없으면(예: 이번 달 적립일 전) 직전 주기까지를 `current`로 본다. 거치식은 `null`.
- `days`: 첫 매수일부터 종료일까지 **거래일마다 한 칸**, 날짜 오름차순. `close`는 기준 통화(원화 기준이면 미국 종목은 그날 환율로 환산한 값, 달러 기준이면 위 "달러 기준").

달러 기준 응답 예(일부):

```json
{
  "from": "2026-10-12", "to": "2026-10-30", "currency": "USD",
  "results": [{
    "etf": { "code": "SPY", "name": "SPDR S&P 500 ETF", "market": "US", "type": "ETF", "currency": "USD" },
    "lumpSum": {
      "summary": { "finalValue": 659.69, "invested": 589.01, "profit": 70.68,
                   "totalReturn": 0.12, "twr": 0.12, "annualizedTwr": 8.95 },
      "pain": { "maxDrawdownAmount": { "amount": -14.14, "peak": "2026-10-19", "trough": "2026-10-21" },
                "worstDayAmount": { "date": "2026-10-20", "pnl": -9.42 }, "...": "..." },
      "days": [{ "date": "2026-10-12", "close": 500.00, "value": 589.01, "invested": 589.01,
                 "dailyReturn": 0.0, "purchased": true }]
    },
    "recurring": { "...": "SimulationResult" }
  }],
  "disclaimer": "..."
}
```
  - `dailyReturn`: 입출금을 제거한 그날 수익률(로그 "일별 손익" 뷰)
  - 누적 수익률 뷰 = `value / invested − 1` (프론트 계산)
  - 매수 단가 대비 뷰 = `purchased`인 날만 `마지막 close / 그날 close − 1` (프론트 계산)

## 후속(v2 후보, 지금은 없음)

배당 재투자, 수수료·세금 반영, 환전 수수료, 적립일 바꿨을 때의 결과 비교, 공유 카드.
