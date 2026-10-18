import { AccountApiError } from "@/lib/api/account-types";
import type { BrokerAccount, ConnectResult, Me, PublicIp, TossConnection, TossCredentials, TossSetup } from "@/lib/api/account-types";

// NEXT_PUBLIC_USE_MOCK=1 (next dev 전용)에서 연결 상태를 localStorage에 흉내 낸다. 키(client_secret)는 저장하지 않는다.
// 맥 앱처럼 로그인은 없고, 로컬 사용자 한 명이 늘 있다.
//
// 연결 흐름(가짜 토스 서버 규칙과 맞춘다):
// - client_secret이 "invalid"            → TOSS_INVALID_CREDENTIALS (field: clientSecret)
// - client_id가 "bad-id"로 시작          → TOSS_INVALID_CREDENTIALS (field: clientId)
// - client_id가 "ip-blocked"로 시작      → TOSS_IP_NOT_ALLOWED
// - client_id가 "no-account"로 시작      → TOSS_NO_BROKERAGE_ACCOUNT
// - client_id가 "rate"로 시작            → TOSS_RATE_LIMITED
// - client_id가 "down"으로 시작          → TOSS_UNAVAILABLE
// - client_id가 "multi"로 시작           → CHOOSE_ACCOUNT(계좌 둘)
// - client_id가 "fail"로 시작            → 역산이 중간에 실패해 ERROR
// - 그 밖                                → BACKFILLING, 약 10초 동안 진행률이 올라 ACTIVE
//
// 키 다시 넣기(PUT /api/connections/toss/credentials): 위 오류 규칙과 같고, client_id가 "other"로 시작하면
// 다른 계좌의 키라 INVALID_PARAMETER. 성공하면 ERROR였던 연결이 ACTIVE로 돌아온다.

const KEY = "uplog.mock.account";
const BACKFILL_MS = 10_000;
const SYNC_INTERVAL_MS = 60 * 60 * 1000;
const LOCAL_USER = { id: 1 };

type MockConnection = {
  maskedAccountNo: string;
  connectedAt: string;
  failAt: number | null; // 이 진행률에서 실패(0–1)
  lastSyncRequestedAt: string | null;
};

type MockState = {
  connection: MockConnection | null;
};

function load(): MockState {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { connection: (JSON.parse(raw) as Partial<MockState>).connection ?? null };
  } catch {
    // 저장소를 못 쓰면 연결 전으로 본다.
  }
  return { connection: null };
}

function save(state: MockState) {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    // 이번 화면에서만 유지되지 않아도 괜찮다.
  }
}

function requireConnection(state: MockState) {
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

export function me(): Me {
  const s = load();
  return {
    user: LOCAL_USER,
    connection: s.connection
      ? { status: statusOf(s.connection), maskedAccountNo: s.connection.maskedAccountNo }
      : { status: "NONE", maskedAccountNo: null },
  };
}

export function setup(): TossSetup {
  return {
    keyPageUrl: "https://www.tossinvest.com/",
    notes: ["같은 키를 다른 도구에서도 쓰면 서로의 접속이 끊길 수 있어요. UpLog 전용 키를 새로 발급해 주세요."],
    simulated: true,
  };
}

export function publicIp(): PublicIp {
  return { ip: "203.0.113.42" };
}

const ACCOUNTS: BrokerAccount[] = [
  { accountSeq: "1", maskedAccountNo: "1234-**-**56", type: "BROKERAGE" },
  { accountSeq: "2", maskedAccountNo: "9876-**-**21", type: "BROKERAGE" },
];

/** 연결·키 다시 넣기가 함께 쓰는 키 검사(가짜 토스 서버 규칙). */
function checkKeys({ clientId, clientSecret }: TossCredentials): BrokerAccount[] {
  if (!clientId.trim() || !clientSecret.trim()) throw new AccountApiError(400, "INVALID_PARAMETER", "client_id와 client_secret을 입력해 주세요.");
  if (clientId.startsWith("bad-id")) {
    throw new AccountApiError(
      400,
      "TOSS_INVALID_CREDENTIALS",
      "토스증권에 없는 client_id예요. WTS Open API 화면의 Client Id를 복사 버튼으로 다시 복사해 붙여 넣어 주세요.",
      "clientId",
    );
  }
  if (clientSecret === "invalid") {
    throw new AccountApiError(
      400,
      "TOSS_INVALID_CREDENTIALS",
      "client_secret이 맞지 않아요. 키를 발급할 때 복사한 값을 앞뒤 공백 없이 다시 붙여 넣어 주세요. 잃어버렸다면 재발급해야 해요.",
      "clientSecret",
    );
  }
  if (clientId.startsWith("ip-blocked")) throw new AccountApiError(400, "TOSS_IP_NOT_ALLOWED", "허용되지 않은 IP예요.");
  if (clientId.startsWith("no-account")) throw new AccountApiError(400, "TOSS_NO_BROKERAGE_ACCOUNT", "종합매매 계좌가 없어요.");
  if (clientId.startsWith("rate")) throw new AccountApiError(429, "TOSS_RATE_LIMITED", "요청이 너무 많아요.");
  if (clientId.startsWith("down")) throw new AccountApiError(502, "TOSS_UNAVAILABLE", "토스증권에 연결하지 못했어요.");
  return clientId.startsWith("multi") ? ACCOUNTS : ACCOUNTS.slice(0, 1);
}

function verify(credentials: TossCredentials): BrokerAccount[] {
  if (load().connection) throw new AccountApiError(409, "ALREADY_CONNECTED", "이미 연결된 계좌가 있어요.");
  return checkKeys(credentials);
}

function startConnection(account: BrokerAccount, clientId: string) {
  save({
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
  save({ connection: { ...c, lastSyncRequestedAt: new Date().toISOString() } });
}

export function disconnect(): void {
  requireConnection(load());
  save({ connection: null });
}

export function replaceCredentials(credentials: TossCredentials): void {
  const c = requireConnection(load());
  checkKeys(credentials);
  if (credentials.clientId.startsWith("other")) {
    throw new AccountApiError(400, "INVALID_PARAMETER", "이 키로는 연결한 계좌가 보이지 않아요. 연결한 계좌를 연 토스 계정에서 발급한 키를 넣어 주세요.");
  }
  // 새 키로 다시 수집할 수 있게 됐으니 멈췄던 연결을 되살린다.
  save({ connection: { ...c, failAt: null } });
}
