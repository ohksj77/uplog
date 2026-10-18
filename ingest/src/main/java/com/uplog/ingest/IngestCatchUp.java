package com.uplog.ingest;

import com.uplog.core.TradingCalendar;
import com.uplog.core.WeekdayTradingCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 놓친 날 따라잡기. 맥 앱은 07:00에 꺼져 있거나 잠자고 있을 수 있어서, 엔진이 뜰 때와 그 뒤 30분마다 연결된 계좌의
 * 마지막 성공한 일일 수집 기준일을 보고, 가장 최근 거래일(07:00 전이면 그 전날 기준)이 아직 수집되지 않았으면 그 날짜로
 * 일일 수집을 넣는다.
 *
 * <p>놓친 날이 여러 날이어도 작업은 가장 최근 날 하나다. 토스 API는 보유·현금의 지금 값만 주므로 지난 날짜로 작업을 돌리면
 * 지금 현금을 지난 날에 기록해 입출금 추정이 틀어진다. 대신 이 작업이 커서 이후의 체결과 그날까지의 일봉·환율을 모두 받아,
 * 놓친 날의 계좌 값은 리플레이로 채워진다(그 사이 입출금은 가장 최근 날 하나로 합쳐 추정된다).
 * 역산이 끝나지 않은 계좌는 건너뛴다(역산이 끝나면 첫 일일 수집을 스스로 넣는다).
 */
public class IngestCatchUp {

    static final LocalTime DAILY_TIME = LocalTime.of(7, 0);

    private static final Logger log = LoggerFactory.getLogger(IngestCatchUp.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final IngestStore store;
    private final IngestJobRepository jobs;
    private final IngestSchedulerService scheduler;
    private final Clock clock;
    private final TradingCalendar calendar = WeekdayTradingCalendar.weekdaysOnly();

    public IngestCatchUp(IngestStore store, IngestJobRepository jobs, IngestSchedulerService scheduler, Clock clock) {
        this.store = store;
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        run();
    }

    @Scheduled(fixedDelayString = "${uplog.ingest.catch-up-interval:30m}", initialDelayString = "${uplog.ingest.catch-up-interval:30m}")
    public void periodic() {
        run();
    }

    /** @return 넣은 작업 수 */
    public int run() {
        LocalDate latest = latestDue();
        int enqueued = 0;
        for (long accountId : store.activeAccounts()) {
            boolean backfilled = jobs.latest(accountId, IngestJob.Kind.BACKFILL)
                    .map(j -> j.state() == IngestJob.State.DONE)
                    .orElse(false);
            if (!backfilled) {
                continue;
            }
            Optional<LocalDate> lastDone = jobs.lastDailyDoneAsOf(accountId);
            if (lastDone.isPresent() && !lastDone.get().isBefore(latest)) {
                continue;
            }
            scheduler.scheduleDailySync(accountId, latest);
            enqueued++;
            log.info("놓친 수집 따라잡기: 계좌 {}, 마지막 성공 {}, 기준일 {}", accountId, lastDone.orElse(null), latest);
        }
        return enqueued;
    }

    /** 지금 수집돼 있어야 할 가장 최근 거래일: 07:00(KST) 뒤면 오늘의 직전 평일, 전이면 어제의 직전 평일. */
    LocalDate latestDue() {
        ZonedDateTime now = clock.instant().atZone(SEOUL);
        LocalDate reference = now.toLocalTime().isBefore(DAILY_TIME) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        return calendar.previous(reference);
    }
}
