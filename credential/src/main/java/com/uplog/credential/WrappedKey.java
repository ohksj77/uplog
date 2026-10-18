package com.uplog.credential;

/**
 * 마스터키로 암호화한 데이터키.
 *
 * @param masterKeyId 어느 마스터키로 감쌌는지(키 교체 때 쓴다)
 * @param ciphertext 제공자별 형식(환경변수 구현은 IV 12바이트 + AES-GCM 암호문)
 */
public record WrappedKey(String masterKeyId, byte[] ciphertext) {

    @Override
    public String toString() {
        return "WrappedKey[masterKeyId=" + masterKeyId + ", ciphertext=" + ciphertext.length + " bytes]";
    }
}
