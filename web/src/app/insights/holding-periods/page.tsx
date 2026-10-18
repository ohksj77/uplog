import type { Metadata } from "next";
import { MockBadge } from "@/components/holding/MockBadge";
import { HoldingPeriodsView } from "@/components/insights/holding-periods";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "보유 기간 타임라인 | 업로그",
  description: "포지션마다 매수부터 매도까지를 막대로 그리고, 보유 기간별 성적을 비교해요.",
};

export default function HoldingPeriodsPage() {
  return (
    <div>
      <PageHeader
        title="보유 기간 타임라인"
        question="단타와 장투 중 뭘 잘하나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <HoldingPeriodsView />
    </div>
  );
}
