package com.uplog.ingest;

import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.CredentialReader;

/** 수집 실패를 사용자에게 보여 줄 문장과 재시도 여부로 바꾼다. 예외 메시지는 그대로 내보내지 않는다. */
public final class IngestErrors {

    /** TOKEN_REVOKED의 안내. 화면(lastError)에 그대로 보인다. */
    public static final String KEY_IN_USE_ELSEWHERE =
            "다른 곳에서 같은 키를 사용 중이에요. 토스증권 Open API 키를 이 서비스에서만 쓰거나, 새 키를 발급받아 다시 연결해 주세요.";
    public static final String IP_NOT_ALLOWED =
            "토스증권이 우리 서버 IP를 허용하지 않았어요. 토스증권 WTS > 설정 > Open API > 허용 IP 관리에 연결 화면의 IP를 등록해 주세요.";

    private IngestErrors() {}

    /** 연결이 해제된(DISCONNECTED) 계좌의 작업. 다시 하지 않는다. */
    public static final class AccountDisconnectedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public AccountDisconnectedException(long accountId) {
            super("연결이 해제된 계좌입니다: " + accountId);
        }
    }

    /** @param permanent 다시 해도 같은 결과라 바로 FAILED로 둔다 */
    public record Failure(String reason, String message, boolean permanent) {}

    public static Failure classify(Throwable e) {
        if (e instanceof BrokerException b) {
            return switch (b.reason()) {
                case TOKEN_REVOKED -> new Failure(b.reason().name(), KEY_IN_USE_ELSEWHERE, false);
                case IP_NOT_ALLOWED -> new Failure(b.reason().name(), IP_NOT_ALLOWED, true);
                case INVALID_CREDENTIALS -> new Failure(b.reason().name(),
                        "토스증권 키가 더 이상 유효하지 않아요. 키를 확인하고 다시 연결해 주세요.", true);
                case NO_BROKERAGE_ACCOUNT -> new Failure(b.reason().name(), "토스증권 종합매매 계좌를 찾을 수 없어요.", true);
                case RATE_LIMITED -> new Failure(b.reason().name(), "토스증권 요청 한도를 넘었어요. 잠시 뒤 다시 수집합니다.", false);
                case UNAVAILABLE -> new Failure(b.reason().name(), "토스증권 응답이 원활하지 않아요. 잠시 뒤 다시 수집합니다.", false);
            };
        }
        if (e instanceof AccountDisconnectedException) {
            return new Failure("DISCONNECTED", "연결이 해제된 계좌예요.", true);
        }
        if (e instanceof CredentialReader.CredentialNotFoundException) {
            return new Failure("CREDENTIAL_NOT_FOUND", "연결이 해제돼 키가 없어요. 다시 연결해 주세요.", true);
        }
        if (e instanceof org.springframework.beans.factory.NoSuchBeanDefinitionException) {
            return new Failure("CREDENTIAL_NOT_FOUND", "키 저장소가 설정되지 않았어요.", true);
        }
        return new Failure("INTERNAL", "수집 중 오류가 생겼어요. 잠시 뒤 다시 수집합니다.", false);
    }
}
