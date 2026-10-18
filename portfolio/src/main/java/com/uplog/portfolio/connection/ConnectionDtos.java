package com.uplog.portfolio.connection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uplog.portfolio.connect.BrokerAccount;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 연결 API 요청·응답(docs/design/account-connection.md "API 계약"). */
public final class ConnectionDtos {

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

    public record SetupResponse(List<String> egressIps, URI keyPageUrl, List<String> notes) {}

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

    /** 공통 오류 형식. egressIps는 TOSS_IP_NOT_ALLOWED일 때만 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String code, String message, List<String> egressIps) {}
}
