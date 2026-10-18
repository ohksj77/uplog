import type { Metadata } from "next";
import { Suspense } from "react";
import { ResultSkeleton } from "@/components/result/ResultSkeleton";
import { SimulationResultView } from "@/components/result/SimulationResultView";

export const metadata: Metadata = {
  title: "시뮬레이션 결과 | UpLog",
  description: "그때 샀다면 어땠을지, 거치식과 적립식 결과를 하루 단위 로그로 비교해요.",
};

// 쿼리는 요청마다 다르므로 본문은 클라이언트에서 그린다. 정적 셸에는 로그 모양 스켈레톤만 담긴다.
export default function SimulatePage() {
  return (
    <Suspense fallback={<ResultSkeleton />}>
      <SimulationResultView />
    </Suspense>
  );
}
