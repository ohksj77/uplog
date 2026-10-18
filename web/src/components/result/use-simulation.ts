"use client";

import { useEffect, useState } from "react";
import { ApiError, runSimulation } from "@/lib/api/client";
import type { SimulationParams, SimulationResponse } from "@/lib/api/types";
import { toSearchParams } from "@/lib/simulation-params";

export type SimulationState =
  | { status: "loading" }
  | { status: "success"; data: SimulationResponse }
  | { status: "error"; error: ApiError };

type Settled = { key: string } & ({ data: SimulationResponse } | { error: ApiError });

/** 파라미터가 바뀌면 이전 요청을 취소하고 다시 부른다. 결과는 요청 키로 묶어 오래된 응답을 버린다. */
export function useSimulation(params: SimulationParams | null) {
  const [attempt, setAttempt] = useState(0);
  const [settled, setSettled] = useState<Settled | null>(null);
  const key = params ? `${toSearchParams(params)}#${attempt}` : null;

  useEffect(() => {
    if (!params || !key) return;
    const controller = new AbortController();
    runSimulation(params, controller.signal)
      .then((data) => setSettled({ key, data }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const error = e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "알 수 없는 오류가 발생했어요.");
        setSettled({ key, error });
      });
    return () => controller.abort();
    // key가 params 내용을 모두 담는다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  let state: SimulationState = { status: "loading" };
  if (settled && settled.key === key) {
    state = "data" in settled ? { status: "success", data: settled.data } : { status: "error", error: settled.error };
  }
  return { state, retry: () => setAttempt((a) => a + 1) };
}
