package com.uplog.portfolio.connection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uplog.portfolio.connect.BrokerAccount;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 연결 API 요청·응답(docs/design/account-connection.md "API 계약"). */
public final class ConnectionDtos {

    /** 이 맥의 공인 IP를 확인하는 엔진 API(누를 때만 부른다). setup과 TOSS_IP_NOT_ALLOWED 오류가 가리킨다. */
    public static final String PUBLIC_IP_CHECK = "/api/desktop/public-ip";

    private ConnectionDtos() {}

    /** secret이 로그·오류에 찍히지 않게 toString을 가린다. */
    public record ConnectRequest(String clientId, String clientSecret) {
        @Override
        public String toString() {
            return "ConnectRequest[clientId=" + clientId + ", clientSecret=***]";
        }
    }

    public record ChooseAccountRequest(String clientId, String clientSecret, String accountSeq) {
        @Override
        public String toString() {
            return "ChooseAccountRequest[clientId=" + clientId + ", clientSecret=***, accountSeq=" + accountSeq + "]";
        }
    }

    /** status: CHOOSE_ACCOUNT(accounts 있음) | BACKFILLING */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConnectResponse(String status, List<AccountOption> accounts) {}

    public record AccountOption(String accountSeq, String maskedAccountNo, String type) {
        static AccountOption of(BrokerAccount a) {
            return new AccountOption(a.accountSeq(), a.maskedAccountNo(), a.type());
        }
    }

    /**
     * @param publicIpCheck 허용 IP 단계에서 이 맥의 공인 IP를 확인할 엔진 API 경로({@value #PUBLIC_IP_CHECK}). 사용자가 누를 때만 부른다
     * @param simulated 가짜 토스 서버에 연결되는 개발 환경이면 true
     */
    public record SetupResponse(String publicIpCheck, URI keyPageUrl, List<String> notes, boolean simulated) {}

    /** @param state PENDING | RUNNING | DONE | FAILED */
    public record Backfill(String state, double progress) {}

    /** status: BACKFILLING | ACTIVE | ERROR */
    public record ConnectionStatusResponse(
            String status, String maskedAccountNo, LocalDate connectedAt, Backfill backfill, Instant lastSyncedAt,
            String lastError, List<LocalDate> recentGaps) {}

    public record MeUser(long id, String nickname, String provider) {}

    /** status: NONE | BACKFILLING | ACTIVE | ERROR */
    public record MeConnection(String status, String maskedAccountNo) {}

    public record MeResponse(MeUser user, MeConnection connection) {}

    /**
     * 공통 오류 형식. publicIpCheck는 TOSS_IP_NOT_ALLOWED일 때만({@value #PUBLIC_IP_CHECK}), field는 틀린 입력을 알 때만
     * (clientId | clientSecret).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String code, String message, String publicIpCheck, String field) {

        public ErrorResponse(String code, String message) {
            this(code, message, null, null);
        }
    }

    /** 연결한 계좌의 키만 바꾼다(토큰이 끊겼거나 키를 다시 발급했을 때). */
    public record RekeyRequest(String clientId, String clientSecret) {}
}
