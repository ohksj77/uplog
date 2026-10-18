-- 증권사 식별자 길이를 넉넉히. 토스 orderId는 86자(base64url)라 VARCHAR(64)에 들어가지 않았다.
ALTER TABLE trade ALTER COLUMN broker_trade_id SET DATA TYPE VARCHAR(255);
ALTER TABLE cash_flow ALTER COLUMN broker_flow_id SET DATA TYPE VARCHAR(255);
ALTER TABLE dividend ALTER COLUMN broker_dividend_id SET DATA TYPE VARCHAR(255);
