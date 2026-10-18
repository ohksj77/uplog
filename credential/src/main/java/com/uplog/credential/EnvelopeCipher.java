package com.uplog.credential;

import java.util.Arrays;

/** 봉투 암호화: 비밀마다 새 256비트 데이터키로 AES-256-GCM 암호화하고, 데이터키는 마스터키로 감싼다. */
public final class EnvelopeCipher {

    /**
     * 암호화한 비밀.
     *
     * @param ciphertext 비밀의 AES-GCM 암호문(태그 포함)
     * @param iv 비밀 암호화에 쓴 IV
     * @param dataKey 마스터키로 감싼 데이터키
     */
    public record Sealed(byte[] ciphertext, byte[] iv, WrappedKey dataKey) {

        @Override
        public String toString() {
            return "Sealed[ciphertext=" + ciphertext.length + " bytes, dataKey=" + dataKey + "]";
        }
    }

    private final MasterKeyProvider masterKeys;

    public EnvelopeCipher(MasterKeyProvider masterKeys) {
        this.masterKeys = masterKeys;
    }

    /** plaintext는 호출한 쪽이 지운다. aad는 암호문을 묶을 맥락(바꾸면 복호화 실패). */
    public Sealed seal(byte[] plaintext, byte[] aad) {
        byte[] dataKey = Aes256Gcm.randomKey();
        try {
            byte[] iv = Aes256Gcm.randomIv();
            byte[] ciphertext = Aes256Gcm.encrypt(dataKey, iv, plaintext, aad);
            return new Sealed(ciphertext, iv, masterKeys.wrap(dataKey));
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    /** 돌려받은 평문은 호출한 쪽이 다 쓰고 지운다. */
    public byte[] open(Sealed sealed, byte[] aad) {
        byte[] dataKey = masterKeys.unwrap(sealed.dataKey());
        try {
            return Aes256Gcm.decrypt(dataKey, sealed.iv(), sealed.ciphertext(), aad);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }
}
