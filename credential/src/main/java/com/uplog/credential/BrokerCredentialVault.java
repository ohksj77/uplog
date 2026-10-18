package com.uplog.credential;

import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.CredentialReader;
import com.uplog.portfolio.connection.CredentialStore;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * broker_credential 보관소. 연결 API는 {@link CredentialStore}로 저장·파기하고, 수집 작업은 {@link CredentialReader}로만 읽는다.
 * 로그에는 계좌 id와 마스터키 id만 남긴다.
 */
@Component
public class BrokerCredentialVault implements CredentialStore, CredentialReader {

    private static final Logger log = LoggerFactory.getLogger(BrokerCredentialVault.class);

    private final JdbcClient jdbc;
    private final EnvelopeCipher cipher;

    public BrokerCredentialVault(JdbcClient jdbc, EnvelopeCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public void save(long accountId, BrokerCredentials credentials) {
        byte[] secret = utf8(credentials.clientSecret());
        EnvelopeCipher.Sealed sealed;
        try {
            sealed = cipher.seal(secret, aad(accountId, credentials.clientId()));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        jdbc.sql("DELETE FROM broker_credential WHERE account_id = :accountId").param("accountId", accountId).update();
        jdbc.sql("""
                        INSERT INTO broker_credential
                            (account_id, client_id, secret_ciphertext, secret_iv, data_key_ciphertext, master_key_id)
                        VALUES (:accountId, :clientId, :secret, :iv, :dataKey, :masterKeyId)
                        """)
                .param("accountId", accountId)
                .param("clientId", credentials.clientId())
                .param("secret", sealed.ciphertext())
                .param("iv", sealed.iv())
                .param("dataKey", sealed.dataKey().ciphertext())
                .param("masterKeyId", sealed.dataKey().masterKeyId())
                .update();
        log.info("계좌 {}의 증권사 키를 암호화해 저장했습니다(마스터키 {})", accountId, sealed.dataKey().masterKeyId());
    }

    @Override
    @Transactional
    public void delete(long accountId) {
        int rows = jdbc.sql("DELETE FROM broker_credential WHERE account_id = :accountId").param("accountId", accountId).update();
        if (rows > 0) {
            log.info("계좌 {}의 증권사 키를 파기했습니다", accountId);
        }
    }

    @Override
    public BrokerCredentials read(long accountId) {
        Row row = find(accountId).orElseThrow(() -> new CredentialNotFoundException(accountId));
        byte[] plain = cipher.open(
                new EnvelopeCipher.Sealed(row.secret(), row.iv(), new WrappedKey(row.masterKeyId(), row.dataKey())),
                aad(accountId, row.clientId()));
        try {
            return new BrokerCredentials(row.clientId(), new String(plain, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public boolean exists(long accountId) {
        return jdbc.sql("SELECT COUNT(*) FROM broker_credential WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query(Long.class)
                .single() > 0;
    }

    private record Row(String clientId, byte[] secret, byte[] iv, byte[] dataKey, String masterKeyId) {}

    private Optional<Row> find(long accountId) {
        return jdbc.sql("""
                        SELECT client_id, secret_ciphertext, secret_iv, data_key_ciphertext, master_key_id
                        FROM broker_credential WHERE account_id = :accountId
                        """)
                .param("accountId", accountId)
                .query((rs, n) -> new Row(rs.getString("client_id"), rs.getBytes("secret_ciphertext"), rs.getBytes("secret_iv"),
                        rs.getBytes("data_key_ciphertext"), rs.getString("master_key_id")))
                .optional();
    }

    /** 암호문을 이 계좌·client_id에 묶는다. */
    static byte[] aad(long accountId, String clientId) {
        return ("uplog/broker_credential/v1/" + accountId + "/" + clientId).getBytes(StandardCharsets.UTF_8);
    }

    /** String.getBytes와 달리 중간 버퍼도 지운다. */
    private static byte[] utf8(String s) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(s));
        byte[] bytes = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit());
        Arrays.fill(buffer.array(), (byte) 0);
        return bytes;
    }
}
