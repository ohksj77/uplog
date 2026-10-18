package com.uplog.simulation.api;

import java.time.LocalDate;

/** GET /api/etfs 항목. @param sample 로컬 개발용 합성 시세면 true(화면에 "샘플 데이터" 배지). */
public record EtfResponse(
        String code, String name, String currency, boolean sample, LocalDate firstDate, LocalDate lastDate) {}
