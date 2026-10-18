package com.uplog.portfolio.connect;

/** 수집 작업이 복호화된 키를 받는 유일한 통로. */
public interface CredentialReader {

    /** @throws CredentialNotFoundException 연결이 해제돼 키가 파기된 경우 */
    BrokerCredentials read(long accountId);

    final class CredentialNotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public CredentialNotFoundException(long accountId) {
            super("계좌 " + accountId + "의 키가 없습니다(연결 해제됨)");
        }
    }
}
