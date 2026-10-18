package com.uplog.portfolio.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 맥 위젯("업로그 로그")이 읽는 스냅샷({@code {data-dir}/widget/widget.json}). 형식은 docs/design/desktop-app.md "위젯".
 * 키·토큰·계좌번호는 넣지 않는다.
 *
 * @param asOf 마지막으로 기록한 거래일(MISSING 제외). 기록이 없으면 null
 * @param today asOf 하루의 손익. 기록이 없으면 null
 * @param weeks 최근 {@link WidgetSnapshots#WEEKS}주, 오래된 순. 마지막 주는 오늘이 든 주
 * @param thresholds 진하기 경계(원). 지난 1년 기록한 날 중 손익이 0이 아닌 날의 |손익| 사분위수
 */
public record WidgetSnapshot(
        int version,
        Instant generatedAt,
        LocalDate asOf,
        String accountName,
        boolean sample,
        Today today,
        Tally month,
        Tally year,
        Streak streak,
        List<Week> weeks,
        List<Long> thresholds) {

    public static final int VERSION = 1;

    public WidgetSnapshot {
        weeks = List.copyOf(weeks);
        thresholds = List.copyOf(thresholds);
    }

    /** @param ret 그날 수익률(소수, 0.0123 = 1.23%) */
    public record Today(long pnl, @JsonProperty("return") double ret) {}

    /** asOf가 든 달(month)·해(year)의 손익 합과 번 날·잃은 날. MISSING인 날은 세지 않는다. */
    public record Tally(long pnl, int gainDays, int lossDays) {}

    /** 마지막 기록일까지 이어진 연속. 마지막 날 손익이 0이거나 기록이 없으면 kind null, length 0. */
    public record Streak(String kind, int length) {}

    /** @param start 월요일, days 월–금 5칸 */
    public record Week(LocalDate start, List<Day> days) {

        public Week {
            days = List.copyOf(days);
        }
    }

    /** @param level −4…4, 0은 손익 0. 기록 없음·휴장·미래는 null(pnl도 null) */
    public record Day(LocalDate date, Integer level, Long pnl) {}
}
