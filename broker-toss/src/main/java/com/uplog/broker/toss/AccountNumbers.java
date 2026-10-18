package com.uplog.broker.toss;

/** 계좌번호 가리기. 숫자 중 앞 4자리와 끝 2자리만 남기고 나머지는 *로 바꾼다(구분자는 그대로). */
public final class AccountNumbers {

    private AccountNumbers() {}

    public static String mask(String accountNo) {
        int digits = (int) accountNo.chars().filter(Character::isDigit).count();
        StringBuilder sb = new StringBuilder(accountNo.length());
        int seen = 0;
        for (char c : accountNo.toCharArray()) {
            if (Character.isDigit(c)) {
                seen++;
                sb.append(seen <= 4 || seen > digits - 2 ? c : '*');
            } else {
                sb.append(c);
            }
        }
        return digits <= 6 ? "*".repeat(Math.max(digits, 4)) : sb.toString();
    }
}
