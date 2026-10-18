import type { Metadata } from "next";
import { Suspense } from "react";
import { HomeSkeleton, PortfolioHome } from "@/components/portfolio-home";

export const metadata: Metadata = {
  title: "수익 로그 | UpLog",
  description: "하루하루 계좌 손익을 로그로 쌓아 올해 며칠 벌고 며칠 잃었는지 보여드려요.",
};

// 연도(?year=)는 요청마다 다르므로 본문은 클라이언트에서 그린다. 정적 셸에는 스켈레톤만 담긴다.
export default function Home() {
  return (
    <Suspense fallback={<HomeSkeleton />}>
      <PortfolioHome />
    </Suspense>
  );
}
