# 기능 B — 내 계좌 뷰 API 계약 (v1)

프론트(`web/`)와 백엔드(`portfolio`)가 함께 따르는 계약이다. 바꾸려면 이 문서와 `web/src/lib/api/portfolio-types.ts`를 함께 고친다.
공통 규칙(금액·비율·날짜·오류 형식)은 [simulation.md](simulation.md)와 같다.

- 금액은 **원화 정수**. 해외 종목의 현지 통화 값은 `…Local` 접미사(소수 허용)를 붙인다.
- 계좌는 아직 로그인이 없으므로 "현재 계좌" 하나다. `local` 프로필에서는 합성 데모 계좌(`account.sample = true`)를 쓴다.
- 오류 코드 추가: `ACCOUNT_NOT_CONNECTED`(404), `INSTRUMENT_NOT_FOUND`(404), `DATE_NOT_FOUND`(404: 거래일이 아니거나 기록 없음)

## 공통 타입

```ts
Instrument = { code: "005930", name: "삼성전자", market: "KR" | "US", currency: "KRW" | "USD" }
Streak     = { length, start, end }                          // simulation.md와 같음
StreakStats = { longest: Streak | null, current: Streak | null }
Trade = { date, instrument, side: "BUY" | "SELL", quantity, priceLocal, fxRate /* 1 for KRW */,
          amount /* 원, 수량×가격×환율 */, fee, tax }
```

## 화면 ↔ 엔드포인트

| 화면 | 엔드포인트 |
|---|---|
| 메인: 수익 로그 | `GET /api/portfolio/summary`, `GET /api/portfolio/log`, `GET /api/portfolio/holdings` |
| 하루 상세 (로그 칸) | `GET /api/portfolio/days/{date}` |
| 종목 상세 | `GET /api/portfolio/holdings/{code}` |
| 1. 판 뒤의 그림자 | `GET /api/portfolio/insights/sales` |
| 2. 그림자 계좌 | `GET /api/portfolio/insights/shadow` |
| 3. 손익 폭포수 | `GET /api/portfolio/insights/waterfall` |
| 4. 보유 기간 타임라인 | `GET /api/portfolio/insights/holding-periods` |
| 5. 수익 분해 | `GET /api/portfolio/insights/fx` |
| 6. 수중 차트 | `GET /api/portfolio/insights/underwater` |

## 계산 정의

- **하루 손익** `pnl_t = value_t − netCashFlow_t − value_{t−1}` (원). `netCashFlow`는 입금(+)·출금(−)만. 배당은 계좌 안의 수익이라 현금흐름이 아니다.
- **하루 수익률** `pnl_t / value_{t−1}` (전날 0이면 0). TWR = Π(1 + r) − 1.
- **포지션(라운드트립)**: 한 종목의 보유 수량이 0에서 양수가 된 날부터 다시 0이 된 날까지. 아직 보유 중이면 `close = null`.
- **포지션 수익률** = (매도 금액 + 현재 평가금 + 배당 − 매수 금액 − 수수료 − 세금) / 매수 금액. 모두 원화.
- **해외 수익 분해** (현재 보유분, 이동평균): 수량 q, 평균 매수가 P0, 매수 평균 환율 F0(매수 금액 가중), 현재가 P1, 현재 환율 F1
  - 주가 효과 = q × (P1 − P0) × F0
  - 환율 효과 = q × P1 × (F1 − F0)
  - 합 = q × (P1×F1 − P0×F0) (정확히 맞아떨어진다)

## GET /api/portfolio/summary

```json
{
  "account": { "name": "데모 계좌", "broker": "TOSS", "sample": true, "firstDate": "2024-10-14", "lastDate": "2026-10-16" },
  "asOf": "2026-10-16",
  "totalValue": 48210000, "netDeposits": 42000000, "profit": 6210000,
  "twr": 0.1342, "todayPnl": -182000, "todayReturn": -0.0038
}
```

## GET /api/portfolio/log?year=2026

`year` 생략 시 `lastDate`의 연도. 그해 1월 1일(또는 첫 기록일) ~ 12월 31일(또는 마지막 기록일).

```json
{
  "year": 2026, "availableYears": [2024, 2025, 2026],
  "from": "2026-01-02", "to": "2026-10-16",
  "days": [ { "date": "2026-01-02", "value": 41200000, "netCashFlow": 0, "pnl": 120000, "dailyReturn": 0.0029,
              "source": "COLLECTED" } ],
  "stats": { "gainDays": 104, "lossDays": 88, "flatDays": 3, "totalPnl": 3120000,
             "bestDay":  { "date": "2026-04-10", "pnl": 1320000 },
             "worstDay": { "date": "2026-04-07", "pnl": -1850000 } },
  "streaks": { "gain": { "longest": …, "current": … }, "loss": { "longest": …, "current": … } }
}
```

