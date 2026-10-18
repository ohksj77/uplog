package com.uplog.credential;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 키체인이 없는 환경(macOS가 아닌 개발·테스트)의 대체: data-dir의 키 파일(32바이트 base64, 권한 600).
 * 없으면 만든다. 권한이 더 넓으면 600으로 좁힌다. 맥 앱에서는 쓰지 않는다(키체인을 쓴다).
 */
final class FileMasterKey {

    private static final Logger log = LoggerFactory.getLogger(FileMasterKey.class);
    static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    private final Path file;

    FileMasterKey(Path file) {
        this.file = file;
    }

    byte[] load() {
        log.warn("키체인을 쓰지 않고 마스터키를 파일({})에 둡니다(권한 600). 개발·테스트용입니다.", file);
        try {
            if (Files.notExists(file)) {
                create();
            }
            restrict();
            byte[] key = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).strip());
            if (key.length != Aes256Gcm.KEY_BYTES) {
                Arrays.fill(key, (byte) 0);
                throw new IllegalStateException("마스터키 파일 길이가 다릅니다(32바이트가 아님): " + file);
            }
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("마스터키 파일을 읽거나 만들지 못했습니다: " + file, e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("마스터키 파일이 base64가 아닙니다: " + file);
        }
    }

    private void create() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] key = Aes256Gcm.randomKey();
        byte[] encoded = Base64.getEncoder().encode(key);
        Arrays.fill(key, (byte) 0);
        try {
            if (posix()) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            } else {
                Files.createFile(file);
            }
            Files.write(file, encoded);
        } catch (FileAlreadyExistsException e) {
            // 동시에 다른 쪽이 만들었다. 그 키를 쓴다.
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private void restrict() throws IOException {
        if (posix() && !Files.getPosixFilePermissions(file).equals(OWNER_ONLY)) {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        }
    }

    private boolean posix() {
        Path dir = file.toAbsolutePath().getParent();
        return Files.getFileAttributeView(dir == null ? file : dir, PosixFileAttributeView.class) != null;
    }
}
