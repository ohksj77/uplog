package com.uplog.portfolio.widget;

import com.uplog.localuser.CurrentUser;
import com.uplog.localuser.LocalUsers;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.portfolio.PortfolioAccounts;
import com.uplog.portfolio.PortfolioDataVersion;
import com.uplog.portfolio.PortfolioException;
import com.uplog.portfolio.PortfolioRepository.Account;
import com.uplog.portfolio.PortfolioService;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 맥 위젯이 읽는 스냅샷을 {@code {data-dir}/widget/widget.json}에 쓴다(임시 파일에 쓰고 rename — 위젯이 반쯤 쓴 파일을 읽지 않는다).
 * 계좌는 로컬 사용자의 ACTIVE 계좌, 없으면 데모 계좌(sample). 쓰는 때:
 *
 * <ul>
 *   <li>엔진이 뜬 직후
 *   <li>계좌·시세 데이터 번호({@link PortfolioDataVersion}·{@link PriceDataVersion})나 날짜가 바뀐 뒤 — 수집·역산 뒤
 *       {@link PortfolioService#refresh}, 연결·해제, 시세 수집이 모두 번호를 올린다. 몇 초마다 번호만 비교한다
 *   <li>30분마다(번호가 그대로여도)
 * </ul>
 *
 * 계좌가 아직 만드는 중이거나 없으면 이전 파일을 그대로 둔다. {@code uplog.widget.enabled=false}면 쓰지 않는다.
 */
@Component
public class WidgetSnapshotWriter {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Logger log = LoggerFactory.getLogger(WidgetSnapshotWriter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Signature(long accountVersion, long priceVersion, LocalDate today) {}

    private final PortfolioService service;
    private final PortfolioAccounts accounts;
    private final LocalUsers users;
    private final PortfolioDataVersion accountVersion;
    private final PriceDataVersion priceVersion;
    private final Path file;
    private final boolean enabled;
    private final Clock clock;
    private volatile Signature written;

    @Autowired
    public WidgetSnapshotWriter(
            PortfolioService service,
            PortfolioAccounts accounts,
            LocalUsers users,
            PortfolioDataVersion accountVersion,
            PriceDataVersion priceVersion,
            @Value("${uplog.desktop.data-dir:}") String dataDir,
            @Value("${uplog.widget.enabled:true}") boolean enabled) {
        this(service, accounts, users, accountVersion, priceVersion, dataDir, enabled, Clock.systemUTC());
    }

    WidgetSnapshotWriter(
            PortfolioService service,
            PortfolioAccounts accounts,
            LocalUsers users,
            PortfolioDataVersion accountVersion,
            PriceDataVersion priceVersion,
            String dataDir,
            boolean enabled,
            Clock clock) {
        this.service = service;
        this.accounts = accounts;
        this.users = users;
        this.accountVersion = accountVersion;
        this.priceVersion = priceVersion;
        this.file = dataDir == null || dataDir.isBlank() ? null : Path.of(dataDir, "widget", "widget.json");
        this.enabled = enabled && file != null;
        this.clock = clock;
    }

    /** 위젯 파일 위치. data-dir이 없으면 empty. */
    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        write();
    }

    /** 데이터 번호·날짜가 바뀌었을 때만 쓴다. */
    @Scheduled(initialDelayString = "${uplog.widget.check-interval:5s}", fixedDelayString = "${uplog.widget.check-interval:5s}")
    public void writeIfChanged() {
        if (!Objects.equals(written, signature())) {
            write();
        }
    }

    @Scheduled(initialDelayString = "${uplog.widget.refresh-interval:30m}", fixedDelayString = "${uplog.widget.refresh-interval:30m}")
    public void writePeriodically() {
        write();
    }

    /** 지금 스냅샷을 쓴다. 쓰지 않았으면(꺼짐·계좌 없음·만드는 중·IO 실패) false. */
    public synchronized boolean write() {
        if (!enabled) {
            return false;
        }
        Signature signature = signature();
        written = signature; // 실패해도 다음 번호 변경이나 30분 뒤에 다시 쓴다(몇 초마다 같은 실패를 되풀이하지 않는다).
        try {
            Optional<WidgetSnapshot> snapshot = build(signature.today());
            if (snapshot.isEmpty()) {
                return false;
            }
            writeAtomically(file, JSON.writeValueAsBytes(snapshot.get()));
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("위젯 스냅샷을 쓰지 못했습니다: {}", e.toString());
            return false;
        }
    }

    Optional<WidgetSnapshot> build(LocalDate today) {
        Account account;
        try {
            account = accounts.resolve(Optional.of(new CurrentUser(users.currentId())));
            // 지난 1년(진하기 경계) + 26주(격자)를 덮도록 넉넉히.
            LocalDate from = today.minusYears(1).minusWeeks(WidgetSnapshots.WEEKS + 1);
            return Optional.of(WidgetSnapshots.build(account.name(), account.sample(), service.logSince(account, from), today,
                    clock.instant()));
        } catch (PortfolioException e) {
            log.debug("위젯 스냅샷 건너뜀: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Signature signature() {
        return new Signature(accountVersion.current(), priceVersion.current(), LocalDate.now(clock.withZone(KST)));
    }

    /** 같은 폴더의 임시 파일에 쓰고 이름을 바꾼다. 읽는 쪽은 이전 파일이나 새 파일 하나만 본다. */
    static void writeAtomically(Path target, byte[] content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
