package com.uplog.portfolio.broker;

import com.uplog.calc.Trade;
import com.uplog.core.CurrencyCode;
import com.uplog.portfolio.connect.BrokerAccount;
import com.uplog.portfolio.connect.BrokerCredentials;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 증권사에서 계좌 원시 데이터를 읽어 오는 포트(조회만). 수집 작업(ingest)이 이걸로 체결·보유·현금·시세를 받는다.
 *
 * <p>토스 Open API 기준으로 설계했다(docs/toss-openapi-notes.md): 입출금·배당 API는 없고, 현금은 매수 가능 금액으로
 * 근사하며, 체결은 주문 단위로 합쳐서 온다. 그래서 입출금은 이 포트가 아니라 ingest가 현금 변화로 추정한다.
 * 모든 호출은 실패 시 {@link com.uplog.portfolio.connect.BrokerException}을 던진다.
 */
public interface BrokerPort {

    enum Market {
        KR,
        US
    }

    /** 키와 계좌 하나. accountSeq는 {@link BrokerAccount#accountSeq()}. */
    record AccountRef(BrokerCredentials credentials, String accountSeq) {

        public AccountRef {
            Objects.requireNonNull(credentials, "credentials");
            Objects.requireNonNull(accountSeq, "accountSeq");
        }
    }

    /** 보유 종목 하나. 가격은 거래 통화 기준. */
    record Holding(
            String symbol, String name, Market market, CurrencyCode currency, BigDecimal quantity, BigDecimal averagePrice,
            BigDecimal lastPrice) {}

    /**
     * 체결이 있는 종료 주문 하나(부분 체결 뒤 취소된 주문 포함). 토스는 주문 단위로 체결을 합쳐 준다.
     * 가격·금액·수수료·세금은 거래 통화(현지) 값.
     *
     * @param orderedAt 주문 시각(커서·조회 구간 기준)
     * @param filledAt 마지막 체결 시각
     */
    record FilledOrder(
            String orderId,
            String symbol,
            Market market,
            CurrencyCode currency,
            Trade.Side side,
            String status,
            BigDecimal filledQuantity,
            BigDecimal averageFilledPrice,
            BigDecimal filledAmount,
            BigDecimal commission,
            BigDecimal tax,
            OffsetDateTime orderedAt,
            OffsetDateTime filledAt) {}

    /** @param nextCursor 다음 페이지 커서, 마지막이면 null */
    record OrderPage(List<FilledOrder> orders, String nextCursor) {

        public OrderPage {
            orders = List.copyOf(orders);
        }
    }

    /** 일봉 하나. date는 그 시장의 현지 거래일. */
    record Candle(LocalDate date, BigDecimal close, CurrencyCode currency) {}

    /** @param candles 최신순 / nextBefore 다음 페이지 요청값, 마지막이면 null */
    record CandlePage(List<Candle> candles, OffsetDateTime nextBefore) {

        public CandlePage {
            candles = List.copyOf(candles);
        }
    }

    /** 기준일 앞뒤 영업일. todayOpen은 기준일이 영업일인지. */
    record MarketDays(LocalDate previous, LocalDate today, boolean todayOpen, LocalDate next) {}

    /** 종합매매 계좌 목록. 없으면 빈 목록. */
    List<BrokerAccount> accounts(BrokerCredentials credentials);

    /** 지금 시점의 보유 종목(국내·미국 주식). */
    List<Holding> holdings(AccountRef account);

    /** 현금(근사): 그 통화의 현금 매수 가능 금액. */
    BigDecimal cash(AccountRef account, CurrencyCode currency);

    /**
     * 체결이 있는 종료 주문. from·to는 주문일(현지 KST) 기준 포함 구간이고 null이면 전체 기간.
     *
     * @param cursor 첫 페이지는 null
     */
    OrderPage closedOrders(AccountRef account, LocalDate from, LocalDate to, String cursor);

    /** at 시점의 USD/KRW(매매기준율). 그 시점 환율이 없으면 empty. */
    Optional<BigDecimal> usdKrw(BrokerCredentials credentials, OffsetDateTime at);

    /**
     * 일봉(수정주가) 한 페이지. before 이하(포함)의 봉을 최신순으로 준다. 상장폐지 등으로 종목이 없으면 empty.
     *
     * @param before null이면 가장 최근 봉부터
     */
    Optional<CandlePage> dailyCandles(BrokerCredentials credentials, String symbol, OffsetDateTime before);

    MarketDays marketDays(BrokerCredentials credentials, Market market, LocalDate date);
}