- `days`: 거래일마다 한 칸. `source` = `COLLECTED` | `BACKFILLED`(체결 내역으로 역산) | `MISSING`(수집 실패, `value`·`pnl`은 0이고 빈 칸으로 그림).
- `stats`: `pnl > 0` / `< 0` / `= 0`인 날 수(MISSING 제외). `streaks`는 `pnl` 부호 기준, 규칙은 simulation.md와 같다.

## GET /api/portfolio/holdings

현재 보유 종목, 평가금액 큰 순.

```json
[ { "instrument": { … }, "quantity": 12, "avgCostLocal": 182.4, "priceLocal": 201.3, "fxRate": 1385.2,
    "value": 3346000, "profit": 412000, "returnRate": 0.1404, "weight": 0.0694 } ]
```

## GET /api/portfolio/days/{date}

```json
{
  "date": "2026-04-07", "prevDate": "2026-04-06", "nextDate": "2026-04-08",
  "value": 45100000, "netCashFlow": 0, "pnl": -1850000, "dailyReturn": -0.0394,
  "contributions": [ { "instrument": { … }, "pnl": -620000, "dailyReturn": -0.051, "weight": 0.27 } ],
  "trades": [ Trade ],
  "cashFlows": [ { "type": "DEPOSIT" | "WITHDRAWAL" | "DIVIDEND", "amount": 1000000, "instrument": null } ]
}
```

- `contributions`: 그날 보유 종목별 손익(원, 환율 변동 포함), 절댓값 큰 순. 합 + 배당 − 수수료 − 세금 = `pnl`(반올림 오차 제외).
- `prevDate`/`nextDate`: 기록이 있는 이전·다음 거래일, 없으면 null.

## GET /api/portfolio/holdings/{code}

보유 중이거나 과거에 거래한 종목. 보유 이력이 없으면 404.

```json
{
  "instrument": { … },
  "position": { "quantity": 12, "avgCostLocal": 182.4, "priceLocal": 201.3, "fxRate": 1385.2,
                "value": 3346000, "profit": 412000, "returnRate": 0.1404 } ,
  "realizedProfit": 230000, "dividends": 41000,
  "prices": [ { "date": "2025-01-02", "closeLocal": 180.1 } ],
  "trades": [ Trade ],
  "periods": [ HoldingPeriod ],
  "sales":   [ Sale ],
  "fx": FxItem | null
}
```

- `position`: 지금 보유하지 않으면 null. `prices`: 첫 거래 60거래일 전 ~ 마지막 날.
- `fx`: 해외 종목이고 보유 중일 때만.

## 1. GET /api/portfolio/insights/sales?horizon=20

매도 한 건마다 매도 뒤 주가를 이어 그리고, 매도 판단을 채점한다.

```json
{
  "horizon": 20,
  "summary": { "sales": 14, "judged": 12, "good": 7, "winRate": 0.5833,
               "avoidedLoss": 820000, "missedGain": 1430000 },
  "sales": [ Sale ]
}
Sale = {
  "id": "s-12", "date": "2026-02-11", "instrument": { … }, "quantity": 10, "priceLocal": 72000, "fxRate": 1,
  "amount": 720000, "realizedProfit": 85000,
  "priceNowLocal": 78100, "changeSinceSale": 0.0847,
  "changeAfter": { "d5": 0.012, "d20": 0.041, "d60": null },
  "verdict": "GOOD" | "BAD" | "PENDING",
  "afterEffect": -29520,
  "series": [ { "date": "2025-11-14", "closeLocal": 70100 } ]
}
```

- `horizon` ∈ {5, 20, 60} 거래일, 기본 20. `changeAfter.dN` = 매도 N거래일 뒤 종가 / 매도가 − 1(아직 안 지났으면 null).
- `verdict`: horizon 거래일 뒤 종가가 매도가 이하면 `GOOD`(판 게 나았음), 초과면 `BAD`, 아직 안 지났으면 `PENDING`.
- `afterEffect` = 수량 × (horizon 뒤 종가 − 매도가) × 그날 환율(원)의 부호를 바꾼 값: +면 피한 손실, −면 놓친 이익. PENDING이면 0.
- `summary.avoidedLoss` / `missedGain`: GOOD들의 afterEffect 합 / BAD들의 afterEffect 절댓값 합.
- `series`: 매도 60거래일 전 ~ 마지막 날. 화면은 매도일 이후를 점선으로 그린다.

## 2. GET /api/portfolio/insights/shadow?benchmark=SPX

실제 입금·출금 날짜와 금액 그대로 지수에 넣었다면(simulation의 벤치마크 리플레이와 같은 함수).

