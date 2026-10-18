package com.uplog.credential;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM(IV 12바이트, 태그 128비트). 같은 키에 IV를 다시 쓰지 않도록 IV는 매번 무작위로 만든다. */
final class Aes256Gcm {

    static final int KEY_BYTES = 32;
    static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Aes256Gcm() {}

    static byte[] randomKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    static byte[] randomIv() {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        return iv;
    }

    static byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext, byte[] aad) {
        return run(Cipher.ENCRYPT_MODE, key, iv, plaintext, aad);
    }

    /** @throws CredentialCryptoException 키가 다르거나 암호문·IV·aad가 바뀐 경우 */
    static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] aad) {
        return run(Cipher.DECRYPT_MODE, key, iv, ciphertext, aad);
    }

    private static byte[] run(int mode, byte[] key, byte[] iv, byte[] input, byte[] aad) {
        if (key.length != KEY_BYTES) {
            throw new CredentialCryptoException("키 길이가 256비트가 아닙니다");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(input);
        } catch (AEADBadTagException e) {
            throw new CredentialCryptoException("복호화 실패: 키가 다르거나 암호문이 변조됐습니다", e);
        } catch (GeneralSecurityException e) {
            throw new CredentialCryptoException("AES-GCM 처리 실패", e);
        }
    }
}
