-- 맥 앱(로컬 엔진)은 로그인이 없다. 맥 사용자 한 명 = app_user 한 행(첫 실행 때 만든다).
-- 외부 로그인 계정(카카오·개발용)과 다른 기기 이어 하기 코드는 서버 보관 브랜치(server-archive)로 옮겼다.
DROP TABLE IF EXISTS login_handoff;
DROP TABLE IF EXISTS user_identity;
