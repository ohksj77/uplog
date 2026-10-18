package com.uplog.portfolio;

/** 로그 한 칸의 출처. */
public enum DaySource {
    /** 연결 뒤 수집한 날 */
    COLLECTED,
    /** 연결 전 기간을 체결 내역으로 역산한 날 */
    BACKFILLED,
    /** 수집 실패. 값은 비우고 빈 칸으로 그린다. */
    MISSING
}
