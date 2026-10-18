// docs/design/account-connection.md "API 계약"의 타입. 계약을 바꾸면 이 파일도 함께 고친다.

import { ApiError } from "@/lib/api/client";

export type AuthProvider = "KAKAO" | "DEV";

export type User = {
  id: number;
  nickname: string;
  provider: AuthProvider;
};

/** 연결 상태. NONE = 연결 없음(비로그인 포함). */
export type ConnectionStatus = "NONE" | "BACKFILLING" | "ACTIVE" | "ERROR";

export type Me = {
  user: User | null;
  connection: {
    status: ConnectionStatus;
    maskedAccountNo: string | null;
  } | null;
};

export type AuthProviders = {
  kakao: boolean;
  dev: boolean;
};

export type TossSetup = {
  /** 사용자가 WTS 허용 IP 관리에 등록할 우리 서버의 나가는 IP */
  egressIps: string[];
  /** WTS Open API 설정 화면 주소 */
  keyPageUrl: string;
  notes: string[];
  /** 진짜 토스 대신 개발용 가짜 토스 서버에 연결되는 환경이면 true */
  simulated: boolean;
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

/** 연결 API의 오류 코드. 공통 형식({code, message})에 TOSS_IP_NOT_ALLOWED는 egressIps가 붙는다. */
export type AccountErrorCode =
  | "UNAUTHENTICATED"
  | "TOSS_INVALID_CREDENTIALS"
  | "TOSS_IP_NOT_ALLOWED"
  | "TOSS_NO_BROKERAGE_ACCOUNT"
  | "TOSS_RATE_LIMITED"
  | "TOSS_UNAVAILABLE"
  | "ALREADY_CONNECTED"
  | "HANDOFF_INVALID";

/** TOSS_INVALID_CREDENTIALS에서 토스가 어느 값이 틀렸는지 알려 줄 때 */
export type CredentialField = "clientId" | "clientSecret";

export type AccountErrorBody = {
  code: AccountErrorCode | string;
  message: string;
  egressIps?: string[];
  field?: CredentialField;
};

/** 연결 API 오류. TOSS_IP_NOT_ALLOWED는 등록해야 할 IP를, 키 오류는 틀린 칸(field)을 함께 싣는다. */
export class AccountApiError extends ApiError {
  constructor(
    status: number,
    code: string,
    message: string,
    readonly egressIps: string[] = [],
    readonly field: CredentialField | null = null,
  ) {
    super(status, code, message);
  }
}

/** 다른 기기(PC)에서 이어 하기 1회용 코드. 10분 동안 한 번만 쓸 수 있다. */
export type HandoffToken = {
  token: string;
  expiresAt: string; // ISO
};

export type HandoffRedeemResult = {
  nickname: string;
};
