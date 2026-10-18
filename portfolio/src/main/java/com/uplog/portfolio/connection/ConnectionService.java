package com.uplog.portfolio.connection;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uplog.auth.AuthUser;
import com.uplog.auth.UnauthenticatedException;
import com.uplog.auth.UserRepository;
import com.uplog.core.WeekdayTradingCalendar;
import com.uplog.portfolio.PortfolioDataVersion;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.PortfolioRepository.Account;
import com.uplog.portfolio.PortfolioRepository.AccountStatus;
import com.uplog.portfolio.connect.BrokerAccount;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.BrokerVerifier;
import com.uplog.portfolio.connect.IngestScheduler;
import com.uplog.portfolio.connect.IngestStatus;
import com.uplog.portfolio.connection.ConnectionDtos.AccountOption;
import com.uplog.portfolio.connection.ConnectionDtos.Backfill;
import com.uplog.portfolio.connection.ConnectionDtos.ConnectResponse;
import com.uplog.portfolio.connection.ConnectionDtos.ConnectionStatusResponse;
import com.uplog.portfolio.connection.ConnectionDtos.MeConnection;
import com.uplog.portfolio.connection.ConnectionDtos.MeResponse;
import com.uplog.portfolio.connection.ConnectionDtos.MeUser;
import com.uplog.portfolio.connection.ConnectionDtos.SetupResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 토스 계좌 연결(docs/design/account-connection.md). 키는 검증을 통과한 뒤에만 저장하고, 로그·오류·응답에 secret을 남기지 않는다.
 * 사용자당 ACTIVE 계좌는 하나다.
 */
@Service
public class ConnectionService {

    static final String BROKERAGE = "BROKERAGE";
    static final int CLIENT_ID_MAX = 200;
    static final int CLIENT_SECRET_MAX = 500;
    static final int ACCOUNT_SEQ_MAX = 64;

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> NOTES = List.of(
            "토스증권 웹(WTS) > 설정 > Open API에서 client_id와 client_secret을 발급받으세요.",
            "같은 화면의 허용 IP 관리에 아래 IP를 모두 등록해야 연결할 수 있어요.",
            "이 키는 주문 권한도 가지고 있어요. 업로그는 조회 API만 호출하고, 키는 암호화해 보관하며 연결을 해제하면 바로 파기해요.",
            "같은 키를 다른 도구에서 함께 쓰면 서로 토큰을 끊어서 수집이 실패할 수 있어요. 업로그 전용 키를 발급받아 주세요.");

    private final PortfolioRepository repository;
    private final PortfolioDataVersion dataVersion;
    private final UserRepository users;
    private final BrokerVerifier verifier;
    private final IngestScheduler scheduler;
    private final CredentialStore credentials;
    private final List<AccountDataPurger> purgers;
    private final TransactionTemplate tx;
    private final EgressProperties egress;
    private final ConnectionProperties properties;
    private final Clock clock;
    private final Cache<Long, Instant> recentSyncs;

    @Autowired
    public ConnectionService(
            PortfolioRepository repository, PortfolioDataVersion dataVersion, UserRepository users, BrokerVerifier verifier,
            IngestScheduler scheduler, CredentialStore credentials, List<AccountDataPurger> purgers, TransactionTemplate tx,
            EgressProperties egress, ConnectionProperties properties) {
        this(repository, dataVersion, users, verifier, scheduler, credentials, purgers, tx, egress, properties, Clock.systemUTC());
    }

    ConnectionService(
            PortfolioRepository repository, PortfolioDataVersion dataVersion, UserRepository users, BrokerVerifier verifier,
            IngestScheduler scheduler, CredentialStore credentials, List<AccountDataPurger> purgers, TransactionTemplate tx,
            EgressProperties egress, ConnectionProperties properties, Clock clock) {
        this.repository = repository;
        this.dataVersion = dataVersion;
        this.users = users;
        this.verifier = verifier;
        this.scheduler = scheduler;
        this.credentials = credentials;
        this.purgers = List.copyOf(purgers);
        this.tx = tx;
        this.egress = egress;
        this.properties = properties;
        this.clock = clock;
        this.recentSyncs = Caffeine.newBuilder().expireAfterWrite(properties.syncInterval()).maximumSize(100_000).build();
    }

    // ---- 조회

    public MeResponse me(Optional<Long> userId) {
        Optional<AuthUser> user = userId.flatMap(users::find);
        if (user.isEmpty()) {
            return new MeResponse(null, new MeConnection("NONE", null));
        }
        AuthUser u = user.get();
        MeConnection connection = repository.activeAccountOf(u.id())
                .map(a -> new MeConnection(status(scheduler.status(a.id())), a.maskedAccountNo()))
                .orElse(new MeConnection("NONE", null));
        return new MeResponse(new MeUser(u.id(), u.nickname(), u.provider()), connection);
    }

    public SetupResponse setup() {
        return new SetupResponse(egress.egressIps(), properties.keyPageUrl(), NOTES);
    }

