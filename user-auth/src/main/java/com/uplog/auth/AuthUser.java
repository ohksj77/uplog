package com.uplog.auth;

/**
 * 로그인한 사용자.
 *
 * @param provider 처음 가입한 외부 계정 종류(KAKAO | DEV)
 */
public record AuthUser(long id, String nickname, String provider) {}
