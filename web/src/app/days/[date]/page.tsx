import type { Metadata } from "next";
import { Suspense } from "react";
import { DayDetailSkeleton, DayDetailView } from "@/components/day-detail";

export const metadata: Metadata = {
  title: "하루 기록 | 업로그",
  description: "그날 계좌 손익을 종목별로 나누고, 거래와 입출금을 함께 보여드려요.",
};

// 날짜는 요청 때 정해진다(generateStaticParams 없음). params 접근은 Suspense 안에서.
export default function DayPage({ params }: PageProps<"/days/[date]">) {
  return <Suspense fallback={<DayDetailSkeleton />}>{params.then(({ date }) => <DayDetailView date={date} />)}</Suspense>;
}
