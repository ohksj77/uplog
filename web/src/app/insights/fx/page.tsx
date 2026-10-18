import type { Metadata } from "next";
import { MockBadge } from "@/components/holding/MockBadge";
import { FxView } from "@/components/insights/fx";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "수익 분해 | 업로그",
  description: "해외 종목 수익을 주가 효과와 환율 효과로 나눠 보여 드려요.",
};

export default function FxInsightPage() {
  return (
    <div>
      <PageHeader
        title="수익 분해"
        question="이 수익은 종목 덕인가 환율 덕인가"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <FxView />
    </div>
  );
}
