package com.uplog.calc.portfolio;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** {@link PortfolioReplay}의 결과: 거래일별 계좌 상태와 매도 기록(날짜순). */
public record PortfolioHistory(List<PortfolioDay> days, List<RealizedSale> sales) {

    public PortfolioHistory {
        if (days.isEmpty()) {
            throw new IllegalArgumentException("계좌 기록이 비어 있습니다");
        }
        days = List.copyOf(days);
        sales = List.copyOf(sales);
    }

    public PortfolioDay first() {
        return days.getFirst();
    }

    public PortfolioDay last() {
        return days.getLast();
    }

    /** date와 같은 거래일의 인덱스. 없으면 empty. */
    public Optional<Integer> indexOf(LocalDate date) {
        int i = Collections.binarySearch(days.stream().map(PortfolioDay::date).toList(), date);
        return i >= 0 ? Optional.of(i) : Optional.empty();
    }

    /** date 이하인 마지막 거래일의 인덱스. date가 첫날보다 앞이면 empty. */
    public Optional<Integer> indexOnOrBefore(LocalDate date) {
        int found = -1;
        for (int i = 0; i < days.size() && !days.get(i).date().isAfter(date); i++) {
            found = i;
        }
        return found < 0 ? Optional.empty() : Optional.of(found);
    }
}
