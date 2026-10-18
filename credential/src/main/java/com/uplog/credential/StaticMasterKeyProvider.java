package com.uplog.credential;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 메모리에 든 마스터키(32바이트)로 데이터키를 감싼다. 키는 키체인·키 파일·설정({@code uplog.credential.master-key})에서 온다.
 * 키 id는 키의 SHA-256 앞 8바이트라서, 어디에 보관했든 같은 키면 같은 id이고 키가 바뀌면 예전 키로 감싼 데이터키를 알아본다.
 */
public final class StaticMasterKeyProvider implements MasterKeyProvider {

    private static final byte[] KEY_ID_LABEL = "uplog-master-key-id".getBytes(StandardCharsets.US_ASCII);

    private final byte[] key;
    private final String keyId;

    StaticMasterKeyProvider(byte[] key) {
        if (key.length != Aes256Gcm.KEY_BYTES) {
            throw new IllegalStateException("마스터키는 32바이트(base64로 44자)여야 합니다.");
        }
        this.key = key.clone();
        this.keyId = "mk-" + fingerprint(this.key);
    }

    /** @throws IllegalStateException base64가 아니거나 32바이트가 아닌 경우(값은 메시지에 넣지 않는다) */
    public static StaticMasterKeyProvider fromBase64(String base64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("마스터키가 base64가 아닙니다.");
        }
        try {
            return new StaticMasterKeyProvider(decoded);
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
        return "StaticMasterKeyProvider[keyId=" + keyId + "]";
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
