import type { Metadata } from "next";
import { UnderwaterView } from "@/components/insights/underwater/UnderwaterView";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "수중 차트 | UpLog",
  description: "계좌가 직전 고점 대비 얼마나 빠져 있었는지와 회복까지 걸린 기간을 보여줘요.",
};

export default function UnderwaterPage() {
  return (
    <div>
      <PageHeader
        title="수중 차트"
        question="지금 하락은 전에 버틴 것보다 큰가"
        back={{ href: "/insights", label: "분석" }}
      />
      <UnderwaterView />
    </div>
  );
}