    public ConnectionStatusResponse status(long userId) {
        Account account = activeAccount(userId);
        IngestStatus s = scheduler.status(account.id());
        return new ConnectionStatusResponse(
                status(s), account.maskedAccountNo(), account.connectedAt(),
                new Backfill(s.backfillState(), s.backfillProgress()), s.lastSyncedAt(), s.lastError(), s.recentGaps());
    }

    /** 역산 중이면 BACKFILLING, 역산 실패나 최근 수집 실패가 있으면 ERROR, 아니면 ACTIVE. */
    static String status(IngestStatus s) {
        return switch (s.backfillState()) {
            case "FAILED" -> "ERROR";
            case "DONE" -> s.lastError() == null ? "ACTIVE" : "ERROR";
            default -> "BACKFILLING";
        };
    }

    // ---- 연결

    /** 키를 검증하고 종합매매 계좌가 하나면 바로 연결한다. 여럿이면 고르게 한다(키는 저장하지 않는다). */
    public ConnectResponse connect(long userId, String clientId, String clientSecret) {
        BrokerCredentials creds = credentials(clientId, clientSecret);
        requireNotConnected(userId);
        List<BrokerAccount> accounts = verify(creds);
        if (accounts.size() == 1) {
            attach(userId, creds, accounts.getFirst());
            return new ConnectResponse("BACKFILLING", null);
        }
        return new ConnectResponse("CHOOSE_ACCOUNT", accounts.stream().map(AccountOption::of).toList());
    }

    /** 고른 계좌로 연결한다. 키는 다시 검증한다(검증한 키만 저장). */
    public ConnectResponse chooseAccount(long userId, String clientId, String clientSecret, String accountSeq) {
        BrokerCredentials creds = credentials(clientId, clientSecret);
        String seq = required(accountSeq, "accountSeq", ACCOUNT_SEQ_MAX);
        requireNotConnected(userId);
        BrokerAccount chosen = verify(creds).stream()
                .filter(a -> a.accountSeq().equals(seq))
                .findFirst()
                .orElseThrow(() -> new ConnectionException(
                        ConnectionErrorCode.INVALID_PARAMETER, "고른 계좌를 이 키로 찾을 수 없습니다. 다시 골라 주세요."));
        attach(userId, creds, chosen);
        return new ConnectResponse("BACKFILLING", null);
    }

    private List<BrokerAccount> verify(BrokerCredentials creds) {
        List<BrokerAccount> accounts;
        try {
            accounts = verifier.verify(creds);
        } catch (BrokerException e) {
            // 메시지에 키가 섞일 가능성을 막으려고 사유만 남긴다
            log.info("토스 키 검증 실패: {}", e.reason());
            throw toConnectionException(e);
        }
        List<BrokerAccount> brokerage = accounts.stream()
                .filter(a -> a.type() == null || BROKERAGE.equals(a.type().toUpperCase(Locale.ROOT)))
                .toList();
        if (brokerage.isEmpty()) {
            throw new ConnectionException(ConnectionErrorCode.TOSS_NO_BROKERAGE_ACCOUNT,
                    "토스증권 종합매매 계좌가 없습니다. 계좌를 개설한 뒤 다시 연결해 주세요.");
        }
        return brokerage;
    }

    /** 사용자당 ACTIVE 하나를 지키며 계좌를 만들거나(예전에 해제한 같은 계좌면 다시 쓴다) 키를 저장하고 역산을 예약한다. */
    private void attach(long userId, BrokerCredentials creds, BrokerAccount chosen) {
        long accountId = tx.execute(status -> {
            repository.lockUser(userId);
            requireNotConnected(userId);
            long id = repository.disconnectedAccountOf(userId, chosen.accountSeq())
                    .map(a -> {
                        repository.updateStatus(a.id(), AccountStatus.ACTIVE);
                        repository.updateMaskedAccountNo(a.id(), chosen.maskedAccountNo());
                        return a.id();
                    })
                    .orElseGet(() -> repository.insertConnectedAccount(
                            userId, chosen.accountSeq(), chosen.maskedAccountNo(), LocalDate.now(clock.withZone(KST))));
            credentials.save(id, creds);
            scheduler.scheduleBackfill(id);
            return id;
        });
        dataVersion.bump();
        log.info("사용자 {}가 토스 계좌를 연결했습니다(계좌 {})", userId, accountId);
    }

    // ---- 다시 수집·해제

    /** 직전 거래일 기준 일일 수집을 예약한다. 계좌당 {@code uplog.connection.sync-interval}에 한 번. */
    public void sync(long userId) {
        Account account = activeAccount(userId);
        Instant now = clock.instant();
        Instant previous = recentSyncs.asMap().putIfAbsent(account.id(), now);
        if (previous != null) {
            Duration wait = properties.syncInterval().minus(Duration.between(previous, now));
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
            throw new ConnectionException(ConnectionErrorCode.SYNC_TOO_SOON,
                    "다시 수집은 1시간에 한 번만 할 수 있어요. " + minutes + "분 뒤에 다시 시도해 주세요.",
                    wait.isNegative() ? Duration.ZERO : wait);
        }
        try {
            LocalDate asOf = WeekdayTradingCalendar.weekdaysOnly().previous(LocalDate.now(clock.withZone(KST)));
            scheduler.scheduleDailySync(account.id(), asOf);
        } catch (RuntimeException e) {
            recentSyncs.invalidate(account.id());
            throw e;
        }
    }