```json
{
  "benchmarks": [ { "code": "SPX", "name": "S&P500" }, { "code": "KOSPI200", "name": "코스피200" } ],
  "benchmark": "SPX",
  "series": [ { "date": "2024-10-14", "actual": 10000000, "shadow": 10000000, "netDeposits": 10000000 } ],
  "summary": { "actual": 48210000, "shadow": 51030000, "difference": -2820000,
               "actualTwr": 0.1342, "shadowTwr": 0.2011 }
}
```

- S&P500은 그날 USD/KRW 환율로 원화 환산한 값에 넣는다. `difference = actual − shadow`.

## 3. GET /api/portfolio/insights/waterfall?period=YTD

`period` ∈ `1M` | `3M` | `YTD` | `1Y` | `ALL`, 기본 `YTD`.

```json
{
  "period": "YTD", "from": "2025-12-31", "to": "2026-10-16",
  "startValue": 40100000, "netCashFlow": 6000000, "endValue": 48210000,
  "items": [
    { "kind": "INSTRUMENT", "label": "삼성전자", "instrument": { … }, "amount": 1240000 },
    { "kind": "DIVIDEND", "label": "배당", "instrument": null, "amount": 210000 },
    { "kind": "FEE", "label": "수수료", "instrument": null, "amount": -18000 },
    { "kind": "TAX", "label": "세금", "instrument": null, "amount": -42000 },
    { "kind": "FX", "label": "환율", "instrument": null, "amount": 380000 }
  ],
  "total": 2110000
}
```

- `from`은 기간 시작 직전 거래일(그날 종가가 출발점). `total = endValue − startValue − netCashFlow = Σ items.amount`. 반올림으로 생기는 차이는 마지막에 `{ "kind": "OTHER", "label": "기타" }` 한 줄로 맞춘다(0이면 생략).
- `INSTRUMENT`: 종목별 **주가 효과**(해외는 환율 고정). 환율 효과는 모든 해외 종목을 합쳐 `FX` 한 줄. 종목은 금액 큰 순(+ 먼저, 그다음 −).

## 4. GET /api/portfolio/insights/holding-periods

```json
{
  "periods": [ HoldingPeriod ],
  "buckets": [ { "key": "SHORT", "label": "단타 (7일 이하)", "count": 9, "winRate": 0.44, "avgReturn": -0.012, "profit": -310000 } ]
}
HoldingPeriod = { "id": "p-3", "instrument": { … }, "open": "2025-03-04", "close": "2025-03-07" | null,
                  "days": 3, "returnRate": -0.021, "profit": -42000, "status": "CLOSED" | "OPEN" }
```

- `days` = close(또는 마지막 날) − open (달력일). 버킷: `SHORT` ≤ 7일, `SWING` 8–90일, `MID` 91–365일, `LONG` > 365일. 버킷은 이 순서로 항상 4개(count 0 포함). 보유 중 포지션도 평가 기준으로 포함.
- `periods`: open 오름차순.

## 5. GET /api/portfolio/insights/fx

```json
{
  "asOf": "2026-10-16",
  "items": [ FxItem ],
  "total": { "priceEffect": 820000, "fxEffect": 310000, "profit": 1130000 }
}
FxItem = { "instrument": { … }, "quantity": 12, "avgCostLocal": 182.4, "avgFxRate": 1342.1,
           "priceLocal": 201.3, "fxRate": 1385.2, "priceEffect": 303000, "fxEffect": 104000, "profit": 407000 }
```

현재 보유 중인 해외 종목만. `profit` = priceEffect + fxEffect.

## 6. GET /api/portfolio/insights/underwater

```json
{
  "series": [ { "date": "2026-04-07", "drawdown": -0.142, "drawdownAmount": -6100000 } ],
  "episodes": [ { "peak": "2026-02-18", "trough": "2026-04-07", "recovered": "2026-06-30" | null,
                  "depth": -0.142, "daysToTrough": 48, "daysToRecover": 84 | null } ],
  "current": { "drawdown": -0.031, "peak": "2026-09-22", "days": 24,
               "deepestPast": -0.142, "deeperThanPast": false }
}
```

- `drawdown`: TWR 지수의 직전 고점 대비(0 이하). `drawdownAmount`: 평가손익(평가금 − 순입금)의 직전 고점 대비(원, 0 이하). 화면은 simulation의 고통 지표처럼 수익률/금액 기준을 고를 수 있다.
- `episodes`: drawdown이 0 아래로 내려갔다가 0으로 돌아온(또는 아직 못 돌아온) 구간, 깊은 순. `days*`는 달력일.
- `current`: 지금 고점 아래면 그 구간, 고점이면 `drawdown: 0, peak: 그날, days: 0`. `deepestPast`는 현재 구간을 뺀 과거 구간 중 가장 깊은 값(없으면 0).
