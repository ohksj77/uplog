# 토스증권 Open API 확인 메모

2026-10-18에 공개 문서로 확인한 내용이다. 실제 키로 호출해 본 것은 아니다.

- 출처
  - [개발자 문서](https://developers.tossinvest.com/docs), [llms.txt](https://developers.tossinvest.com/llms.txt)
  - [OpenAPI 명세 1.2.24](https://openapi.tossinvest.com/openapi-docs/latest/openapi.json), [개요](https://openapi.tossinvest.com/openapi-docs/overview.md), [FAQ](https://openapi.tossinvest.com/openapi-docs/faq.md)
- 명세와 다르게 동작하는 부분이 있을 수 있으니, 실제 키로 처음 연결할 때 응답 원문을 기록해 대조한다.

## 기획서 "확인 필요" 항목에 대한 답

| 항목 | 답 | 영향 |
|---|---|---|
| 조회 전용 키·권한 범위가 있는가 | **없다.** OAuth2 Client Credentials에 scope가 없고, 같은 키로 주문 API를 부를 수 있다 | 키 유출 = 주문 가능. 어댑터는 조회 API만 구현하고, 나가는 요청을 허용 목록(토큰 발급 + GET 조회)으로 막는다. 암호화 보관·즉시 파기는 필수 |
| 체결 내역 조회 가능 기간 | 계좌 개설 이후 **전체**. `GET /api/v1/orders?status=CLOSED`, 페이지당 최대 100건, `cursor` | 가입 전 체결을 전부 받아 역산할 수 있다 |
| 과거 시세 | 일봉(`1d`)은 기간 제한 없음, 200개씩 `before` 페이지네이션, 수정주가 선택 가능. 상장폐지 종목은 404 | 보유했던 종목의 일봉을 받아 역산한다. 상장폐지 종목은 마지막 체결가로 처리 |
| 허용 IP 등록 | 사용자가 WTS > 설정 > Open API > 허용 IP 관리에 **토스를 부르는 기기의 공인 IP를 직접 등록**해야 한다. 미등록이면 403 `ip-not-allowed` | 맥 앱: 연결 화면에서 이 맥의 공인 IP를 확인(`/api/desktop/public-ip`)해 등록하게 한다. 공유기·통신사가 IP를 바꾸면 다시 등록해야 한다 |
| 입출금·배당 내역 | **API 없음** | 아래 "현금흐름 추정" |
| 현금 잔액 | 전용 API 없음. `GET /api/v1/buying-power?currency=KRW|USD`의 `cashBuyingPower`(미수 미발생 기준 매수 가능 금액) | 현금 = 매수 가능 금액으로 근사 |

## WTS 화면 (사용자 스크린샷으로 확인, 2026-10-18)

- 위치: 별도 주소 없음. PC에서 https://www.tossinvest.com/ 로그인 → 오른쪽 사이드바 맨 아래 톱니(설정) → 설정 팝업 왼쪽 메뉴 **Open API**.
- **Open API Key 관리**: Client Id(`tsck_live_…`, 복사 버튼), Client Secret(`tssk_live_…`, 발급 뒤에는 가려지고 복사 버튼 없음 → 발급할 때만 볼 수 있음), 발급일, 만료일(**발급 1년 뒤**), 상태(활성) + **재발급**.
- **허용 IP 관리**: **IP 추가**로 여러 개 등록, 항목마다 삭제.
- 화면 아래 "Open API 가이드 문서" 링크.

## 인증

- `POST /oauth2/token` (form-urlencoded, `grant_type=client_credentials`, `client_id`, `client_secret`) → `{access_token, token_type, expires_in}`. refresh token은 없고, 만료되면 다시 발급받는다.
- **토큰은 클라이언트당 1개만 유효하다.** 새로 발급하면 이전 토큰은 `401 token-revoked`가 된다. 사용자가 같은 키를 다른 도구에서도 쓰면 서로의 토큰을 무효화하므로, 401 `token-revoked`를 받으면 한 번만 다시 발급받고, 반복되면 "다른 곳에서 같은 키를 쓰고 있음"으로 표시한다.
- 계좌·자산·주문 API에는 `X-Tossinvest-Account: {accountSeq}`가 필요하다(`GET /api/v1/accounts`, 현재는 종합매매 `BROKERAGE`만 반환).

## 쓰는 엔드포인트 (조회만)

| 용도 | 엔드포인트 | Rate Limit 그룹 |
|---|---|---|
| 계좌 목록 | `GET /api/v1/accounts` | ACCOUNT (초당 1) |
| 보유 종목 스냅샷 | `GET /api/v1/holdings` | ASSET (초당 5) |
| 체결 내역 | `GET /api/v1/orders?status=CLOSED&from&to&cursor&limit=100` | ORDER_HISTORY (초당 5) |
| 현금(근사) | `GET /api/v1/buying-power?currency=KRW` / `USD` | ORDER_INFO (초당 6, 09:00–09:10 초당 3) |
| 환율 | `GET /api/v1/exchange-rate?dateTime&baseCurrency=USD&quoteCurrency=KRW` | MARKET_INFO (초당 3) |
| 일봉 | `GET /api/v1/candles?symbol&interval=1d&count=200&before&adjusted` | MARKET_DATA_CHART (초당 20) |
| 장 운영일 | `GET /api/v1/market-calendar/{KR|US}?date` | MARKET_INFO |
| 시장별 전체 종목(기능 A 검색) | `GET /api/v1/stocks/all?market=KOSPI|KOSDAQ|NYSE|NASDAQ|AMEX&status=ACTIVE` — `[{symbol, name, securityType, isCommonShare, isinCode}]`, 페이지 없이 한 번에(NASDAQ 약 2,800건). 하루 1회 캐시 | STOCK_ALL (초당 1) |
| 종목 상세 | `GET /api/v1/stocks?symbols=a,b`(최대 200) — market, currency, status, listDate 등 | STOCK (초당 5) |

- 응답 헤더 `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, 429일 때 `Retry-After`. 한도는 사전 공지 없이 바뀔 수 있다.
- 체결 내역 주의: Open API가 지원하는 호가 유형(지정가·시장가·장마감지정가)으로 접수된 주문만 조회된다. 시간외 종가 주문 등은 빠진다.
- 체결의 `execution`: `filledQuantity`, `averageFilledPrice`, `filledAmount`, `commission`, `tax`, `filledAt`, `settlementDate`. 체결 환율은 없어서 `filledAt` 시점 참고 환율(`exchange-rate?dateTime`)로 환산한다(실제 거래 환율과 다를 수 있음).
- 숫자는 모두 문자열이다.
- `securityType`: STOCK, FOREIGN_STOCK, DEPOSITARY_RECEIPT, INFRASTRUCTURE_FUND, REIT, ETF, FOREIGN_ETF, ETN, STOCK_WARRANTS. 명세는 미국 ETF가 `ETF`인지 `FOREIGN_ETF`인지 적어 두지 않아서 둘 다 ETF로 본다(실제 키로 확인 필요).
- 일봉 `nextBefore`는 그 페이지 가장 오래된 봉의 시각이고 `before`는 포함(inclusive)이라 다음 페이지 첫 봉이 겹친다.

## 현금흐름 추정 (입출금·배당 API가 없어서)

연결 이후 하루 단위로:

```
외부 유입_t = (현금_t − 현금_{t−1}) − (매도 금액 − 매수 금액 − 수수료 − 세금)_t
```

- 현금은 KRW·USD 매수 가능 금액을 그날 환율로 원화 합산한 값이다.
- 외부 유입 > 0이면 입금 또는 배당·이자, < 0이면 출금으로 본다. 배당과 입금은 구분할 수 없다. 보유 종목의 배당 일정과 맞는 소액 유입만 "배당 추정"으로 표시하는 것은 후속 과제다.
- 결제일(D+2) 때문에 매수 가능 금액이 매매 당일과 어긋날 수 있다. 그래서 추정치는 며칠 단위로 보정될 수 있고, 화면에는 "추정"으로 표시한다.
- **연결 전 기간(역산)** 은 현금 이력이 없다. 현재 구현은 체결 장부의 현금이 음수가 되는 날마다 그 부족분만큼을 `INFERRED` 입금으로 넣고(`inferred-backfill-<날짜>`), 첫 일일 수집에서 실제 현금과 장부의 차이를 그날 한 번의 유입으로 기록한다(`uplog.ingest.infer-backfill-deposits`, 기본 켜짐).
  - 한계: 실제로는 더 많은 현금이 있었을 수 있어서, 역산 구간의 수익률은 실제보다 작은 원금 기준으로 계산된다. 그래서 TWR이 단순 수익률보다 크게 나올 수 있다(로컬 가짜 계좌: TWR +63.9%, 손익/순입금 약 27%).
  - 대안: 역산 구간은 "보유 종목 평가손익"만 보여 주고 TWR은 연결 이후만 계산하는 방식. 실제 계좌로 확인한 뒤 고른다.
