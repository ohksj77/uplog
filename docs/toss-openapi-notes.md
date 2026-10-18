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
| 서버 IP 허용 등록 | 사용자가 WTS > 설정 > Open API > 허용 IP 관리에 **우리 서버 IP를 직접 등록**해야 한다. 미등록이면 403 `ip-not-allowed` | 연결 화면에서 우리 고정 IP를 보여 주고 복사하게 한다. 수집 워커는 고정 IP 뒤에 둔다 |
| 입출금·배당 내역 | **API 없음** | 아래 "현금흐름 추정" |
| 현금 잔액 | 전용 API 없음. `GET /api/v1/buying-power?currency=KRW|USD`의 `cashBuyingPower`(미수 미발생 기준 매수 가능 금액) | 현금 = 매수 가능 금액으로 근사 |

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

- 응답 헤더 `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, 429일 때 `Retry-After`. 한도는 사전 공지 없이 바뀔 수 있다.
- 체결 내역 주의: Open API가 지원하는 호가 유형(지정가·시장가·장마감지정가)으로 접수된 주문만 조회된다. 시간외 종가 주문 등은 빠진다.
- 체결의 `execution`: `filledQuantity`, `averageFilledPrice`, `filledAmount`, `commission`, `tax`, `filledAt`, `settlementDate`. 체결 환율은 없어서 `filledAt` 시점 참고 환율(`exchange-rate?dateTime`)로 환산한다(실제 거래 환율과 다를 수 있음).
- 숫자는 모두 문자열이다.

## 현금흐름 추정 (입출금·배당 API가 없어서)

연결 이후 하루 단위로:

```
외부 유입_t = (현금_t − 현금_{t−1}) − (매도 금액 − 매수 금액 − 수수료 − 세금)_t
```

- 현금은 KRW·USD 매수 가능 금액을 그날 환율로 원화 합산한 값이다.
- 외부 유입 > 0이면 입금 또는 배당·이자, < 0이면 출금으로 본다. 배당과 입금은 구분할 수 없다. 보유 종목의 배당 일정과 맞는 소액 유입만 "배당 추정"으로 표시하는 것은 후속 과제다.
- 결제일(D+2) 때문에 매수 가능 금액이 매매 당일과 어긋날 수 있다. 그래서 추정치는 며칠 단위로 보정될 수 있고, 화면에는 "추정"으로 표시한다.
- **연결 전 기간(역산)** 은 현금 이력이 없다. 그래서 역산 구간의 로그는 "보유 종목 평가손익"만 보여 주고, 입출금과 현금은 비워 둔다.
