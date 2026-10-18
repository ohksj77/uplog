import type { Metadata } from "next";
import { ConnectFlow } from "@/components/connect/ConnectFlow";

export const metadata: Metadata = {
  title: "계좌 연결 | UpLog",
  description: "토스증권 Open API 키로 내 계좌를 연결해요. UpLog는 조회만 해요.",
};

export default function ConnectPage() {
  return <ConnectFlow />;
}
