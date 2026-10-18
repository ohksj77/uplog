package com.uplog.marketdata.feed;

import com.uplog.marketdata.InstrumentType;
import java.util.Optional;

/**
 * 증권사 종목 유형 → 우리 유형. 기능 A는 ETF와 주식만 다룬다.
 *
 * <p>토스 명세(1.2.24)는 미국 ETF가 {@code ETF}인지 {@code FOREIGN_ETF}인지 적어 두지 않아서 둘 다 ETF로 본다.
 * 해외 주식({@code FOREIGN_STOCK}), 예탁증서(ADR 등 {@code DEPOSITARY_RECEIPT}), 리츠({@code REIT})는 주식으로 본다.
 * ETN·신주인수권·인프라펀드는 뺀다.
 */
public final class SecurityTypes {

    private SecurityTypes() {}

    public static Optional<InstrumentType> classify(String securityType) {
        if (securityType == null) {
            return Optional.empty();
        }
        return switch (securityType) {
            case "ETF", "FOREIGN_ETF" -> Optional.of(InstrumentType.ETF);
            case "STOCK", "FOREIGN_STOCK", "DEPOSITARY_RECEIPT", "REIT" -> Optional.of(InstrumentType.STOCK);
            default -> Optional.empty();
        };
    }
}
