package com.uplog.portfolio.connection;

import com.uplog.portfolio.connect.BrokerCredentials;

/** 연결 API가 키를 맡기고 파기하는 통로. credential 모듈이 암호화해 보관한다(읽기는 {@code CredentialReader}). */
public interface CredentialStore {

    /** 검증을 통과한 키를 암호화해 저장한다. 이미 있으면 바꾼다. */
    void save(long accountId, BrokerCredentials credentials);

    /** 키를 즉시 파기한다. 없으면 아무것도 하지 않는다. */
    void delete(long accountId);
}
