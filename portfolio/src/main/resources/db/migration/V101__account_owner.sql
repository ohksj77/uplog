-- 3단계(로그인·계좌 연결): 계좌의 주인과 연결 상태, 현금흐름의 출처.
-- user_id는 app_user(id)를 가리키지만 user-auth 마이그레이션(V200–)이 뒤에 돌아서 외래키는 두지 않는다. 데모 계좌는 null.
-- 사용자당 ACTIVE 계좌는 하나다(연결 서비스가 트랜잭션 안에서 확인한다).

ALTER TABLE account ADD COLUMN user_id BIGINT;
-- 토스 X-Tossinvest-Account 값과 화면용 가린 계좌번호
ALTER TABLE account ADD COLUMN broker_account_seq VARCHAR(64);
ALTER TABLE account ADD COLUMN masked_account_no VARCHAR(64);
ALTER TABLE account ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE account ADD CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'DISCONNECTED'));
CREATE INDEX ix_account_user ON account (user_id);

-- SEED: 증권사·시드가 준 실제 입출금, INFERRED: 매수 가능 금액 변화로 추정한 유입·유출
ALTER TABLE cash_flow ADD COLUMN source VARCHAR(10) DEFAULT 'SEED' NOT NULL;
ALTER TABLE cash_flow ADD CONSTRAINT ck_cash_flow_source CHECK (source IN ('SEED', 'INFERRED'));
