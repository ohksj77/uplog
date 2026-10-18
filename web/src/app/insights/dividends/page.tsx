import type { Metadata } from "next";
import { MockBadge } from "@/components/holding/MockBadge";
import { DividendsView } from "@/components/insights/dividends/DividendsView";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "배당 흐름 | 업로그",
  description: "달마다 들어온 배당과 최근 12개월 배당을 계좌 평가금과 견줘 보여 드려요.",
};

export default function DividendsPage() {
  return (
    <div>
      <PageHeader
        title="배당 흐름"
        question="매달 배당이 얼마 들어오나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <DividendsView />
    </div>
  );
}
