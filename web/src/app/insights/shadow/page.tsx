import type { Metadata } from "next";
import { Suspense } from "react";
import { ShadowView } from "@/components/insights/shadow/ShadowView";
import { LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = {
  title: "그림자 계좌 | 업로그",
  description: "실제 입금 날짜와 금액 그대로 지수에 넣었다면 지금 얼마일지 내 계좌와 비교해요.",
};

// 지수 선택은 URL 쿼리에 있어 본문은 클라이언트에서 그린다.
export default function ShadowPage() {
  return (
    <div>
      <PageHeader
        title="그림자 계좌"
        question="종목 고르기가 지수보다 나았나"
        back={{ href: "/insights", label: "분석" }}
      />
      <Suspense fallback={<LoadingBlock height={520} />}>
        <ShadowView />
      </Suspense>
    </div>
  );
}
