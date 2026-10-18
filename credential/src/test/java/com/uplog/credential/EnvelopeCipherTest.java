package com.uplog.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uplog.portfolio.connect.BrokerCredentials;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EnvelopeCipherTest {

    private static final byte[] SECRET = "toss-secret-value-1234".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD = BrokerCredentialVault.aad(7, "client-a");

    private static StaticMasterKeyProvider master(int seed) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) seed);
        return new StaticMasterKeyProvider(key);
    }

    @Test
    void 암호화한_비밀을_같은_마스터키와_맥락으로_되돌린다() {
        EnvelopeCipher cipher = new EnvelopeCipher(master(1));
        EnvelopeCipher.Sealed sealed = cipher.seal(SECRET.clone(), AAD);

        assertThat(sealed.ciphertext()).isNotEqualTo(SECRET);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain("toss-secret");
        assertThat(sealed.iv()).hasSize(12);
        assertThat(sealed.dataKey().masterKeyId()).startsWith("mk-");
        assertThat(cipher.open(sealed, AAD)).isEqualTo(SECRET);
    }

    @Test
    void 비밀마다_데이터키와_IV가_새로_만들어진다() {
        EnvelopeCipher cipher = new EnvelopeCipher(master(1));
        EnvelopeCipher.Sealed a = cipher.seal(SECRET.clone(), AAD);
        EnvelopeCipher.Sealed b = cipher.seal(SECRET.clone(), AAD);

        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
        assertThat(a.dataKey().ciphertext()).isNotEqualTo(b.dataKey().ciphertext());
    }

    @Test
    void 암호문_IV_데이터키_맥락_중_하나라도_바뀌면_복호화가_실패한다() {
        EnvelopeCipher cipher = new EnvelopeCipher(master(1));
        EnvelopeCipher.Sealed sealed = cipher.seal(SECRET.clone(), AAD);

        byte[] ct = sealed.ciphertext().clone();
        ct[0] ^= 1;
        assertThatThrownBy(() -> cipher.open(new EnvelopeCipher.Sealed(ct, sealed.iv(), sealed.dataKey()), AAD))
                .isInstanceOf(CredentialCryptoException.class);

        byte[] iv = sealed.iv().clone();
        iv[0] ^= 1;
        assertThatThrownBy(() -> cipher.open(new EnvelopeCipher.Sealed(sealed.ciphertext(), iv, sealed.dataKey()), AAD))
                .isInstanceOf(CredentialCryptoException.class);

        byte[] wrapped = sealed.dataKey().ciphertext().clone();
        wrapped[wrapped.length - 1] ^= 1;
        WrappedKey tamperedKey = new WrappedKey(sealed.dataKey().masterKeyId(), wrapped);
        assertThatThrownBy(() -> cipher.open(new EnvelopeCipher.Sealed(sealed.ciphertext(), sealed.iv(), tamperedKey), AAD))
                .isInstanceOf(CredentialCryptoException.class);

        // 다른 계좌의 행으로 옮기거나 client_id를 고친 경우
        assertThatThrownBy(() -> cipher.open(sealed, BrokerCredentialVault.aad(8, "client-a")))
                .isInstanceOf(CredentialCryptoException.class);
        assertThatThrownBy(() -> cipher.open(sealed, BrokerCredentialVault.aad(7, "client-b")))
                .isInstanceOf(CredentialCryptoException.class);
    }

    @Test
    void 다른_마스터키로는_풀_수_없다() {
        EnvelopeCipher.Sealed sealed = new EnvelopeCipher(master(1)).seal(SECRET.clone(), AAD);

        assertThatThrownBy(() -> new EnvelopeCipher(master(2)).open(sealed, AAD))
                .isInstanceOf(CredentialCryptoException.class)
                .hasMessageContaining("다른 마스터키");
        // 키 id까지 맞춰도 키가 다르면 GCM 태그 검증에서 걸린다
        WrappedKey spoofed = new WrappedKey(master(2).keyId(), sealed.dataKey().ciphertext());
        assertThatThrownBy(() -> master(2).unwrap(spoofed)).isInstanceOf(CredentialCryptoException.class);
    }

    @Test
    void 마스터키는_base64_32바이트여야_한다() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 9);
        assertThat(StaticMasterKeyProvider.fromBase64(Base64.getEncoder().encodeToString(key)).keyId())
                .isEqualTo(master(9).keyId());
        assertThatThrownBy(() -> StaticMasterKeyProvider.fromBase64(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StaticMasterKeyProvider.fromBase64("not base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not base64");
    }

    @Test
    void toString은_비밀을_드러내지_않는다() {
        EnvelopeCipher.Sealed sealed = new EnvelopeCipher(master(1)).seal(SECRET.clone(), AAD);

        assertThat(new BrokerCredentials("client-a", "toss-secret-value-1234").toString())
                .contains("client-a")
                .doesNotContain("toss-secret-value-1234");
        assertThat(sealed.toString()).doesNotContain("toss-secret");
        assertThat(master(1).toString()).doesNotContain(Base64.getEncoder().encodeToString(new byte[] {1, 1, 1}));
        assertThat(new CredentialProperties("bWFzdGVyLWtleQ==", CredentialProperties.KeyStore.AUTO, null).toString()).doesNotContain("bWFzdGVyLWtleQ==");
    }
}
