package com.uplog.portfolio.publicip;

/** 이 기기의 공인 IP를 밖에서 본 대로 알아낸다. */
@FunctionalInterface
public interface PublicIpLookup {

    /** @throws PublicIpUnavailableException 확인하지 못했을 때 */
    String lookup();
}
