package com.uplog.calc;

import static com.uplog.calc.Fixtures.won;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class PurchasePlanTest {

    /** 평일 중 holidays를 뺀 날. */
    private static NavigableSet<LocalDate> tradingDays(LocalDate from, LocalDate to, LocalDate... holidays) {
        NavigableSet<LocalDate> days = new TreeSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                days.add(d);
            }
        }
        days.removeAll(List.of(holidays));
        return days;
    }

    private static List<LocalDate> dates(List<CashFlow> flows) {
        return flows.stream().map(CashFlow::date).toList();
    }

    @Test
    void 거치식은_첫_거래일에_한_번_산다() {
        // 2026-10-03(토) 시작 → 10-05(월)
        var days = tradingDays(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 31));

        assertThat(new PurchasePlan.LumpSum(won(1_000_000)).cashFlows(days))
                .containsExactly(new CashFlow(LocalDate.of(2026, 10, 5), won(1_000_000)));
    }

    @Test
    void 매일_적립은_거래일마다_한_번씩_산다() {
        // 2026-10-09(금) 한글날 휴장: 다음 날 두 번 사지 않는다
        var days = tradingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 9));

        List<CashFlow> flows = new PurchasePlan.Daily(won(10_000)).cashFlows(days);

        assertThat(flows).hasSize(9).allMatch(f -> f.amount().compareTo(won(10_000)) == 0);
    }

    @Test
    void 매주_적립일이_휴장이면_다음_거래일에_산다() {
        // 2026-10-09(금) 휴장
        var days = tradingDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 9));

        assertThat(dates(new PurchasePlan.Weekly(won(50_000), DayOfWeek.FRIDAY).cashFlows(days)))
                .containsExactly(
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 12),
                        LocalDate.of(2026, 10, 16),
                        LocalDate.of(2026, 10, 23));
    }

    @Test
    void 매월_적립일이_없는_달은_말일_기준으로_산다() {
        var days = tradingDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        // 1/31(토) → 2/2(월), 2/28(토) → 3/2(월), 3/31(화), 4/30(목)
        assertThat(dates(new PurchasePlan.Monthly(won(300_000), 31).cashFlows(days)))
                .containsExactly(
                        LocalDate.of(2026, 2, 2),
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 30));
    }

    @Test
    void 시작일_이전의_매수일은_건너뛴다() {
        var days = tradingDays(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 12, 31));

        assertThat(dates(new PurchasePlan.Monthly(won(300_000), 15).cashFlows(days)))
                .containsExactly(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 15));
    }

    @Test
    void 잘못된_입력은_거부한다() {
        assertThatThrownBy(() -> new PurchasePlan.LumpSum(won(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchasePlan.Monthly(won(1), 32)).isInstanceOf(IllegalArgumentException.class);
    }
}
