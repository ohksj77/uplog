package com.uplog.marketdata.feed;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.DailyPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 한 사용자의 증권사 키에 묶인 시세 소스(조회만). 요청한 사용자 자신의 키로만 만들고 다른 사용자의 요청에 쓰지 않는다
 * ({@link MarketFeeds}). 실패는 {@link MarketFeedException}.
 */
public interface MarketFeed {

    /** 종목 목록을 받는 상장 시장. */
    enum ListingMarket {
        KOSPI(CurrencyCode.KRW),
        KOSDAQ(CurrencyCode.KRW),
        NYSE(CurrencyCode.USD),
        NASDAQ(CurrencyCode.USD),
        AMEX(CurrencyCode.USD);

        private final CurrencyCode currency;

        ListingMarket(CurrencyCode currency) {
            this.currency = currency;
        }

        public CurrencyCode currency() {
            return currency;
        }
    }

    /** @param securityType 증권사 원문 유형. {@link SecurityTypes}로 분류한다. */
    record Listing(String code, String name, String securityType) {

        public Listing {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }
    }

    /** @param status ACTIVE | SCHEDULED | DELISTED */
    record Detail(String code, String name, CurrencyCode currency, String securityType, String status) {}

    /** @param candles 최신순 / nextBefore 다음 페이지 요청값, 마지막이면 null */
    record CandlePage(List<DailyPrice> candles, OffsetDateTime nextBefore) {

        public CandlePage {
            candles = List.copyOf(candles);
        }
    }

    List<Listing> listings(ListingMarket market);

    /** 모르는 코드는 결과에서 빠진다. */
    List<Detail> details(List<String> codes);

    /**
     * 수정주가 일봉 한 페이지. before 이하(포함)를 최신순으로. 종목이 없으면(상장폐지 등) empty.
     *
     * @param before null이면 가장 최근 봉부터
     */
    Optional<CandlePage> dailyCandles(String code, OffsetDateTime before);

    /** 그날(서울 외환시장 마감 기준) USD/KRW. 없으면 empty. */
    Optional<BigDecimal> usdKrw(LocalDate date);
}
