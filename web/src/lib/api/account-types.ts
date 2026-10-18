// docs/design/account-connection.md "API 계약"과 docs/design/desktop-app.md의 타입. 계약을 바꾸면 이 파일도 함께 고친다.

import { ApiError } from "@/lib/api/client";

/** 맥 앱의 로컬 사용자(첫 실행 때 엔진이 만든다). 로그인이 없어서 화면은 거의 쓰지 않는다. */
export type User = {
  id: number;
  nickname?: string;
};

/** 연결 상태. NONE = 연결 없음. */
export type ConnectionStatus = "NONE" | "BACKFILLING" | "ACTIVE" | "ERROR";

export type Me = {
  user: User | null;
  connection: {
    status: ConnectionStatus;
    maskedAccountNo: string | null;
  } | null;
};

export type TossSetup = {
  /** WTS Open API 설정 화면 주소 */
  keyPageUrl: string;
  notes: string[];
  /** 진짜 토스 대신 개발용 가짜 토스 서버에 연결되는 환경이면 true */
  simulated: boolean;
};

/** GET /api/desktop/public-ip. 사용자가 누를 때만 엔진이 api.ipify.org에 묻는다. */
export type PublicIp = {
  ip: string;
};

export type TossCredentials = {
  clientId: string;
  clientSecret: string;
};

export type BrokerAccount = {
  accountSeq: string;
  maskedAccountNo: string;
  type: string; // 현재는 BROKERAGE만
};

export type ConnectResult =
  | { status: "CHOOSE_ACCOUNT"; accounts: BrokerAccount[] }
  | { status: "BACKFILLING" };

export type BackfillState = "PENDING" | "RUNNING" | "DONE" | "FAILED";

export type TossConnection = {
  status: Exclude<ConnectionStatus, "NONE">;
  maskedAccountNo: string | null;
  connectedAt: string | null; // YYYY-MM-DD (연결한 날)
  backfill: { state: BackfillState; progress: number } | null; // progress 0–1
  lastSyncedAt: string | null;
  lastError: string | null;
  recentGaps: string[]; // YYYY-MM-DD
};

/** 연결 API의 오류 코드. 공통 형식({code, message}). */
export type AccountErrorCode =
  | "TOSS_INVALID_CREDENTIALS"
  | "TOSS_IP_NOT_ALLOWED"
  | "TOSS_NO_BROKERAGE_ACCOUNT"
  | "TOSS_RATE_LIMITED"
  | "TOSS_UNAVAILABLE"
  | "ALREADY_CONNECTED";

/** TOSS_INVALID_CREDENTIALS에서 토스가 어느 값이 틀렸는지 알려 줄 때 */
export type CredentialField = "clientId" | "clientSecret";

export type AccountErrorBody = {
  code: AccountErrorCode | string;
  message: string;
  field?: CredentialField;
};

/** 연결 API 오류. 키 오류는 틀린 칸(field)을 함께 싣는다. */
export class AccountApiError extends ApiError {
  constructor(
    status: number,
    code: string,
    message: string,
    readonly field: CredentialField | null = null,
  ) {
    super(status, code, message);
  }
}
