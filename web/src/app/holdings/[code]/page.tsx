import type { Metadata } from "next";
import { Suspense } from "react";
import { HoldingDetailView } from "@/components/holding";
import { LoadingBlock } from "@/components/ui/AsyncState";

export const metadata: Metadata = {
  title: "종목 상세 | 업로그",
  description: "한 종목의 보유 현황, 사고판 시점, 판 뒤의 주가를 모아 봐요.",
};

// 종목 코드는 요청 때 정해지므로 Suspense 안에서 꺼낸다(cacheComponents).
export default function HoldingPage({ params }: PageProps<"/holdings/[code]">) {
  return (
    <Suspense fallback={<LoadingBlock height={520} />}>
      {params.then(({ code }) => (
        <HoldingDetailView code={code} />
      ))}
    </Suspense>
  );
}
