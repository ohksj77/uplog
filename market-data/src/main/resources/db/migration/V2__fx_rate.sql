-- 일별 환율(장 마감 기준). pair는 'USDKRW'처럼 기준통화+상대통화, rate는 기준통화 1단위의 상대통화 값.
-- 행이 있는 날이 고시일이고, 휴일에는 직전 고시 환율을 쓴다(calc-engine의 PriceSeries.closeOnOrBefore).
-- instrument.type은 V1부터 ETF·INDEX·STOCK을 받고, market은 자유 문자열(KRX, KR, US, SAMPLE …)이다.

CREATE TABLE fx_rate (
    pair VARCHAR(6)     NOT NULL,
    date DATE           NOT NULL,
    rate NUMERIC(20, 6) NOT NULL,
    CONSTRAINT pk_fx_rate PRIMARY KEY (pair, date),
    CONSTRAINT ck_fx_rate_rate CHECK (rate > 0)
);
