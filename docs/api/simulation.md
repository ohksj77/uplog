# 기능 A — 시뮬레이션 API 계약 (v1)

프론트(`web/`)와 백엔드(`simulation`, `market-data`)가 함께 따르는 계약이다. 바꾸려면 이 문서와 `web/src/lib/api/types.ts`를 함께 고친다.

## 공통

- 금액: 원화 정수(JSON number, 원 단위 반올림). v1은 국내 상장 ETF(KRW)만 다룬다.
- 비율: 소수(0.1234 = 12.34%).
- 날짜: `YYYY-MM-DD`.
- 오류: HTTP 4xx/5xx + `{"code": "INVALID_PARAMETER", "message": "사람이 읽을 문장"}`
  - `INVALID_PARAMETER`(400), `ETF_NOT_FOUND`(404), `NO_PRICE_DATA`(422: 기간 안에 시세가 없음)

## GET /api/etfs?query={검색어}&limit={기본 20}

이름 또는 종목코드 부분 일치. `query`가 비면 거래대금 등 기준 없이 이름순 상위 `limit`개.

```json
[
  { "code": "069500", "name": "KODEX 200", "currency": "KRW", "sample": false,
    "firstDate": "2002-10-14", "lastDate": "2026-10-16" }
]
```

`sample: true`는 로컬 개발용 합성 시세다. 화면에 반드시 "샘플 데이터" 배지를 붙인다.

## GET /api/simulations

| 파라미터 | 필수 | 값 |
|---|---|---|
| `etfs` | O | 종목코드 쉼표 구분, 1–3개 |
| `period` | O | `6M` \| `1Y` \| `3Y` — 종료일은 시세 마지막 날, 시작일은 그로부터 기간만큼 전 |
| `frequency` | O | 적립 주기 `DAILY` \| `WEEKLY` \| `MONTHLY` |
| `dayOfWeek` | WEEKLY일 때 | `MONDAY` … `FRIDAY` |
| `dayOfMonth` | MONTHLY일 때 | 1–31 (없는 날짜면 말일) |
| `amountType` | O | `TOTAL`(총 금액을 회차로 나눔) \| `PER_PURCHASE`(회차당 금액) |
| `amount` | O | 원, 1,000 이상 10억 이하 정수 |

한 번의 요청으로 ETF마다 **거치식과 적립식을 같은 총 투입금으로** 계산해 돌려준다(기획서: 두 로그를 위아래로 비교).

- `TOTAL`: 적립식 회차당 금액 = amount / 회차 수(원 단위 내림), 거치식 = 적립식 실제 총 투입금
- `PER_PURCHASE`: 적립식 회차당 금액 = amount, 거치식 = amount × 회차 수

```json
{
  "from": "2025-10-16",
  "to": "2026-10-16",
  "results": [
    {
      "etf": { "code": "069500", "name": "KODEX 200", "currency": "KRW", "sample": false },
      "lumpSum":   { "...": "SimulationResult" },
      "recurring": { "...": "SimulationResult" }
    }
  ],
  "disclaimer": "과거 수익률은 미래 수익을 보장하지 않습니다. 이 계산은 투자 권유가 아닙니다."
}
```

### SimulationResult

```json
{
  "summary": {
    "finalValue": 13400000, "invested": 12000000, "profit": 1400000,
    "totalReturn": 0.1167, "twr": 0.0812, "annualizedTwr": 0.0812
  },
  "pain": {
    "maxDrawdown": { "rate": -0.183, "peak": "2026-03-04", "trough": "2026-04-09" },
    "underwater":  { "start": "2026-03-20", "end": "2026-06-11", "recovered": true, "days": 83 },
    "worstDay":    { "date": "2026-04-07", "dailyReturn": -0.052 }
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

- `pain.underwater`: 평가금액이 원금 아래로 내려간 적이 없으면 `null`. `recovered: false`면 `end` = 마지막 날.
- `streaks`: 연속 기록. 기록이 없으면 `longest`가 `null`, 마지막 날까지 이어지는 연속이 없으면 `current`가 `null`.
  - `gain` / `loss`: 그날 수익률(`dailyReturn`)이 0보다 큰 / 작은 거래일이 이어진 기록(로그 "일별 손익" 뷰의 빨간 / 파란 칸). 0%인 날은 연속을 끊는다.
  - `purchase`: 매수가 있었던 주기가 이어진 기록. `unit` = `DAY`(거래일) \| `WEEK` \| `MONTH`(적립 주기). `start`/`end`는 첫·마지막 매수일. 마지막 주기에 아직 매수가 없으면(예: 이번 달 적립일 전) 직전 주기까지를 `current`로 본다. 거치식은 `null`.
- `days`: 첫 매수일부터 종료일까지 **거래일마다 한 칸**, 날짜 오름차순.
  - `dailyReturn`: 입출금을 제거한 그날 수익률(로그 "일별 손익" 뷰)
  - 누적 수익률 뷰 = `value / invested − 1` (프론트 계산)
  - 매수 단가 대비 뷰 = `purchased`인 날만 `마지막 close / 그날 close − 1` (프론트 계산)

## 후속(v2 후보, 지금은 없음)

배당 재투자, 수수료·세금 반영, 해외 ETF 원화 환산, 적립일 바꿨을 때의 결과 비교, 공유 카드.
