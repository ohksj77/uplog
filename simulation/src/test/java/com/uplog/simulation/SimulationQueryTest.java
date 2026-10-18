package com.uplog.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

class SimulationQueryTest {

    @Test
    void 같은_뜻의_요청은_같은_값으로_정규화한다() {
        SimulationQuery a = SimulationQuery.parse(" sample01, SAMPLE02 ,sample01", "1y", "monthly", "MONDAY", "15", "per_purchase", "300000");
        SimulationQuery b = SimulationQuery.parse("SAMPLE01,SAMPLE02", "1Y", "MONTHLY", null, "15", "PER_PURCHASE", "300000");

        assertThat(a).isEqualTo(b);
        assertThat(a.etfs()).containsExactly("SAMPLE01", "SAMPLE02");
        assertThat(a.period()).isEqualTo(SimulationPeriod.ONE_YEAR);
        assertThat(a.schedule()).isEqualTo(RecurringSchedule.monthly(15));
        assertThat(a.amountType()).isEqualTo(AmountType.PER_PURCHASE);
        assertThat(a.amount()).isEqualTo(300_000);
    }

    @Test
    void WEEKLY는_평일_요일이_필요하다() {
        assertThat(SimulationQuery.parse("A", "6M", "WEEKLY", "friday", null, "TOTAL", "1000").schedule())
                .isEqualTo(RecurringSchedule.weekly(DayOfWeek.FRIDAY));
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "WEEKLY", null, null, "TOTAL", "1000"), "dayOfWeek");
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "WEEKLY", "SATURDAY", null, "TOTAL", "1000"), "dayOfWeek");
    }

    @Test
    void MONTHLY는_1일에서_31일이_필요하다() {
        assertThat(SimulationQuery.parse("A", "6M", "MONTHLY", null, "31", "TOTAL", "1000").schedule())
                .isEqualTo(RecurringSchedule.monthly(31));
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "MONTHLY", null, null, "TOTAL", "1000"), "dayOfMonth");
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "MONTHLY", null, "0", "TOTAL", "1000"), "dayOfMonth");
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "MONTHLY", null, "32", "TOTAL", "1000"), "dayOfMonth");
    }

    @Test
    void 종목은_1개에서_3개다() {
        assertInvalid(() -> SimulationQuery.parse(null, "6M", "DAILY", null, null, "TOTAL", "1000"), "etfs");
        assertInvalid(() -> SimulationQuery.parse(" ", "6M", "DAILY", null, null, "TOTAL", "1000"), "etfs");
        assertInvalid(() -> SimulationQuery.parse("A,B,C,D", "6M", "DAILY", null, null, "TOTAL", "1000"), "최대 3개");
        assertInvalid(() -> SimulationQuery.parse("A,,B", "6M", "DAILY", null, null, "TOTAL", "1000"), "종목코드");
        assertInvalid(() -> SimulationQuery.parse("A;DROP", "6M", "DAILY", null, null, "TOTAL", "1000"), "종목코드");
        assertInvalid(() -> SimulationQuery.parse("A/B", "6M", "DAILY", null, null, "TOTAL", "1000"), "종목코드");
        assertInvalid(() -> SimulationQuery.parse("ABCDEFGHIJKLM", "6M", "DAILY", null, null, "TOTAL", "1000"), "종목코드");
    }

    @Test
    void 종목코드는_토스_심볼_문자를_받고_대문자로_맞춘다() {
        assertThat(SimulationQuery.parse("069500, brk.b ,BF-B", "6M", "DAILY", null, null, "TOTAL", "1000").etfs())
                .containsExactly("069500", "BRK.B", "BF-B");
    }

    @Test
    void 금액은_1000원_이상_10억_이하_정수다() {
        assertThat(SimulationQuery.parse("A", "6M", "DAILY", null, null, "TOTAL", "1000000000").amount())
                .isEqualTo(1_000_000_000);
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "DAILY", null, null, "TOTAL", "999"), "amount");
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "DAILY", null, null, "TOTAL", "1000000001"), "amount");
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "DAILY", null, null, "TOTAL", "1000.5"), "amount");
        assertInvalid(() -> SimulationQuery.parse("A", "6M", "DAILY", null, null, "TOTAL", null), "amount");
    }

    @Test
    void 기간_주기_금액방식은_정해진_값만_받는다() {
        assertInvalid(() -> SimulationQuery.parse("A", "2Y", "DAILY", null, null, "TOTAL", "1000"), "period");
        assertInvalid(() -> SimulationQuery.parse("A", "1Y", "YEARLY", null, null, "TOTAL", "1000"), "frequency");
        assertInvalid(() -> SimulationQuery.parse("A", "1Y", "DAILY", null, null, "ALL", "1000"), "amountType");
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String messagePart) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(SimulationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_PARAMETER))
                .hasMessageContaining(messagePart);
    }
}
