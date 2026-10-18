package com.uplog.credential;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 처음 키가 필요할 때(계좌 연결·수집) 마스터키를 읽는다. 엔진은 키체인에 접근하지 않고도 뜨고 데모 계좌를 보여 준다.
 * 읽기에 실패하면 다음에 다시 시도한다.
 */
final class LazyMasterKeyProvider implements MasterKeyProvider {

    private final Supplier<byte[]> source;
    private final String description;
    private volatile StaticMasterKeyProvider delegate;

    /** @param source 32바이트 키를 주는 곳. 받은 배열은 여기서 지운다 */
    LazyMasterKeyProvider(Supplier<byte[]> source, String description) {
        this.source = source;
        this.description = description;
    }

    @Override
    public WrappedKey wrap(byte[] dataKey) {
        return delegate().wrap(dataKey);
    }

    @Override
    public byte[] unwrap(WrappedKey wrapped) {
        return delegate().unwrap(wrapped);
    }

    private StaticMasterKeyProvider delegate() {
        StaticMasterKeyProvider d = delegate;
        if (d == null) {
            synchronized (this) {
                d = delegate;
                if (d == null) {
                    byte[] key = source.get();
                    try {
                        d = new StaticMasterKeyProvider(key);
                    } finally {
                        Arrays.fill(key, (byte) 0);
                    }
                    delegate = d;
                }
            }
        }
        return d;
    }

    @Override
    public String toString() {
        return "LazyMasterKeyProvider[" + description + "]";
    }
}
