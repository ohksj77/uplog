import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginScreen } from "@/components/auth/LoginScreen";
import { LoadingBlock } from "@/components/ui/AsyncState";

export const metadata: Metadata = {
  title: "로그인 | 업로그",
  description: "로그인하고 내 토스증권 계좌의 하루하루를 로그로 확인해요.",
};

// ?next=는 요청마다 달라서 LoginScreen(useSearchParams)은 Suspense 안에서 클라이언트가 그린다.
export default function LoginPage() {
  return (
    <Suspense fallback={<LoadingBlock height={320} label="로그인 화면을 불러오는 중" />}>
      <LoginScreen />
    </Suspense>
  );
}
