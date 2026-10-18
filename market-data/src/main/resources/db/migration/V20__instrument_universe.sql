-- 기능 A 실제 종목: 시장별 종목 목록(유니버스) 동기화 기록과 종목별 일봉 확인 구간.
-- V4–V19는 다른 작업이 쓸 수 있게 비워 둔다(out-of-order 켬).

-- 시장(KOSPI, KOSDAQ, NYSE, NASDAQ, AMEX)마다 마지막으로 목록을 받은 날(KST). 하루 한 번만 받는다.
CREATE TABLE instrument_universe_sync (
    listing_market   VARCHAR(10) NOT NULL,
    synced_on        DATE        NOT NULL,
    instrument_count INT         NOT NULL,
    CONSTRAINT pk_instrument_universe_sync PRIMARY KEY (listing_market)
);

-- 증권사에서 일봉을 받아 확인한 구간. [covered_from, covered_to]의 거래일 시세는 daily_price에 모두 있거나,
-- (상장 전·휴장이라) 없다는 것을 확인했다. 요청 구간에서 이 구간 밖만 새로 받는다.
CREATE TABLE price_coverage (
    instrument_id BIGINT    NOT NULL,
    covered_from  DATE      NOT NULL,
    covered_to    DATE      NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT pk_price_coverage PRIMARY KEY (instrument_id),
    CONSTRAINT fk_price_coverage_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);
