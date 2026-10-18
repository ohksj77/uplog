import type { Metadata } from "next";
import { MockBadge } from "@/components/holding/MockBadge";
import { HabitsView } from "@/components/insights/habits/HabitsView";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "매매 습관 | UpLog",
  description: "이익과 손실 포지션의 보유 기간, 물타기 결과, 달마다 사고판 횟수를 보여줘요.",
};

export default function HabitsPage() {
  return (
    <div>
      <PageHeader
        title="매매 습관"
        question="수익은 빨리 팔고 손실은 오래 버티나"
        back={{ href: "/insights", label: "분석" }}
        aside={<MockBadge />}
      />
      <HabitsView />
    </div>
  );
}
