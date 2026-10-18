package com.uplog.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlanAmountsTest {

    @Test
    void TOTAL은_총액을_회차로_나눠_원_단위로_내리고_거치식은_실제_적립_총액이다() {
        PlanAmounts amounts = PlanAmounts.of(AmountType.TOTAL, 1_000_000, 12);

        assertThat(amounts.perPurchase()).isEqualTo(83_333);
        assertThat(amounts.lumpSum()).isEqualTo(83_333 * 12).isEqualTo(999_996);
    }

    @Test
    void PER_PURCHASE는_회차당_금액이고_거치식은_금액_곱하기_회차다() {
        PlanAmounts amounts = PlanAmounts.of(AmountType.PER_PURCHASE, 300_000, 12);

        assertThat(amounts.perPurchase()).isEqualTo(300_000);
        assertThat(amounts.lumpSum()).isEqualTo(3_600_000);
    }

    @Test
    void 회차당_금액이_1원보다_작으면_잘못된_요청이다() {
        assertThatThrownBy(() -> PlanAmounts.of(AmountType.TOTAL, 1_000, 1_001))
                .isInstanceOfSatisfying(SimulationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
