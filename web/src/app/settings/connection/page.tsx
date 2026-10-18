import type { Metadata } from "next";
import { ConnectionSettings } from "@/components/connect/ConnectionSettings";

export const metadata: Metadata = {
  title: "계좌 연결 설정 | 업로그",
  description: "토스증권 연결 상태를 확인하고, 다시 수집하거나 연결을 해제해요.",
};

export default function ConnectionSettingsPage() {
  return <ConnectionSettings />;
}
