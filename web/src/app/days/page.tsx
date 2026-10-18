import type { Metadata } from "next";
import { Suspense } from "react";
import { DayDetailFromUrl, DayDetailSkeleton } from "@/components/day-detail";

export const metadata: Metadata = {
  title: "하루 기록 | 업로그",
  description: "그날 계좌 손익을 종목별로 나누고, 거래와 입출금을 함께 보여드려요.",
};

// /days?date=YYYY-MM-DD. 정적 export라 날짜는 쿼리로 받고, 본문은 Suspense 안에서 클라이언트가 그린다.
export default function DayPage() {
  return (
    <Suspense fallback={<DayDetailSkeleton />}>
      <DayDetailFromUrl />
    </Suspense>
  );
}
