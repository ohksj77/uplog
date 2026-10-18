package com.uplog.broker.toss.fake;

import com.uplog.broker.toss.fake.FakeTossMarket.Symbol;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * 가짜 계좌 하나의 결정적 주문 이력과 그 결과(보유·현금). 입금(첫날·매달)과 가끔 출금·배당으로 현금이 움직이고,
 * 평일마다 일정 확률로 주문을 낸다: 대부분 전량 체결, 일부는 부분 체결 뒤 취소, 미체결 취소, 거부.
 * 국내는 수수료 0.015%·매도 거래세 0.18%, 미국은 수수료 0.1%(달러)이고 달러가 모자라면 원화를 환전해 산다.
 * 체결 결과가 그대로 보유 종목·매수 가능 금액이 되므로 역산 결과와 스냅샷이 맞아야 한다.
 */
final class FakeTossBook {

    /** 토스 Order 응답 한 건에 필요한 값. 금액·수수료·세금은 거래 통화. */
    record Order(
            String orderId, Symbol symbol, String side, String status, BigDecimal quantity, BigDecimal price,
            BigDecimal filledQuantity, BigDecimal averageFilledPrice, BigDecimal filledAmount, BigDecimal commission,
            BigDecimal tax, OffsetDateTime orderedAt, OffsetDateTime filledAt, OffsetDateTime canceledAt, LocalDate settlementDate) {}

    /** @param averagePrice 이동평균 매수가(수수료 제외) */
    record Position(Symbol symbol, BigDecimal quantity, BigDecimal averagePrice) {}

    private static final BigDecimal KR_COMMISSION = new BigDecimal("0.00015");
    private static final BigDecimal KR_SELL_TAX = new BigDecimal("0.0018");
    private static final BigDecimal US_COMMISSION = new BigDecimal("0.001");
    private static final BigDecimal FX_SPREAD = new BigDecimal("1.001");

    private final List<Order> orders = new ArrayList<>();
    private final Map<String, Position> positions = new TreeMap<>();
    private BigDecimal krw = BigDecimal.ZERO;
    private BigDecimal usd = BigDecimal.ZERO;

    /**
     * @param seed 계좌별 시드
     * @param start 계좌 개설일(첫 입금)
     * @param symbols 이 계좌가 사고파는 종목
     */
    FakeTossBook(FakeTossMarket market, long seed, LocalDate start, List<Symbol> symbols) {
        Random rnd = new Random(seed);
        LocalDate lastKr = market.today().minusDays(1);
        // 미국 주문은 현지 날짜 기준이라 하루 더 앞까지만 둔다(현지 장이 이미 끝난 날만).
        LocalDate lastUs = market.today().minusDays(2);
        krw = new BigDecimal("10000000");
        int seq = 0;
        int lastMonth = start.getMonthValue();
        for (LocalDate d = start.plusDays(1); !d.isAfter(lastKr); d = d.plusDays(1)) {
            if (!FakeTossMarket.weekday(d)) {
                continue;
            }
            if (d.getMonthValue() != lastMonth) {
                lastMonth = d.getMonthValue();
                krw = krw.add(new BigDecimal("1000000"));
                if (lastMonth % 7 == 3 && krw.compareTo(new BigDecimal("2000000")) > 0) {
                    krw = krw.subtract(new BigDecimal("700000"));
                }
                Position samsung = positions.get("005930");
                if (samsung != null && lastMonth % 3 == 2) {
                    krw = krw.add(samsung.quantity().multiply(new BigDecimal("361")));
                }
            }
            if (rnd.nextDouble() >= 0.3) {
                continue;
            }
            Symbol s = symbols.get(rnd.nextInt(symbols.size()));
            if (s.usd() && d.isAfter(lastUs)) {
                continue;
            }
            seq++;
            order(market, rnd, s, d, seq);
        }
    }

