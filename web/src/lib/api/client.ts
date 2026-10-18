import type { ApiErrorBody, EtfListItem, SimulationParams, SimulationResponse } from "@/lib/api/types";
import { toSearchParams } from "@/lib/simulation-params";
import { mockEtfs, mockSimulation } from "@/lib/api/mock";

// 브라우저에서 호출한다. /api/* 는 next.config.ts의 rewrite로 백엔드(API_ORIGIN)에 전달된다.
// NEXT_PUBLIC_USE_MOCK=1 이면 백엔드 없이 mock 데이터를 쓴다.
export const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK === "1";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, { signal, headers: { Accept: "application/json" } });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "NETWORK", "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
  }
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as ApiErrorBody | null;
    throw new ApiError(res.status, body?.code ?? "UNKNOWN", body?.message ?? "알 수 없는 오류가 발생했어요.");
  }
  return (await res.json()) as T;
}

export function searchEtfs(query: string, signal?: AbortSignal): Promise<EtfListItem[]> {
  if (USE_MOCK) return Promise.resolve(mockEtfs(query));
  const q = new URLSearchParams({ query, limit: "20" });
  return get(`/api/etfs?${q}`, signal);
}

export function runSimulation(params: SimulationParams, signal?: AbortSignal): Promise<SimulationResponse> {
  if (USE_MOCK) return Promise.resolve(mockSimulation(params));
  return get(`/api/simulations?${toSearchParams(params)}`, signal);
}
