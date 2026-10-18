import type { Metadata } from "next";
import { MockBadge } from "@/components/holding/MockBadge";
import { MonthlyView } from "@/components/insights/monthly/MonthlyView";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "월별 성적표 | UpLog",
  description: "해마다 달마다 계좌 수익률과 손익을 한 표로 보여줘요.",
};

export default function MonthlyPage() {
  return (
    <div>
      <PageHeader
        title="월별 성적표"
        question="어느 달에 벌고 어느 달에 잃었나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <MonthlyView />
    </div>
  );
}
