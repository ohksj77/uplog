package com.uplog.core;

public enum CurrencyCode {
    KRW(0),
    USD(2);

    private final int scale;

    CurrencyCode(int scale) {
        this.scale = scale;
    }

    /** 표시·정산 기준 소수 자릿수. 계산 중간값에는 적용하지 않는다. */
    public int scale() {
        return scale;
    }
}
