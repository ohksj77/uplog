"use client";

import { useSyncExternalStore } from "react";
import { ApiError } from "@/lib/api/client";
import { getMe } from "@/lib/api/account-client";
import type { Me } from "@/lib/api/account-types";

// 로그인한 사용자와 연결 상태(GET /api/me). 헤더·홈·연결 화면이 같은 값을 나눠 쓰도록 모듈 하나에 둔다.
// 로그인·로그아웃·연결·해제 뒤에는 refreshMe()로 다시 불러온다. 다시 부르는 동안에는 이전 값을 그대로 보여 준다.

export type MeState =
  | { status: "loading" }
  | { status: "success"; data: Me }
  | { status: "error"; error: ApiError };

const LOADING: MeState = { status: "loading" };

let snapshot: MeState = LOADING;
let inflight: Promise<Me | null> | null = null;
let started = false;
const listeners = new Set<() => void>();

function emit(next: MeState) {
  snapshot = next;
  listeners.forEach((l) => l());
}

/** /api/me를 다시 불러온다. 동시에 여러 번 불러도 요청은 하나만 나간다. */
export function refreshMe(): Promise<Me | null> {
  started = true;
  inflight ??= getMe()
    .then((data) => {
      emit({ status: "success", data });
      return data;
    })
    .catch((e: unknown) => {
      const error = e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "알 수 없는 오류가 발생했어요.");
      // 이미 값이 있으면 일시적인 실패로 보고 그 값을 둔다.
      if (snapshot.status !== "success") emit({ status: "error", error });
      return null;
    })
    .finally(() => {
      inflight = null;
    });
  return inflight;
}

function subscribe(onChange: () => void) {
  listeners.add(onChange);
  if (!started) void refreshMe();
  return () => {
    listeners.delete(onChange);
  };
}

export function useMe() {
  const state = useSyncExternalStore(
    subscribe,
    () => snapshot,
    () => LOADING,
  );
  const me = state.status === "success" ? state.data : null;
  return {
    state,
    /** 불러오기 전이면 undefined, 비로그인(또는 /api/me 실패)이면 null */
    user: state.status === "success" ? state.data.user : state.status === "error" ? null : undefined,
    connectionStatus: me?.connection?.status ?? "NONE",
    refresh: refreshMe,
  };
}
