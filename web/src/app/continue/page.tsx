import type { Metadata } from "next";
import { Suspense } from "react";
import { ContinueScreen } from "@/components/auth/ContinueScreen";
import { LoadingBlock } from "@/components/ui/AsyncState";

export const metadata: Metadata = {
  title: "PC에서 이어 하기 | 업로그",
  description: "휴대폰에서 만든 링크로 이 기기에서 로그인해 계좌 연결을 이어 가요.",
  robots: { index: false, follow: false },
  // 1회용 코드가 든 주소가 다른 사이트로 새지 않게 한다.
  referrer: "no-referrer",
};

// ?token=은 요청마다 달라서 ContinueScreen(useSearchParams)은 Suspense 안에서 클라이언트가 그린다.
export default function ContinuePage() {
  return (
    <Suspense fallback={<LoadingBlock height={200} label="이어 하기를 준비하는 중" />}>
      <ContinueScreen />
    </Suspense>
  );
}
