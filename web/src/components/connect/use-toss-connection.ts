"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { getTossConnection } from "@/lib/api/account-client";
import type { TossConnection } from "@/lib/api/account-types";

export type TossConnectionState =
  | { status: "loading" }
  | { status: "success"; data: TossConnection }
  | { status: "error"; error: ApiError };

const POLL_MS = 2000;

/**
 * GET /api/connections/toss. 역산 중(BACKFILLING)이면 2초마다 다시 불러 ACTIVE나 ERROR가 되면 멈춘다.
 * 화면이 숨겨지면(다른 경로로 이동) effect 정리로 폴링도 멈추고, 돌아오면 다시 시작한다.
 */
export function useTossConnection(enabled: boolean) {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<TossConnectionState>({ status: "loading" });

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    const tick = async () => {
      try {
        const data = await getTossConnection(controller.signal);
        if (controller.signal.aborted) return;
        setState({ status: "success", data });
        if (data.status === "BACKFILLING") timer = setTimeout(tick, POLL_MS);
      } catch (e) {
        if (controller.signal.aborted) return;
        const error = e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "알 수 없는 오류가 발생했어요.");
        // 폴링 중 일시적인 실패(네트워크·5xx)는 마지막 값을 두고 조금 뒤 다시 묻는다.
        if (error.status === 0 || error.status >= 500) {
          setState((prev) => (prev.status === "success" ? prev : { status: "error", error }));
          timer = setTimeout(tick, POLL_MS * 2);
        } else {
          setState({ status: "error", error });
        }
      }
    };
    void tick();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [enabled, version]);

  return { state, reload: () => setVersion((v) => v + 1) };
}
