/**
 * 로그인과 세션(docs/design/account-connection.md).
 *
 * <p>세션은 서명한 JWT(HS256)를 HttpOnly 쿠키 {@code uplog_session}에 담는다(서버 상태 없음). 카카오 로그인은
 * {@code UPLOG_KAKAO_CLIENT_ID}·{@code UPLOG_KAKAO_CLIENT_SECRET}이 있을 때만, 개발용 로그인은 {@code local} 프로필에서만 켜진다.
 * 다른 모듈은 컨트롤러 파라미터 {@link com.uplog.auth.CurrentUser}로 요청자를 받고, {@link com.uplog.auth.UserRepository#find(long)}로
 * 사용자 정보를 읽는다.
 */
package com.uplog.auth;
