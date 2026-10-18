import { AccountApiError } from "@/lib/api/account-types";
import type {
  AuthProvider,
  AuthProviders,
  BrokerAccount,
  ConnectResult,
  Me,
  TossConnection,
  TossCredentials,
  TossSetup,
} from "@/lib/api/account-types";

// NEXT_PUBLIC_USE_MOCK=1 에서 로그인 세션과 연결 상태를 localStorage에 흉내 낸다. 키(client_secret)는 저장하지 않는다.
//
// 연결 흐름(가짜 토스 서버 규칙과 맞춘다):
// - client_secret이 "invalid"            → TOSS_INVALID_CREDENTIALS
// - client_id가 "ip-blocked"로 시작      → TOSS_IP_NOT_ALLOWED (egressIps 포함)
// - client_id가 "no-account"로 시작      → TOSS_NO_BROKERAGE_ACCOUNT
// - client_id가 "rate"로 시작            → TOSS_RATE_LIMITED
// - client_id가 "down"으로 시작          → TOSS_UNAVAILABLE
// - client_id가 "multi"로 시작           → CHOOSE_ACCOUNT(계좌 둘)
// - client_id가 "fail"로 시작            → 역산이 중간에 실패해 ERROR
// - 그 밖                                → BACKFILLING, 약 10초 동안 진행률이 올라 ACTIVE

const KEY = "uplog.mock.account";
const BACKFILL_MS = 10_000;
const SYNC_INTERVAL_MS = 60 * 60 * 1000;
const EGRESS_IPS = ["203.0.113.10", "203.0.113.11"];

type MockConnection = {
  maskedAccountNo: string;
  connectedAt: string;
  failAt: number | null; // 이 진행률에서 실패(0–1)
  lastSyncRequestedAt: string | null;
};

type MockState = {
  user: { id: number; nickname: string; provider: AuthProvider } | null;
  connection: MockConnection | null;
};

function load(): MockState {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return JSON.parse(raw) as MockState;
  } catch {
    // 저장소를 못 쓰면 비로그인으로 본다.
  }
  return { user: null, connection: null };
}

function save(state: MockState) {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    // 이번 화면에서만 유지되지 않아도 괜찮다.
  }
}

function requireUser(state: MockState) {
  if (!state.user) throw new AccountApiError(401, "UNAUTHENTICATED", "로그인이 필요해요.");
  return state.user;
}

function requireConnection(state: MockState) {
  requireUser(state);
  if (!state.connection) throw new AccountApiError(404, "ACCOUNT_NOT_CONNECTED", "연결된 계좌가 없어요.");
  return state.connection;
}

function progressOf(c: MockConnection): number {
  return Math.min(1, (Date.now() - Date.parse(c.connectedAt)) / BACKFILL_MS);
}

function statusOf(c: MockConnection): TossConnection["status"] {
  const p = progressOf(c);
  if (c.failAt != null && p >= c.failAt) return "ERROR";
  return p >= 1 ? "ACTIVE" : "BACKFILLING";
}

export function providers(): AuthProviders {
  return { kakao: true, dev: true };
}

export function me(): Me {
  const s = load();
  if (!s.user) return { user: null, connection: { status: "NONE", maskedAccountNo: null } };
  return {
    user: s.user,
    connection: s.connection
      ? { status: statusOf(s.connection), maskedAccountNo: s.connection.maskedAccountNo }
      : { status: "NONE", maskedAccountNo: null },
  };
}

export function login(nickname: string, provider: AuthProvider): void {
  const name = nickname.trim();
  if (!name) throw new AccountApiError(400, "INVALID_PARAMETER", "닉네임을 입력해 주세요.");
  const prev = load();
  // 같은 브라우저에서는 같은 사용자로 본다(연결 상태 유지).
  save({ user: { id: 1, nickname: name, provider }, connection: prev.connection });
}

export function logout(): void {
  const s = load();
  save({ ...s, user: null });
}

export function setup(): TossSetup {
  requireUser(load());
  return {
    egressIps: EGRESS_IPS,
    keyPageUrl: "https://wts.tossinvest.com/",
    notes: ["같은 키를 다른 도구에서도 쓰면 서로의 접속이 끊길 수 있어요. 업로그 전용 키를 새로 발급해 주세요."],
  };
}

