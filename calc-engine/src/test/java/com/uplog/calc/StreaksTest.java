package com.uplog.calc;

import static com.uplog.calc.Fixtures.MON;
import static com.uplog.calc.Fixtures.won;
import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.Streaks.Streak;
import com.uplog.calc.Streaks.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreaksTest {

    /** MON부터 평일마다 수익률을 하나씩 배정한다. */
    private static List<DailyReturn> returns(double... daily) {
        List<DailyReturn> list = new ArrayList<>();
        LocalDate d = MON;
        for (double r : daily) {
            while (d.getDayOfWeek().getValue() > 5) {
                d = d.plusDays(1);
            }
            list.add(new DailyReturn(d, r, 1.0));
            d = d.plusDays(1);
        }
        return list;
    }

    private static ReplayPoint point(LocalDate date, long cashFlow) {
        return new ReplayPoint(date, won(100), BigDecimal.ONE, won(100), won(100), won(cashFlow));
    }

    @Test
    void 가장_긴_수익_연속과_지금_이어지는_연속을_센다() {
        // 월 화 수 목 금 | 월 화 수
        var r = returns(0.0, 0.01, 0.02, 0.01, -0.01, 0.01, 0.02, 0.0);

        Streaks.Stats gains = Streaks.gains(r);

        assertThat(gains.longest()).contains(new Streak(3, MON.plusDays(1), MON.plusDays(3)));
        assertThat(gains.current()).isEmpty(); // 마지막 날이 0%라 끊김
    }

    @Test
    void 주말을_넘겨도_거래일이_이어지면_연속이다() {
        var r = returns(0.0, 0.0, 0.0, 0.0, -0.01, -0.02, -0.01);

        Streaks.Stats losses = Streaks.losses(r);

        Streak expected = new Streak(3, MON.plusDays(4), MON.plusDays(8));
        assertThat(losses.longest()).contains(expected);
        assertThat(losses.current()).contains(expected);
    }

    @Test
    void 기록이_없으면_빈_값() {
        assertThat(Streaks.gains(returns(0.0, -0.01)).longest()).isEmpty();
        assertThat(Streaks.gains(List.of()).current()).isEmpty();
    }

    @Test
    void 매주_적립은_주_단위로_센다() {
        // 3주 연속 매수 후 한 주 쉬고 다시 1주
        List<ReplayPoint> points = List.of(
                point(MON, 100), point(MON.plusDays(1), 0),
                point(MON.plusWeeks(1).plusDays(2), 100),
                point(MON.plusWeeks(2), 100),
                point(MON.plusWeeks(3), 0),
                point(MON.plusWeeks(4).plusDays(4), 100));

        Streaks.Stats s = Streaks.purchases(points, Unit.WEEK);

        assertThat(s.longest()).contains(new Streak(3, MON, MON.plusWeeks(2)));
        assertThat(s.current()).contains(new Streak(1, MON.plusWeeks(4).plusDays(4), MON.plusWeeks(4).plusDays(4)));
    }

    @Test
    void 이번_주기에_아직_매수가_없으면_직전_주기까지를_현재_연속으로_본다() {
        LocalDate oct15 = LocalDate.of(2026, 10, 15);
        List<ReplayPoint> points = List.of(
                point(oct15.minusMonths(2), 100),
                point(oct15.minusMonths(1), 100),
                point(oct15, 100),
                point(LocalDate.of(2026, 11, 2), 0)); // 11월 적립일 전

        Streaks.Stats s = Streaks.purchases(points, Unit.MONTH);

        assertThat(s.current()).contains(new Streak(3, oct15.minusMonths(2), oct15));
    }

    @Test
    void 적립이_두_주기_연속_비면_현재_연속은_없다() {
        List<ReplayPoint> points = List.of(
                point(MON, 100), point(MON.plusWeeks(1), 0), point(MON.plusWeeks(2), 0));

        assertThat(Streaks.purchases(points, Unit.WEEK).current()).isEmpty();
    }
}
