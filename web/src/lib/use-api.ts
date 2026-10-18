"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";

export type ApiState<T> =
  | { status: "loading" }
  | { status: "success"; data: T }
  | { status: "error"; error: ApiError };

type Settled<T> = { key: string } & ({ data: T } | { error: ApiError });

/**
 * 키가 바뀌면 이전 요청을 취소하고 다시 부른다. 결과는 요청 키로 묶어 오래된 응답을 버린다.
 * key는 요청을 구분하는 모든 값을 담아야 한다(예: `log:${year}`). null이면 부르지 않는다.
 */
export function useApi<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [settled, setSettled] = useState<Settled<T> | null>(null);
  const fullKey = key == null ? null : `${key}#${attempt}`;

  useEffect(() => {
    if (fullKey == null) return;
    const controller = new AbortController();
    load(controller.signal)
      .then((data) => setSettled({ key: fullKey, data }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const error = e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "알 수 없는 오류가 발생했어요.");
        setSettled({ key: fullKey, error });
      });
    return () => controller.abort();
    // load는 key가 같으면 같은 요청이다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fullKey]);

  let state: ApiState<T> = { status: "loading" };
  if (settled && settled.key === fullKey) {
    state = "data" in settled ? { status: "success", data: settled.data } : { status: "error", error: settled.error };
  }
  return { state, retry: () => setAttempt((a) => a + 1) };
}