    /**
     * 연결을 해제한다. 키는 항상 즉시 파기한다. deleteData면 계좌 기록(원시·파생)과 계좌도 지우고, 아니면 DISCONNECTED로 남긴다.
     * ACTIVE 계좌가 없으면 deleteData일 때 가장 최근에 해제한 계좌의 기록을 지운다.
     */
    public void disconnect(long userId, boolean deleteData) {
        Account account = repository.activeAccountOf(userId)
                .or(() -> deleteData ? repository.latestAccountOf(userId) : Optional.empty())
                .orElseThrow(ConnectionService::notConnected);
        tx.executeWithoutResult(status -> {
            credentials.delete(account.id());
            if (deleteData) {
                purgers.forEach(p -> p.purge(account.id()));
                repository.deleteAccountData(account.id());
            } else {
                repository.updateStatus(account.id(), AccountStatus.DISCONNECTED);
            }
        });
        recentSyncs.invalidate(account.id());
        dataVersion.bump();
        log.info("사용자 {}가 계좌 {} 연결을 해제했습니다(기록 삭제: {})", userId, account.id(), deleteData);
    }

    // ---- 도움

    private Account activeAccount(long userId) {
        return repository.activeAccountOf(userId).orElseThrow(ConnectionService::notConnected);
    }

    private void requireNotConnected(long userId) {
        if (!users.exists(userId)) {
            throw new UnauthenticatedException();
        }
        if (repository.activeAccountOf(userId).isPresent()) {
            throw new ConnectionException(ConnectionErrorCode.ALREADY_CONNECTED,
                    "이미 연결된 계좌가 있습니다. 다른 계좌를 연결하려면 먼저 연결을 해제해 주세요.");
        }
    }

    private static ConnectionException notConnected() {
        return new ConnectionException(ConnectionErrorCode.ACCOUNT_NOT_CONNECTED, "연결된 계좌가 없습니다.");
    }

    private static BrokerCredentials credentials(String clientId, String clientSecret) {
        return new BrokerCredentials(
                required(clientId, "clientId", CLIENT_ID_MAX), required(clientSecret, "clientSecret", CLIENT_SECRET_MAX));
    }

    /** 앞뒤 공백을 지운 값. 오류 문구에는 값을 넣지 않는다. */
    private static String required(String value, String name, int max) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            throw new ConnectionException(ConnectionErrorCode.INVALID_PARAMETER, name + "을(를) 입력해 주세요.");
        }
        if (v.length() > max) {
            throw new ConnectionException(ConnectionErrorCode.INVALID_PARAMETER, name + "이(가) 너무 깁니다(최대 " + max + "자).");
        }
        return v;
    }

    /** 사유별 사용자 안내. BrokerException의 메시지는 쓰지 않는다. */
    static ConnectionException toConnectionException(BrokerException e) {
        return switch (e.reason()) {
            case INVALID_CREDENTIALS -> new ConnectionException(ConnectionErrorCode.TOSS_INVALID_CREDENTIALS,
                    "client_id 또는 client_secret이 올바르지 않습니다. 토스증권에서 발급받은 값을 다시 확인해 주세요.");
            case IP_NOT_ALLOWED -> new ConnectionException(ConnectionErrorCode.TOSS_IP_NOT_ALLOWED,
                    "토스증권 허용 IP에 업로그 서버 IP가 등록되지 않았습니다. 허용 IP 관리에 아래 IP를 등록한 뒤 다시 시도해 주세요.");
            case NO_BROKERAGE_ACCOUNT -> new ConnectionException(ConnectionErrorCode.TOSS_NO_BROKERAGE_ACCOUNT,
                    "토스증권 종합매매 계좌가 없습니다. 계좌를 개설한 뒤 다시 연결해 주세요.");
            case RATE_LIMITED -> new ConnectionException(ConnectionErrorCode.TOSS_RATE_LIMITED,
                    "토스증권 요청 한도를 넘었습니다. 잠시 뒤 다시 시도해 주세요.");
            case TOKEN_REVOKED -> new ConnectionException(ConnectionErrorCode.TOSS_UNAVAILABLE,
                    "다른 곳에서 같은 키로 토큰을 새로 받아 확인이 끊겼습니다(token-revoked). 잠시 뒤 다시 시도해 주세요.");
            case UNAVAILABLE -> new ConnectionException(ConnectionErrorCode.TOSS_UNAVAILABLE,
                    "토스증권에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요.");
        };
    }

    List<String> egressIps() {
        return egress.egressIps();
    }
}
