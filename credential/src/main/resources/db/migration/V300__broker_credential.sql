-- 증권사 Open API 키(봉투 암호화). 마이그레이션 버전 구간: credential V300–V399.
-- secret은 비밀마다 새로 만든 256비트 데이터키로 AES-256-GCM 암호화하고(secret_iv), 데이터키는 마스터키로 암호화해
-- data_key_ciphertext(IV 포함, 형식은 마스터키 제공자가 정함)에 둔다. GCM 추가 인증 데이터에 account_id와 client_id를 넣어
-- 행을 바꿔치기하거나 client_id를 고치면 복호화가 실패한다. 연결 해제 시 행을 지운다(즉시 파기).

CREATE TABLE broker_credential (
    account_id          BIGINT       NOT NULL PRIMARY KEY,
    client_id           VARCHAR(200) NOT NULL,
    secret_ciphertext   BYTEA        NOT NULL,
    secret_iv           BYTEA        NOT NULL,
    data_key_ciphertext BYTEA        NOT NULL,
    master_key_id       VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_broker_credential_account FOREIGN KEY (account_id) REFERENCES account (id)
);
