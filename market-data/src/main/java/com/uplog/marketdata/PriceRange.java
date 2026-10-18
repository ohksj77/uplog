package com.uplog.marketdata;

import java.time.LocalDate;

/** 한 종목의 첫·마지막 시세 날짜. */
public record PriceRange(LocalDate firstDate, LocalDate lastDate) {}
