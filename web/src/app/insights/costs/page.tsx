import type { Metadata } from "next";
import { Suspense } from "react";
import { MockBadge } from "@/components/holding/MockBadge";
import { CostsView } from "@/components/insights/costs/CostsView";
import { LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "숨은 비용 | 업로그",
  description: "한 해 동안 수수료와 세금으로 나간 돈을 달별·종목별로 보여 드려요.",
};

// 연도(?year=)는 URL 쿼리에 있어 본문은 클라이언트에서 그린다.
export default function CostsPage() {
  return (
    <div>
      <PageHeader
        title="숨은 비용"
        question="올해 비용으로 얼마 나갔나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <Suspense fallback={<LoadingBlock height={560} />}>
        <CostsView />
      </Suspense>
    </div>
  );
}
