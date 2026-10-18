package com.uplog.credential;

/**
 * 데이터키를 마스터키로 감싸고 푼다. 마스터키 자체는 이 인터페이스 밖으로 나오지 않는다(KMS로 옮길 수 있게).
 * 구현: {@link EnvMasterKeyProvider}(환경변수), {@link KmsMasterKeyProvider}(자리만).
 */
public interface MasterKeyProvider {

    /** 지금 마스터키로 데이터키를 암호화한다. 호출한 쪽이 dataKey를 지운다. */
    WrappedKey wrap(byte[] dataKey);

    /**
     * 데이터키를 복호화한다. 받은 쪽이 다 쓰고 지운다.
     *
     * @throws CredentialCryptoException 마스터키가 다르거나 암호문이 변조된 경우
     */
    byte[] unwrap(WrappedKey wrapped);
}