    private void order(FakeTossMarket market, Random rnd, Symbol s, LocalDate d, int seq) {
        int scale = s.usd() ? 2 : 0;
        BigDecimal close = market.close(s.symbol(), d);
        BigDecimal price = close.multiply(BigDecimal.valueOf(1 + (rnd.nextDouble() - 0.5) * 0.01)).setScale(scale, RoundingMode.HALF_UP);
        Position held = positions.get(s.symbol());
        boolean sell = held != null && rnd.nextDouble() < 0.4;
        BigDecimal quantity;
        if (sell) {
            long q = Math.round(held.quantity().doubleValue() * (0.3 + 0.7 * rnd.nextDouble()));
            quantity = BigDecimal.valueOf(Math.max(1, Math.min(q, held.quantity().longValue())));
        } else {
            double budgetKrw = 400_000 + rnd.nextDouble() * 1_200_000;
            double fx = s.usd() ? market.usdKrw(d).orElseThrow().doubleValue() : 1;
            quantity = BigDecimal.valueOf((long) Math.floor(budgetKrw / fx / price.doubleValue()));
            if (quantity.signum() <= 0) {
                return;
            }
        }
        double roll = rnd.nextDouble();
        String status;
        BigDecimal filled;
        if (roll < 0.84 || (roll < 0.92 && quantity.longValue() < 2)) {
            status = "FILLED";
            filled = quantity;
        } else if (roll < 0.92) {
            status = "CANCELED";
            filled = BigDecimal.valueOf(quantity.longValue() / 2);
        } else if (roll < 0.97) {
            status = "CANCELED";
            filled = BigDecimal.ZERO;
        } else {
            status = "REJECTED";
            filled = BigDecimal.ZERO;
        }
        ZonedDateTime ordered = d.atTime(LocalTime.of(9, s.usd() ? 45 : 5)).plusMinutes(rnd.nextInt(300)).atZone(s.zone());
        ZonedDateTime filledAt = ordered.plusMinutes(1 + rnd.nextInt(25));
        BigDecimal amount = filled.multiply(price);
        BigDecimal commission = s.usd()
                ? amount.multiply(US_COMMISSION).setScale(2, RoundingMode.HALF_UP)
                : amount.multiply(KR_COMMISSION).setScale(0, RoundingMode.HALF_UP);
        BigDecimal tax = sell && !s.usd() ? amount.multiply(KR_SELL_TAX).setScale(0, RoundingMode.DOWN) : BigDecimal.ZERO;

        if (filled.signum() > 0 && !sell && !pay(market, s, d, amount.add(commission))) {
            return;
        }
        if (filled.signum() > 0) {
            if (sell) {
                credit(s, amount.subtract(commission).subtract(tax));
                BigDecimal left = held.quantity().subtract(filled);
                if (left.signum() == 0) {
                    positions.remove(s.symbol());
                } else {
                    positions.put(s.symbol(), new Position(s, left, held.averagePrice()));
                }
            } else {
                BigDecimal q0 = held == null ? BigDecimal.ZERO : held.quantity();
                BigDecimal cost0 = held == null ? BigDecimal.ZERO : held.averagePrice().multiply(q0);
                BigDecimal q1 = q0.add(filled);
                positions.put(s.symbol(), new Position(s, q1, cost0.add(amount).divide(q1, MathContext.DECIMAL64)));
            }
        }
        OffsetDateTime orderedOffset = ordered.withZoneSameInstant(FakeTossMarket.SEOUL).toOffsetDateTime();
        OffsetDateTime filledOffset = filledAt.withZoneSameInstant(FakeTossMarket.SEOUL).toOffsetDateTime();
        boolean anyFill = filled.signum() > 0;
        orders.add(new Order(
                "fake-" + d.toString().replace("-", "") + "-" + seq,
                s,
                sell ? "SELL" : "BUY",
                status,
                quantity,
                price,
                filled,
                anyFill ? price : null,
                anyFill ? amount : null,
                anyFill ? commission : null,
                anyFill && !s.usd() && sell ? tax : null,
                orderedOffset,
                anyFill ? filledOffset : null,
                status.equals("CANCELED") ? filledOffset.plusMinutes(5) : null,
                anyFill ? d.plusDays(s.usd() ? 1 : 2) : null));
    }

    /** 매수 대금. 달러가 모자라면 원화를 환전한다. 원화도 모자라면 주문을 버린다. */
    private boolean pay(FakeTossMarket market, Symbol s, LocalDate d, BigDecimal cost) {
        if (!s.usd()) {
            if (krw.compareTo(cost) < 0) {
                return false;
            }
            krw = krw.subtract(cost);
            return true;
        }
        if (usd.compareTo(cost) < 0) {
            BigDecimal need = cost.subtract(usd);
            BigDecimal krwCost = need.multiply(market.usdKrw(d).orElseThrow()).multiply(FX_SPREAD).setScale(0, RoundingMode.UP);
            if (krw.compareTo(krwCost) < 0) {
                return false;
            }
            krw = krw.subtract(krwCost);
            usd = usd.add(need);
        }
        usd = usd.subtract(cost);
        return true;
    }

    private void credit(Symbol s, BigDecimal amount) {
        if (s.usd()) {
            usd = usd.add(amount);
        } else {
            krw = krw.add(amount);
        }
    }

    /** 주문 시각 내림차순(최근 먼저). */
    List<Order> ordersNewestFirst() {
        return orders.reversed();
    }

    List<Position> positions() {
        return List.copyOf(positions.values());
    }

    BigDecimal krw() {
        return krw;
    }

    BigDecimal usd() {
        return usd.setScale(2, RoundingMode.HALF_UP);
    }
}