const ACCOUNTS: BrokerAccount[] = [
  { accountSeq: "1", maskedAccountNo: "1234-**-**56", type: "BROKERAGE" },
  { accountSeq: "2", maskedAccountNo: "9876-**-**21", type: "BROKERAGE" },
];

function verify({ clientId, clientSecret }: TossCredentials): BrokerAccount[] {
  const s = load();
  requireUser(s);
  if (s.connection) throw new AccountApiError(409, "ALREADY_CONNECTED", "이미 연결된 계좌가 있어요.");
  if (!clientId.trim() || !clientSecret.trim()) throw new AccountApiError(400, "INVALID_PARAMETER", "client_id와 client_secret을 입력해 주세요.");
  if (clientSecret === "invalid") throw new AccountApiError(400, "TOSS_INVALID_CREDENTIALS", "키가 올바르지 않아요.");
  if (clientId.startsWith("ip-blocked")) throw new AccountApiError(400, "TOSS_IP_NOT_ALLOWED", "허용되지 않은 IP예요.", EGRESS_IPS);
  if (clientId.startsWith("no-account")) throw new AccountApiError(400, "TOSS_NO_BROKERAGE_ACCOUNT", "종합매매 계좌가 없어요.");
  if (clientId.startsWith("rate")) throw new AccountApiError(429, "TOSS_RATE_LIMITED", "요청이 너무 많아요.");
  if (clientId.startsWith("down")) throw new AccountApiError(502, "TOSS_UNAVAILABLE", "토스증권에 연결하지 못했어요.");
  return clientId.startsWith("multi") ? ACCOUNTS : ACCOUNTS.slice(0, 1);
}

function startConnection(account: BrokerAccount, clientId: string) {
  const s = load();
  save({
    ...s,
    connection: {
      maskedAccountNo: account.maskedAccountNo,
      connectedAt: new Date().toISOString(),
      failAt: clientId.startsWith("fail") ? 0.6 : null,
      lastSyncRequestedAt: null,
    },
  });
}

export function connect(credentials: TossCredentials): ConnectResult {
  const accounts = verify(credentials);
  if (accounts.length > 1) return { status: "CHOOSE_ACCOUNT", accounts };
  startConnection(accounts[0], credentials.clientId);
  return { status: "BACKFILLING" };
}

export function chooseAccount(credentials: TossCredentials, accountSeq: string): void {
  const account = verify(credentials).find((a) => a.accountSeq === accountSeq);
  if (!account) throw new AccountApiError(400, "INVALID_PARAMETER", "고른 계좌를 찾지 못했어요.");
  startConnection(account, credentials.clientId);
}

export function connection(): TossConnection {
  const c = requireConnection(load());
  const status = statusOf(c);
  const progress = progressOf(c);
  const doneAt = new Date(Date.parse(c.connectedAt) + BACKFILL_MS).toISOString();
  return {
    status,
    maskedAccountNo: c.maskedAccountNo,
    connectedAt: c.connectedAt,
    backfill:
      status === "ERROR"
        ? { state: "FAILED", progress: c.failAt ?? progress }
        : { state: progress >= 1 ? "DONE" : progress < 0.05 ? "PENDING" : "RUNNING", progress: Math.round(progress * 100) / 100 },
    lastSyncedAt: status === "ACTIVE" ? (c.lastSyncRequestedAt ?? doneAt) : null,
    lastError: status === "ERROR" ? "체결 내역을 받는 중 토스증권이 응답하지 않았어요." : null,
    recentGaps: status === "ACTIVE" ? ["2026-10-12"] : [],
  };
}

export function sync(): void {
  const s = load();
  const c = requireConnection(s);
  const last = c.lastSyncRequestedAt ? Date.parse(c.lastSyncRequestedAt) : 0;
  if (Date.now() - last < SYNC_INTERVAL_MS) {
    throw new AccountApiError(429, "SYNC_TOO_SOON", "다시 수집은 1시간에 한 번 할 수 있어요.");
  }
  save({ ...s, connection: { ...c, lastSyncRequestedAt: new Date().toISOString() } });
}

export function disconnect(): void {
  const s = load();
  requireConnection(s);
  save({ ...s, connection: null });
}
