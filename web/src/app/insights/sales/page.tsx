import type { Metadata } from "next";
import { Suspense } from "react";
import { MockBadge } from "@/components/holding/MockBadge";
import { SalesView } from "@/components/insights/sales";
import { LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "판 뒤의 그림자 | 업로그",
  description: "매도한 종목의 매도 뒤 주가를 이어 그려 매도 판단을 돌아봐요.",
};

// 판정 기준이 URL 쿼리라 본문은 클라이언트에서 그린다.
export default function SalesInsightPage() {
  return (
    <div>
      <PageHeader
        title="판 뒤의 그림자"
        question="그때 판 게 잘한 일이었나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <Suspense fallback={<LoadingBlock height={480} />}>
        <SalesView />
      </Suspense>
    </div>
  );
}
