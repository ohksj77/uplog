import { get, USE_MOCK } from "@/lib/api/client";
import { AccountApiError } from "@/lib/api/account-types";
import type {
  AccountErrorBody,
  AuthProviders,
  ConnectResult,
  Me,
  TossConnection,
  TossCredentials,
  TossSetup,
} from "@/lib/api/account-types";
import * as mockAccount from "@/lib/api/mock/account";

// 로그인·토스 계좌 연결 API (docs/design/account-connection.md).
// 상태를 바꾸는 요청은 세션 쿠키(uplog_session)를 함께 보내고, 백엔드가 Origin 헤더로 CSRF를 막는다.

const NETWORK_MESSAGE = "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.";

/** POST·DELETE. 본문이 없는 응답(202·204)은 null을 돌려준다. */
async function send<T>(method: "POST" | "DELETE", path: string, body?: unknown): Promise<T | null> {
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      credentials: "same-origin",
      headers: body === undefined ? { Accept: "application/json" } : { Accept: "application/json", "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new AccountApiError(0, "NETWORK", NETWORK_MESSAGE);
  }
  if (!res.ok) {
    const err = (await res.json().catch(() => null)) as AccountErrorBody | null;
    throw new AccountApiError(
      res.status,
      err?.code ?? "UNKNOWN",
      err?.message ?? "알 수 없는 오류가 발생했어요.",
      Array.isArray(err?.egressIps) ? err.egressIps : [],
    );
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : null;
}

/** mock이 던진 오류도 실제 요청처럼 거부된 Promise로, 네트워크처럼 조금 늦게 돌려준다. */
function mock<T>(make: () => T, delayMs = 250): Promise<T> {
  return new Promise((resolve) => setTimeout(resolve, delayMs)).then(make);
}

export function getMe(signal?: AbortSignal): Promise<Me> {
  if (USE_MOCK) return mock(() => mockAccount.me(), 120);
  return get("/api/me", signal);
}

export function getAuthProviders(signal?: AbortSignal): Promise<AuthProviders> {
  if (USE_MOCK) return mock(() => mockAccount.providers());
  return get("/api/auth/providers", signal);
}

export async function devLogin(nickname: string): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.login(nickname, "DEV"));
  await send("POST", "/api/auth/dev-login", { nickname });
}

/** 카카오 로그인 시작 경로. 백엔드 OAuth2 왕복이라 <a>로 전체 페이지를 이동한다(next.config.ts rewrite). */
export const KAKAO_LOGIN_PATH = "/oauth2/authorization/kakao";

/** mock 전용: 카카오 왕복 없이 바로 로그인된 것으로 친다. */
export function mockKakaoLogin(): Promise<void> {
  return mock(() => mockAccount.login("카카오 사용자", "KAKAO"));
}

export async function logout(): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.logout());
  await send("POST", "/api/auth/logout");
}

export function getTossSetup(signal?: AbortSignal): Promise<TossSetup> {
  if (USE_MOCK) return mock(() => mockAccount.setup());
  return get("/api/connections/toss/setup", signal);
}

/** 키를 검증하고, 종합매매 계좌가 하나면 바로 연결한다. 키는 본문으로만 보내고 어디에도 남기지 않는다. */
export async function connectToss(credentials: TossCredentials): Promise<ConnectResult> {
  if (USE_MOCK) return mock(() => mockAccount.connect(credentials), 900);
  const res = await send<ConnectResult>("POST", "/api/connections/toss", credentials);
  return res ?? { status: "BACKFILLING" };
}

export async function chooseTossAccount(credentials: TossCredentials, accountSeq: string): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.chooseAccount(credentials, accountSeq), 700);
  await send("POST", "/api/connections/toss/account", { ...credentials, accountSeq });
}

export function getTossConnection(signal?: AbortSignal): Promise<TossConnection> {
  if (USE_MOCK) return mock(() => mockAccount.connection());
  return get("/api/connections/toss", signal);
}

/** 수동 다시 수집. 계좌당 1시간에 한 번이라 넘으면 429. */
export async function syncToss(): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.sync());
  await send("POST", "/api/connections/toss/sync");
}

/** 연결 해제. 키는 항상 즉시 파기되고, deleteData면 계좌 기록도 지운다. */
export async function disconnectToss(deleteData: boolean): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.disconnect());
  await send("DELETE", `/api/connections/toss?deleteData=${deleteData}`);
}
