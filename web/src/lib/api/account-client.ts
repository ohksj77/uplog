import { get, NETWORK_MESSAGE, readErrorBody, USE_MOCK } from "@/lib/api/client";
import { AccountApiError } from "@/lib/api/account-types";
import type { AccountErrorBody, ConnectResult, Me, PublicIp, TossConnection, TossCredentials, TossSetup } from "@/lib/api/account-types";
import * as mockAccount from "@/lib/api/mock/account";

// 토스 계좌 연결 API (docs/design/account-connection.md, docs/design/desktop-app.md).
// 맥 앱의 로컬 엔진이 받는다. 로그인은 없고, 앱 창의 쿠키로 이 맥의 사용자임을 확인한다(없으면 403 DESKTOP_LOCKED).
// 상태를 바꾸는 요청은 엔진이 Origin 헤더로 같은 출처인지 확인한다.

function credentialField(value: unknown): "clientId" | "clientSecret" | null {
  return value === "clientId" || value === "clientSecret" ? value : null;
}

/** POST·PUT·DELETE. 본문이 없는 응답(202·204)은 null을 돌려준다. */
async function send<T>(method: "POST" | "PUT" | "DELETE", path: string, body?: unknown): Promise<T | null> {
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
    const err = await readErrorBody<AccountErrorBody>(res);
    throw new AccountApiError(res.status, err?.code ?? "UNKNOWN", err?.message ?? "알 수 없는 오류가 발생했어요.", credentialField(err?.field));
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : null;
}

/** mock이 던진 오류도 실제 요청처럼 거부된 Promise로, 네트워크처럼 조금 늦게 돌려준다. */
function mock<T>(make: () => T, delayMs = 250): Promise<T> {
  return new Promise((resolve) => setTimeout(resolve, delayMs)).then(make);
}

/** 로컬 사용자와 연결 상태. 화면이 보는 건 connection이다. */
export function getMe(signal?: AbortSignal): Promise<Me> {
  if (USE_MOCK) return mock(() => mockAccount.me(), 120);
  return get("/api/me", signal);
}

export function getTossSetup(signal?: AbortSignal): Promise<TossSetup> {
  if (USE_MOCK) return mock(() => mockAccount.setup());
  return get("/api/connections/toss/setup", signal);
}

/** 이 맥의 공인 IP. WTS 허용 IP 관리에 등록할 값이다. 사용자가 누를 때만 부른다(엔진이 api.ipify.org에 묻는다). */
export function getPublicIp(signal?: AbortSignal): Promise<PublicIp> {
  if (USE_MOCK) return mock(() => mockAccount.publicIp(), 400);
  return get("/api/desktop/public-ip", signal);
}

/** 키를 검증하고, 종합매매 계좌가 하나면 바로 연결한다. 키는 본문으로만 보내고 응답·로그에 남기지 않는다. */
export async function connectToss(credentials: TossCredentials): Promise<ConnectResult> {
  if (USE_MOCK) return mock(() => mockAccount.connect(credentials), 900);
  const res = await send<ConnectResult>("POST", "/api/connections/toss", credentials);
  return res ?? { status: "BACKFILLING" };
}

export async function chooseTossAccount(credentials: TossCredentials, accountSeq: string): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.chooseAccount(credentials, accountSeq), 700);
  await send("POST", "/api/connections/toss/account", { ...credentials, accountSeq });
}

/**
 * 연결한 계좌의 키만 새 키로 바꾼다(토큰 끊김·키 재발급). 새 키로 같은 계좌가 보여야 한다(아니면 400 INVALID_PARAMETER).
 * 오류는 연결과 같다(TOSS_INVALID_CREDENTIALS + field, TOSS_IP_NOT_ALLOWED …).
 */
export async function replaceTossCredentials(credentials: TossCredentials): Promise<void> {
  if (USE_MOCK) return mock(() => mockAccount.replaceCredentials(credentials), 900);
  await send("PUT", "/api/connections/toss/credentials", credentials);
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
