package com.uplog.credential;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** macOS {@code /usr/bin/security} 실행. 테스트는 가짜로 바꿔 끼워 실제 키체인을 건드리지 않는다. */
@FunctionalInterface
public interface SecurityCommand {

    /**
     * @param args {@code security} 뒤에 붙는 인자
     * @param stdin 표준 입력으로 넘길 내용(없으면 null). 비밀은 인자 대신 여기로 넘겨 프로세스 목록에 남지 않게 한다
     */
    Result run(List<String> args, String stdin);

    /** stdout에는 비밀이 들어 있을 수 있으니 로그에 남기지 않는다. */
    record Result(int exitCode, String stdout) {
        @Override
        public String toString() {
            return "Result[exitCode=" + exitCode + ", stdout=***]";
        }
    }

    /** 실제 {@code /usr/bin/security}. 10초 안에 끝나지 않으면 실패로 본다. */
    static SecurityCommand system() {
        return (args, stdin) -> {
            List<String> command = new ArrayList<>();
            command.add("/usr/bin/security");
            command.addAll(args);
            Process process;
            try {
                process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException e) {
                throw new IllegalStateException("security를 실행하지 못했습니다: " + e.getClass().getSimpleName(), e);
            }
            try {
                CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
                try (OutputStream in = process.getOutputStream()) {
                    if (stdin != null) {
                        in.write(stdin.getBytes(StandardCharsets.UTF_8));
                    }
                }
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("security가 10초 안에 끝나지 않았습니다");
                }
                return new Result(process.exitValue(), new String(out.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IllegalStateException("security 실행이 중단됐습니다", e);
            } catch (IOException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                process.destroyForcibly();
                throw new IllegalStateException("security 실행 실패: " + e.getClass().getSimpleName(), e);
            }
        };
    }

    private static byte[] readAll(InputStream in) {
        try (in) {
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
