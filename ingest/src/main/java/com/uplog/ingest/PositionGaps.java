package com.uplog.ingest;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.Execution;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 체결 내역만으로는 설명되지 않는 입고를 찾는다.
 *
 * <p>토스 체결 내역에는 Open API가 지원하지 않는 호가(시간외 종가 등), 타사 이관, 주식 선물·이벤트, 소수점 모으기 같은
 * 주문이 아닌 입고가 빠진다. 그러면 보유보다 많이 파는 매도가 생겨 리플레이가 멈춘다. 모자란 수량을 그 매도 직전의
 * "추정 입고"로 채운다. 언제 얼마에 들어왔는지 모르므로 매도가로 들여 그 몫의 손익은 0으로 둔다(계좌 가치도 같은 금액의
 * 현물 입금으로 맞춰 하루 손익이 튀지 않게 한다).
 */
final class PositionGaps {

    private PositionGaps() {}

    /** @param priceLocal 매도가(현지 통화), fxRate 그 매도의 환율 */
    record Gap(LocalDate date, String symbol, BigDecimal quantity, BigDecimal priceLocal, BigDecimal fxRate) {

        BigDecimal amountKrw() {
            return quantity.multiply(priceLocal).multiply(fxRate);
        }
    }

    /** @param trades 날짜순, 같은 날은 매수 먼저(PortfolioRepository.trades 순서) */
    static List<Gap> oversells(List<Execution> trades) {
        Map<String, BigDecimal> held = new HashMap<>();
        List<Gap> gaps = new ArrayList<>();
        for (Execution t : trades) {
            BigDecimal q = held.getOrDefault(t.instrument(), BigDecimal.ZERO);
            if (t.side() == Trade.Side.BUY) {
                held.put(t.instrument(), q.add(t.quantity()));
                continue;
            }
            BigDecimal shortfall = t.quantity().subtract(q);
            if (shortfall.signum() > 0) {
                gaps.add(new Gap(t.date(), t.instrument(), shortfall, t.priceLocal(), t.fxRate()));
                q = q.add(shortfall);
            }
            held.put(t.instrument(), q.subtract(t.quantity()));
        }
        return List.copyOf(gaps);
    }
}
