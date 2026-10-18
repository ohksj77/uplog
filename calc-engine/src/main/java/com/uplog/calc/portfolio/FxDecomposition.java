package com.uplog.calc.portfolio;

import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 해외 보유분의 수익 분해(이동평균). 수량 q, 평균 매수가 P0, 매수 평균 환율 F0, 현재가 P1, 현재 환율 F1일 때
 * 주가 효과 = q × (P1 − P0) × F0, 환율 효과 = q × P1 × (F1 − F0), 합 = q × (P1×F1 − P0×F0) = 평가금 − 원화 원가.
 * 원 단위로 반올림한 뒤에도 합이 맞도록 손익(= 반올림한 평가금 − 반올림한 원가)에서 주가 효과를 뺀 값을 환율 효과로 둔다.
 */
public final class FxDecomposition {

    private FxDecomposition() {}

    public record Item(
            String instrument,
            BigDecimal quantity,
            BigDecimal avgCostLocal,
            BigDecimal avgFxRate,
            BigDecimal priceLocal,
            BigDecimal fxRate,
            long priceEffect,
            long fxEffect,
            long profit) {}

    public record Total(long priceEffect, long fxEffect, long profit) {}

    public record Result(List<Item> items, Total total) {

        public Result {
            items = List.copyOf(items);
        }
    }

    /** 그날 보유 중인 달러 종목만, 손익 절댓값 큰 순. */
    public static Result of(PortfolioDay day, Map<String, CurrencyCode> currencies) {
        List<Item> items = day.instruments().values().stream()
                .filter(d -> d.position().isOpen() && currencies.get(d.instrument()) == CurrencyCode.USD)
                .map(FxDecomposition::item)
                .sorted(Comparator.comparingLong((Item i) -> Math.abs(i.profit())).reversed().thenComparing(Item::instrument))
                .toList();
        return new Result(items, new Total(
                items.stream().mapToLong(Item::priceEffect).sum(),
                items.stream().mapToLong(Item::fxEffect).sum(),
                items.stream().mapToLong(Item::profit).sum()));
    }

    public static Item item(InstrumentDay d) {
        Position p = d.position();
        long profit = PnlWaterfall.won(d.value()) - PnlWaterfall.won(p.costKrw());
        // q × (P1 − P0) × F0 = (q × P1 − 원가_현지) × F0
        long priceEffect = PnlWaterfall.won(p.quantity().multiply(d.priceLocal()).subtract(p.costLocal()).multiply(p.avgFxRate()));
        return new Item(d.instrument(), p.quantity(), p.avgCostLocal(), p.avgFxRate(), d.priceLocal(), d.fxRate(),
                priceEffect, profit - priceEffect, profit);
    }
}
