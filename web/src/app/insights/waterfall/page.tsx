import type { Metadata } from "next";
import { Suspense } from "react";
import { WaterfallView } from "@/components/insights/waterfall/WaterfallView";
import { LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "손익 폭포수 | UpLog",
  description: "기간 손익을 종목별·수수료·세금·환율 기여로 쌓아 내려가며 보여줘요.",
};

// 기간 선택은 URL 쿼리에 있어 본문은 클라이언트에서 그린다.
export default function WaterfallPage() {
  return (
    <div>
      <PageHeader
        title="손익 폭포수"
        question="누가 내 수익을 만들고 깎았나"
        back={{ href: "/insights", label: "분석" }}
      />
      <Suspense fallback={<LoadingBlock height={560} />}>
        <WaterfallView />
      </Suspense>
    </div>
  );
}
