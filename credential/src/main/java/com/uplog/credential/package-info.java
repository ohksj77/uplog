/**
 * 증권사 키 봉투 암호화 보관(docs/design/account-connection.md).
 *
 * <p>비밀마다 새 256비트 데이터키로 AES-256-GCM 암호화하고, 데이터키는 {@link com.uplog.credential.MasterKeyProvider}로 감싼다.
 * 연결 해제 시 행을 지워 즉시 파기한다. 키 값은 로그·예외 메시지·toString 어디에도 남기지 않는다.
 */
package com.uplog.credential;
