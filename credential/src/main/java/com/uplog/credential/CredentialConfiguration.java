package com.uplog.credential;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 마스터키 제공자. {@code UPLOG_MASTER_KEY}가 있으면 그 키를 쓰고, 없으면 local 프로필은 고정 개발 키(경고),
 * 그 밖의 프로필은 키를 쓰려는 순간 실패하는 제공자를 둔다(앱은 뜨지만 계좌 연결·수집은 안 된다).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CredentialProperties.class)
public class CredentialConfiguration {

    /** local 프로필 전용 고정 개발 키(32바이트). 운영 데이터에 절대 쓰지 않는다. */
    static final byte[] DEV_MASTER_KEY = "uplog-local-dev-master-key-32b!!".getBytes(StandardCharsets.US_ASCII);

    private static final Logger log = LoggerFactory.getLogger(CredentialConfiguration.class);

    @Bean
    MasterKeyProvider masterKeyProvider(CredentialProperties properties, Environment environment) {
        String configured = properties.masterKey();
        if (configured != null && !configured.isBlank()) {
            EnvMasterKeyProvider provider = EnvMasterKeyProvider.fromBase64(configured);
            log.info("마스터키: 환경변수({})", provider.keyId());
            return provider;
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            EnvMasterKeyProvider provider = new EnvMasterKeyProvider(DEV_MASTER_KEY.clone());
            log.warn("UPLOG_MASTER_KEY가 없어 local 개발용 고정 마스터키({})를 씁니다. 운영에서는 반드시 UPLOG_MASTER_KEY를 넣으세요.",
                    provider.keyId());
            return provider;
        }
        log.warn("UPLOG_MASTER_KEY가 없습니다. 증권사 키를 저장·복호화할 수 없어 계좌 연결과 수집이 실패합니다.");
        return new MasterKeyProvider() {
            @Override
            public WrappedKey wrap(byte[] dataKey) {
                Arrays.fill(dataKey, (byte) 0);
                throw new IllegalStateException("UPLOG_MASTER_KEY가 설정되지 않았습니다.");
            }

            @Override
            public byte[] unwrap(WrappedKey wrapped) {
                throw new IllegalStateException("UPLOG_MASTER_KEY가 설정되지 않았습니다.");
            }
        };
    }

    @Bean
    EnvelopeCipher envelopeCipher(MasterKeyProvider masterKeys) {
        return new EnvelopeCipher(masterKeys);
    }
}
