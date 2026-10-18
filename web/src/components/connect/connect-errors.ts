import type { ApiError } from "@/lib/api/client";

export type ConnectErrorView = {
  title: string;
  body: string;
  /** 다시 보여 줄 도움: IP 목록, 설정 링크, 로그인 링크 */
  help?: "ip" | "settings" | "login";
  /** 키 입력 칸에 묶을 오류인가(aria-invalid) */
  credentialField?: boolean;
};

/** 연결 API 오류 코드 → 사용자에게 보일 안내. */
export function connectErrorView(error: ApiError): ConnectErrorView {
  switch (error.code) {
    case "TOSS_INVALID_CREDENTIALS":
      return {
        title: "키가 맞지 않아요",
        body: "client_id나 client_secret이 토스증권에 등록된 값과 달라요. WTS에서 복사한 값을 앞뒤 공백 없이 다시 붙여 넣어 주세요. 키를 다시 발급했다면 새 값을 넣어야 해요.",
        credentialField: true,
      };
    case "TOSS_IP_NOT_ALLOWED":
      return {
        title: "허용 IP에 업로그 서버가 없어요",
        body: "토스증권이 업로그 서버의 접속을 막았어요. WTS 설정 > Open API > 허용 IP 관리에 아래 IP를 모두 추가한 뒤 다시 확인해 주세요. 등록이 반영되기까지 잠깐 걸릴 수 있어요.",
        help: "ip",
      };
    case "TOSS_NO_BROKERAGE_ACCOUNT":
      return {
        title: "종합매매 계좌를 찾지 못했어요",
        body: "이 키로 볼 수 있는 토스증권 종합매매 계좌가 없어요. 계좌를 개설했는지, 키를 발급한 토스 계정이 맞는지 확인해 주세요.",
      };
    case "TOSS_RATE_LIMITED":
      return {
        title: "잠시 후 다시 시도해 주세요",
        body: "토스증권에 짧은 시간 동안 요청이 많이 몰렸어요. 1분쯤 기다렸다가 다시 확인해 주세요.",
      };
    case "TOSS_UNAVAILABLE":
      return {
        title: "토스증권에 연결하지 못했어요",
        body: "토스증권 서버가 지금 응답하지 않아요. 입력한 키에는 문제가 없을 수 있어요. 잠시 후 다시 시도해 주세요.",
      };
    case "ALREADY_CONNECTED":
      return {
        title: "이미 연결된 계좌가 있어요",
        body: "계좌는 하나만 연결할 수 있어요. 다른 계좌로 바꾸려면 연결 설정에서 지금 연결을 해제한 뒤 다시 연결해 주세요.",
        help: "settings",
      };
    case "UNAUTHENTICATED":
      return {
        title: "로그인이 풀렸어요",
        body: "보안을 위해 입력한 키는 지웠어요. 다시 로그인한 뒤 연결을 이어 가 주세요.",
        help: "login",
      };
    default:
      return { title: "연결하지 못했어요", body: error.message };
  }
}
