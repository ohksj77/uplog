package com.uplog.credential;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * macOS 키체인의 마스터키(서비스 {@code uplog}, 계정 {@code master-key}, 값은 32바이트 base64).
 * {@code security find-generic-password -s uplog -a master-key -w}로 읽고, 없으면(종료 코드 44) 새로 만들어
 * {@code add-generic-password -U}로 넣은 뒤 다시 읽어 확인한다. 새 키는 명령 인자가 아니라 {@code security -i}의 표준 입력으로
 * 넘겨 프로세스 목록에 보이지 않게 한다. 키 값은 로그·예외 메시지에 넣지 않는다.
 *
 * <p>있는 항목이 32바이트 base64가 아니면 덮어쓰지 않고 실패한다(덮어쓰면 이미 암호화한 키를 영영 풀 수 없다).
 */
final class KeychainMasterKey {

    static final String SERVICE = "uplog";
    static final String ACCOUNT = "master-key";
    /** {@code security}: 항목이 없음(errSecItemNotFound) */
    static final int NOT_FOUND = 44;

    private static final Logger log = LoggerFactory.getLogger(KeychainMasterKey.class);
    private static final List<String> FIND = List.of("find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w");

    private final SecurityCommand security;

    KeychainMasterKey(SecurityCommand security) {
        this.security = security;
    }

    /** 키체인의 마스터키. 없으면 만들어 넣는다. 받은 쪽이 다 쓰고 지운다. */
    byte[] load() {
        SecurityCommand.Result found = security.run(FIND, null);
        if (found.exitCode() == 0) {
            return decode(found.stdout());
        }
        if (found.exitCode() != NOT_FOUND) {
            throw new IllegalStateException("키체인에서 마스터키를 읽지 못했습니다(security 종료 코드 " + found.exitCode()
                    + "). 키체인이 잠겨 있거나 접근을 거부했을 수 있어요.");
        }
        byte[] key = Aes256Gcm.randomKey();
        String encoded = Base64.getEncoder().encodeToString(key);
        Arrays.fill(key, (byte) 0);
        security.run(List.of("-i"), "add-generic-password -U -s " + SERVICE + " -a " + ACCOUNT
                + " -l uplog-master-key -D application-password -w " + encoded + "\n");
        SecurityCommand.Result stored = security.run(FIND, null);
        if (stored.exitCode() != 0) {
            throw new IllegalStateException("키체인에 마스터키를 넣지 못했습니다(security 종료 코드 " + stored.exitCode() + ").");
        }
        byte[] loaded = decode(stored.stdout());
        log.info("키체인에 새 마스터키를 만들었습니다(서비스 {}, 계정 {}).", SERVICE, ACCOUNT);
        return loaded;
    }

    private static byte[] decode(String stdout) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(stdout.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("키체인의 uplog 마스터키 형식이 다릅니다(base64 아님). 항목을 그대로 두고 확인해 주세요.");
        }
        if (key.length != Aes256Gcm.KEY_BYTES) {
            Arrays.fill(key, (byte) 0);
            throw new IllegalStateException("키체인의 uplog 마스터키 길이가 다릅니다(32바이트가 아님). 항목을 그대로 두고 확인해 주세요.");
        }
        return key;
    }
}
