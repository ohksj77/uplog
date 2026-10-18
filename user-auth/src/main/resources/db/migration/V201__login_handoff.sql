-- 다른 기기(폰 → PC)에서 같은 사용자로 이어 하기 위한 1회용 코드. 코드 자체는 저장하지 않고 SHA-256만 둔다.
CREATE TABLE login_handoff (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP
);
