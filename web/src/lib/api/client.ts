import type { ApiErrorBody, EtfListItem, SimulationParams, SimulationResponse } from "@/lib/api/types";
import { toSearchParams } from "@/lib/simulation-params";
import { mockInstruments, mockSimulation } from "@/lib/api/mock";
import { DESKTOP_LOCKED, markDesktopLocked } from "@/lib/desktop-lock";

// 브라우저에서 호출한다. 맥 앱에서는 이 화면을 서빙하는 로컬 엔진(127.0.0.1)이 같은 출처로 /api를 받는다.
// next dev에서는 next.config.ts의 rewrite가 /api를 개발용 엔진(API_ORIGIN)으로 넘긴다.
// NEXT_PUBLIC_USE_MOCK=1 이면 엔진 없이 mock 데이터를 쓴다. next dev에서만 켜지고, 빌드(정적 export)에는 들어가지 않는다.
export const USE_MOCK = process.env.NODE_ENV === "development" && process.env.NEXT_PUBLIC_USE_MOCK === "1";

export const NETWORK_MESSAGE = "업로그 앱에 연결하지 못했어요. 앱이 켜져 있는지 확인하고, 계속되면 앱을 다시 열어 주세요.";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

/**
 * 오류 응답 본문을 읽는다. 403 DESKTOP_LOCKED(앱 창 밖에서 열린 화면)면 화면 전체 안내를 띄운다.
 * account-client도 이 함수로 읽어 같은 규칙을 따른다.
 */
export async function readErrorBody<T extends { code?: string; message?: string }>(res: Response): Promise<T | null> {
  const body = (await res.json().catch(() => null)) as T | null;
  if (res.status === 403 && body?.code === DESKTOP_LOCKED) markDesktopLocked();
  return body;
}

export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, { signal, credentials: "same-origin", headers: { Accept: "application/json" } });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "NETWORK", NETWORK_MESSAGE);
  }
  if (!res.ok) {
    const body = await readErrorBody<ApiErrorBody>(res);
    throw new ApiError(res.status, body?.code ?? "UNKNOWN", body?.message ?? "알 수 없는 오류가 발생했어요.");
  }
  return (await res.json()) as T;
}

/** 실제 ETF·주식 검색(코드 일치 → ETF → 주식 순). 계좌를 연결하지 않았으면 403 SIMULATION_REQUIRES_CONNECTION. */
export function searchInstruments(query: string, signal?: AbortSignal): Promise<EtfListItem[]> {
  if (USE_MOCK) return Promise.resolve(mockInstruments(query));
  const q = new URLSearchParams({ query, limit: "20" });
  return get(`/api/instruments?${q}`, signal);
}

/** 시뮬레이션은 계좌를 연결한 뒤에 쓸 수 있다(시세를 그 사용자의 토스 키로 받는다). */
export const REQUIRES_CONNECTION = "SIMULATION_REQUIRES_CONNECTION";

export function runSimulation(params: SimulationParams, signal?: AbortSignal): Promise<SimulationResponse> {
  if (USE_MOCK) return Promise.resolve(mockSimulation(params));
  return get(`/api/simulations?${toSearchParams(params)}`, signal);
}
