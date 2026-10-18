package com.uplog.credential;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 마스터키 제공자. {@code uplog.credential.master-key}를 넣었으면 그 키(개발·테스트), 아니면 {@code key-store}에 따라
 * macOS 키체인(맥 앱 기본) 또는 data-dir의 키 파일. 키체인·파일은 처음 쓸 때 읽는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CredentialProperties.class)
public class CredentialConfiguration {

    static final Path SECURITY_TOOL = Path.of("/usr/bin/security");

    private static final Logger log = LoggerFactory.getLogger(CredentialConfiguration.class);

    @Bean
    MasterKeyProvider masterKeyProvider(CredentialProperties properties, ObjectProvider<SecurityCommand> security) {
        return select(properties, security.getIfAvailable(SecurityCommand::system), macOs());
    }

    static MasterKeyProvider select(CredentialProperties properties, SecurityCommand security, boolean macOs) {
        String configured = properties.masterKey();
        if (configured != null && !configured.isBlank()) {
            StaticMasterKeyProvider provider = StaticMasterKeyProvider.fromBase64(configured);
            log.info("마스터키: 설정값({})", provider.keyId());
            return provider;
        }
        CredentialProperties.KeyStore store = properties.keyStore();
        if (store == CredentialProperties.KeyStore.AUTO) {
            store = macOs ? CredentialProperties.KeyStore.KEYCHAIN : CredentialProperties.KeyStore.FILE;
        }
        if (store == CredentialProperties.KeyStore.KEYCHAIN) {
            log.info("마스터키: macOS 키체인(서비스 {}, 계정 {})", KeychainMasterKey.SERVICE, KeychainMasterKey.ACCOUNT);
            return new LazyMasterKeyProvider(new KeychainMasterKey(security)::load, "keychain");
        }
        String file = properties.keyFile();
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("uplog.credential.key-file이 없습니다(키체인을 쓰지 않을 때 필요).");
        }
        log.warn("마스터키: 키 파일({}). macOS 키체인을 쓰지 않습니다.", file);
        return new LazyMasterKeyProvider(new FileMasterKey(Path.of(file))::load, "file");
    }

    static boolean macOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") && Files.isExecutable(SECURITY_TOOL);
    }

    @Bean
    EnvelopeCipher envelopeCipher(MasterKeyProvider masterKeys) {
        return new EnvelopeCipher(masterKeys);
    }
}
