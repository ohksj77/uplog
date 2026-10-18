package com.uplog.credential;

/**
 * 운영용 KMS 마스터키 자리. 아직 구현하지 않았고 빈으로 등록하지 않는다.
 *
 * <p>TODO: 클라우드가 정해지면 {@code wrap}은 KMS Encrypt(또는 GenerateDataKey), {@code unwrap}은 KMS Decrypt로 바꾸고,
 * {@code masterKeyId}에 KMS 키 ARN·버전을 넣는다. 복호화 권한은 수집 워커(batch)의 역할에만 준다.
 */
public final class KmsMasterKeyProvider implements MasterKeyProvider {

    private final String keyArn;

    public KmsMasterKeyProvider(String keyArn) {
        this.keyArn = keyArn;
    }

    @Override
    public WrappedKey wrap(byte[] dataKey) {
        throw new UnsupportedOperationException("KMS 마스터키는 아직 없습니다: " + keyArn);
    }

    @Override
    public byte[] unwrap(WrappedKey wrapped) {
        throw new UnsupportedOperationException("KMS 마스터키는 아직 없습니다: " + keyArn);
    }
}
