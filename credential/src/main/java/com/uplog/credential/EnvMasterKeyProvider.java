package com.uplog.credential;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 환경변수 {@code UPLOG_MASTER_KEY}(base64, 32바이트)의 마스터키. 키 id는 키의 SHA-256 앞 8바이트라서 키가 바뀌면
 * 예전 키로 감싼 데이터키를 알아본다.
 */
public final class EnvMasterKeyProvider implements MasterKeyProvider {

    private static final byte[] KEY_ID_LABEL = "uplog-master-key-id".getBytes(StandardCharsets.US_ASCII);

    private final byte[] key;
    private final String keyId;

    EnvMasterKeyProvider(byte[] key) {
        if (key.length != Aes256Gcm.KEY_BYTES) {
            throw new IllegalStateException("마스터키는 32바이트(base64로 44자)여야 합니다.");
        }
        this.key = key.clone();
        this.keyId = "env-" + fingerprint(this.key);
    }

    /** @throws IllegalStateException base64가 아니거나 32바이트가 아닌 경우(값은 메시지에 넣지 않는다) */
    public static EnvMasterKeyProvider fromBase64(String base64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("UPLOG_MASTER_KEY가 base64가 아닙니다.");
        }
        try {
            return new EnvMasterKeyProvider(decoded);
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public String keyId() {
        return keyId;
    }

    @Override
    public WrappedKey wrap(byte[] dataKey) {
        byte[] iv = Aes256Gcm.randomIv();
        byte[] ct = Aes256Gcm.encrypt(key, iv, dataKey, aad(keyId));
        return new WrappedKey(keyId, ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    }

    @Override
    public byte[] unwrap(WrappedKey wrapped) {
        if (!keyId.equals(wrapped.masterKeyId())) {
            throw new CredentialCryptoException("다른 마스터키로 감싼 데이터키입니다: " + wrapped.masterKeyId());
        }
        byte[] all = wrapped.ciphertext();
        if (all.length <= Aes256Gcm.IV_BYTES) {
            throw new CredentialCryptoException("데이터키 암호문 형식이 잘못됐습니다");
        }
        byte[] iv = Arrays.copyOfRange(all, 0, Aes256Gcm.IV_BYTES);
        byte[] ct = Arrays.copyOfRange(all, Aes256Gcm.IV_BYTES, all.length);
        return Aes256Gcm.decrypt(key, iv, ct, aad(keyId));
    }

    @Override
    public String toString() {
        return "EnvMasterKeyProvider[keyId=" + keyId + "]";
    }

    private static byte[] aad(String keyId) {
        return ("uplog/data-key/" + keyId).getBytes(StandardCharsets.US_ASCII);
    }

    private static String fingerprint(byte[] key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(KEY_ID_LABEL);
            return HexFormat.of().formatHex(sha.digest(key), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
