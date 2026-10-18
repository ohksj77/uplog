import type { Metadata } from "next";
import { Suspense } from "react";
import { HoldingDetailFromUrl } from "@/components/holding";
import { LoadingBlock } from "@/components/ui/AsyncState";

export const metadata: Metadata = {
  title: "종목 상세 | 업로그",
  description: "한 종목의 보유 현황, 사고판 시점, 판 뒤의 주가를 모아 봐요.",
};

// /holdings?code=XXX. 정적 export라 종목 코드는 쿼리로 받고, 본문은 Suspense 안에서 클라이언트가 그린다.
export default function HoldingPage() {
  return (
    <Suspense fallback={<LoadingBlock height={520} />}>
      <HoldingDetailFromUrl />
    </Suspense>
  );
}
