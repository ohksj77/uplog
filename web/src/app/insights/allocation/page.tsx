import type { Metadata } from "next";
import { MockBadge } from "@/components/holding/MockBadge";
import { AllocationView } from "@/components/insights/allocation/AllocationView";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "자산 구성 변화 | 업로그",
  description: "시기별로 내 돈이 어느 종목과 국내·해외·현금에 있었는지 쌓아서 보여 드려요.",
};

export default function AllocationPage() {
  return (
    <div>
      <PageHeader
        title="자산 구성 변화"
        question="내 돈이 시기별로 어디에 몰려 있었나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <AllocationView />
    </div>
  );
}
