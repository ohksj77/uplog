package com.uplog.portfolio.connect;

import java.util.List;

/** 키 검증과 계좌 목록. 토큰 발급과 계좌 목록 조회가 모두 성공해야 검증된 것으로 본다. */
public interface BrokerVerifier {

    /** @throws BrokerException 실패 사유와 함께 */
    List<BrokerAccount> verify(BrokerCredentials credentials);
}
