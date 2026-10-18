package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** 계좌 입출금(원). amount는 항상 양수이고 방향은 type이 정한다. 배당은 계좌 안의 수익이라 여기 넣지 않는다. */
public record AccountFlow(LocalDate date, Type type, BigDecimal amount) {

    public enum Type {
        DEPOSIT,
        WITHDRAWAL
    }

    public AccountFlow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(type, "type");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("입출금 금액은 양수여야 합니다: " + amount);
        }
    }

    /** 입금 +, 출금 −. */
    public BigDecimal signedAmount() {
        return type == Type.DEPOSIT ? amount : amount.negate();
    }
}
