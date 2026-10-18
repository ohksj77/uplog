package com.uplog.ingest;

/** 이 엔진이 큐에 작업을 넣었다는 신호. 한가해서 폴링을 늦춘 워커를 바로 깨운다. */
public final class QueueSignal {

    private volatile boolean pending;

    public void wake() {
        pending = true;
    }

    /** 신호가 있었으면 true를 돌려주고 지운다. */
    boolean consume() {
        boolean was = pending;
        pending = false;
        return was;
    }
}
