package com.uplog.marketdata;

import java.time.LocalDate;
import java.util.List;

/** 외부 시세 소스 포트. 구현은 공공데이터포털(국내 ETF) 등. */
public interface PriceSource {

    /** 그날 상장된 ETF 전체의 종가. 휴장일이거나 아직 공개 전이면 빈 목록. */
    List<EtfQuote> etfQuotes(LocalDate date);
}
