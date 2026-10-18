package com.uplog.simulation.api;

import java.time.LocalDate;

/**
 * GET /api/etfs(= /api/instruments) 항목: ETF 또는 주식.
 *
 * @param market {@code KR} | {@code US}
 * @param type {@code ETF} | {@code STOCK}
 * @param firstDate 저장된 첫 시세일. 실제 종목은 아직 시세를 받지 않았으면 null(시뮬레이션할 때 받는다)
 * @param lastDate 저장된 마지막 시세일, 없으면 null
 */
public record EtfResponse(
        String code, String name, String market, String type, String currency, LocalDate firstDate,
        LocalDate lastDate) {}
