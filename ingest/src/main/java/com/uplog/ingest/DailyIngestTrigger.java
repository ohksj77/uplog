package com.uplog.ingest;

import com.uplog.core.WeekdayTradingCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 매일 07:00(KST, 미국장 마감 뒤) 연결된 계좌마다 일일 수집 작업을 넣는다. 기준일은 평일 기준 직전 거래일이고,
 * 작업이 돌 때 장 운영 캘린더로 휴장일이면 그 전 영업일로 바로잡는다. 월요일 아침은 금요일분이 토요일에 이미 돌았으므로 쉰다.
 */
public class DailyIngestTrigger {

    private static final Logger log = LoggerFactory.getLogger(DailyIngestTrigger.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final IngestStore store;
    private final IngestSchedulerService scheduler;
    private final Clock clock;

    public DailyIngestTrigger(IngestStore store, IngestSchedulerService scheduler, Clock clock) {
        this.store = store;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Scheduled(cron = "${uplog.ingest.daily-cron:0 0 7 * * TUE-SAT}", zone = "Asia/Seoul")
    public void run() {
        LocalDate asOf = WeekdayTradingCalendar.weekdaysOnly().previous(LocalDate.now(clock.withZone(SEOUL)));
        var accounts = store.activeAccounts();
        accounts.forEach(id -> scheduler.scheduleDailySync(id, asOf));
        log.info("일일 수집 예약: 계좌 {}개, 기준일 {}", accounts.size(), asOf);
    }
}
