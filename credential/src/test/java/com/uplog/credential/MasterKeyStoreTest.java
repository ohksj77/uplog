package com.uplog.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 키체인(가짜 {@code security})과 키 파일 마스터키. 실제 키체인은 건드리지 않는다. */
class MasterKeyStoreTest {

    private static final List<String> FIND = List.of("find-generic-password", "-s", "uplog", "-a", "master-key", "-w");
    private static final byte[] SECRET = "toss-secret".getBytes(StandardCharsets.UTF_8);

    /** 키체인 하나를 흉내 낸다: -i 표준 입력의 add-generic-password로 값을 넣고, find로 읽는다. */
    static final class FakeKeychain implements SecurityCommand {
        String stored;
        int findExit = 44;
        final List<List<String>> calls = new ArrayList<>();
        final List<String> stdins = new ArrayList<>();

        @Override
        public Result run(List<String> args, String stdin) {
            calls.add(args);
            stdins.add(stdin);
            if (args.equals(FIND)) {
                if (stored != null) {
                    return new Result(0, stored + "\n");
                }
                return new Result(findExit, "");
            }
            if (args.equals(List.of("-i")) && stdin != null && stdin.startsWith("add-generic-password -U -s uplog -a master-key")) {
                String[] parts = stdin.strip().split(" ");
                stored = parts[parts.length - 1];
                return new Result(0, "");
            }
            return new Result(1, "");
        }
    }

    @Test
    void 키체인에_키가_있으면_그대로_읽는다() {
        FakeKeychain keychain = new FakeKeychain();
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 5);
        keychain.stored = Base64.getEncoder().encodeToString(key);

        assertThat(new KeychainMasterKey(keychain).load()).isEqualTo(key);
        assertThat(keychain.calls).containsExactly(FIND);
    }

    @Test
    void 키체인에_키가_없으면_만들어_표준_입력으로_넣고_다시_읽는다() {
        FakeKeychain keychain = new FakeKeychain();

        byte[] first = new KeychainMasterKey(keychain).load();
        assertThat(first).hasSize(32);
        assertThat(keychain.calls).containsExactly(FIND, List.of("-i"), FIND);
        // 키는 명령 인자에 없다(프로세스 목록에 보이지 않게)
        assertThat(keychain.calls).allSatisfy(args -> assertThat(String.join(" ", args)).doesNotContain(keychain.stored));
        assertThat(Base64.getDecoder().decode(keychain.stored)).isEqualTo(first);

        // 다음 실행은 같은 키
        assertThat(new KeychainMasterKey(keychain).load()).isEqualTo(first);
    }

    @Test
    void 키체인을_읽지_못하거나_형식이_다르면_덮어쓰지_않고_실패한다() {
        FakeKeychain locked = new FakeKeychain();
        locked.findExit = 51;
        assertThatThrownBy(() -> new KeychainMasterKey(locked).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("51");
        assertThat(locked.calls).containsExactly(FIND);

        FakeKeychain broken = new FakeKeychain();
        broken.stored = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new KeychainMasterKey(broken).load()).hasMessageContaining("32바이트");
        assertThat(broken.calls).containsExactly(FIND);
    }

    @Test
    void 키체인_제공자는_처음_쓸_때_키를_읽고_봉투_암호화가_돈다() {
        FakeKeychain keychain = new FakeKeychain();
        MasterKeyProvider provider = CredentialConfiguration.select(
                new CredentialProperties(null, CredentialProperties.KeyStore.KEYCHAIN, null), keychain, false);
        assertThat(keychain.calls).isEmpty();

        EnvelopeCipher cipher = new EnvelopeCipher(provider);
        EnvelopeCipher.Sealed sealed = cipher.seal(SECRET.clone(), new byte[] {1});
        assertThat(cipher.open(sealed, new byte[] {1})).isEqualTo(SECRET);
        assertThat(keychain.calls).hasSize(3);

        // 재시작: 같은 키체인 항목이면 풀린다
        EnvelopeCipher restarted = new EnvelopeCipher(CredentialConfiguration.select(
                new CredentialProperties(null, CredentialProperties.KeyStore.AUTO, null), keychain, true));
        assertThat(restarted.open(sealed, new byte[] {1})).isEqualTo(SECRET);
    }

    @Test
    void 키_파일은_권한_600으로_만들고_다음에도_같은_키를_쓴다(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("data/master.key");
        AtomicInteger used = new AtomicInteger();
        MasterKeyProvider provider = CredentialConfiguration.select(
                new CredentialProperties(null, CredentialProperties.KeyStore.AUTO, file.toString()),
                (args, stdin) -> {
                    used.incrementAndGet();
                    throw new AssertionError("security를 부르면 안 된다");
                },
                false);
        EnvelopeCipher.Sealed sealed = new EnvelopeCipher(provider).seal(SECRET.clone(), new byte[] {2});

        assertThat(file).exists();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
        assertThat(Base64.getDecoder().decode(Files.readString(file).strip())).hasSize(32);
        assertThat(used).hasValue(0);

        // 권한이 넓어졌으면 좁힌다
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        byte[] again = new FileMasterKey(file).load();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
        assertThat(new EnvelopeCipher(new StaticMasterKeyProvider(again)).open(sealed, new byte[] {2})).isEqualTo(SECRET);
    }

    @Test
    void 설정한_마스터키가_있으면_그것을_쓴다() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 3);
        MasterKeyProvider provider = CredentialConfiguration.select(new CredentialProperties(
                Base64.getEncoder().encodeToString(key), CredentialProperties.KeyStore.KEYCHAIN, null), (a, s) -> {
                    throw new AssertionError();
                }, true);
        assertThat(provider).isInstanceOf(StaticMasterKeyProvider.class);
        assertThat(((StaticMasterKeyProvider) provider).keyId()).isEqualTo(new StaticMasterKeyProvider(key).keyId());
    }
}
