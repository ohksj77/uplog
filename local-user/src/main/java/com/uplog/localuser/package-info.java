/**
 * 맥 앱의 로컬 엔진 보안과 로컬 사용자(docs/design/desktop-app.md "실행 계약").
 *
 * <p>로그인이 없다. Electron이 실행마다 만든 토큰을 {@code GET /__launch?token=}으로 내면 HttpOnly·SameSite=Strict 쿠키
 * {@code uplog_desktop}을 주고, 그 뒤 {@code /api/**}는 이 쿠키가 있어야 한다(없으면 403 {@code DESKTOP_LOCKED}).
 * 모든 요청은 {@code Host}가 {@code 127.0.0.1:{port}}·{@code localhost:{port}}여야 하고(DNS 리바인딩 차단), 상태를 바꾸는
 * 요청은 같은 출처여야 한다. 쿠키가 맞는 요청은 모두 첫 실행 때 만든 로컬 사용자 한 명이다. 다른 모듈은 컨트롤러 파라미터
 * {@link com.uplog.localuser.CurrentUser}로 받는다.
 */
package com.uplog.localuser;
