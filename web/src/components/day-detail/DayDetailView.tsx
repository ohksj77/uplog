"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Contributions } from "@/components/day-detail/Contributions";
import { CashFlows } from "@/components/day-detail/CashFlows";
import { DayDetailSkeleton } from "@/components/day-detail/DayDetailSkeleton";
import { DayNav } from "@/components/day-detail/DayNav";
import { DaySummary } from "@/components/day-detail/DaySummary";
import { TradesTable } from "@/components/day-detail/TradesTable";
import { formatLogDate } from "@/components/log-grid/logic";
import { ErrorBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { getPortfolioDay } from "@/lib/api/portfolio-client";
import { useApi } from "@/lib/use-api";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** /days?date=YYYY-MM-DD. 쿼리는 요청마다 달라서 페이지가 Suspense로 감싼다. */
export function DayDetailFromUrl() {
  const date = useSearchParams().get("date") ?? "";
  return <DayDetailView date={date} />;
}

/** 하루 상세. 날짜 형식이 틀리면 부르지 않고 바로 "기록 없음"을 보여준다. */
export function DayDetailView({ date }: { date: string }) {
  const valid = ISO_DATE.test(date) && !Number.isNaN(Date.parse(`${date}T00:00:00Z`));
  const { state, retry } = useApi(valid ? `day:${date}` : null, (s) => getPortfolioDay(date, s));
  const back = { href: valid ? `/?year=${date.slice(0, 4)}` : "/", label: "수익 로그" };
  const title = valid ? formatLogDate(date) : "하루 기록";

  if (!valid) return <NotFound title={title} back={back} />;
  if (state.status === "loading") return <DayDetailSkeleton />;
  if (state.status === "error") {
    const notFound = state.error.status === 404 && state.error.code !== "ACCOUNT_NOT_CONNECTED";
    if (notFound) return <NotFound title={title} back={back} />;
    return (
      <div>
        <PageHeader title={title} back={back} />
        <ErrorBlock error={state.error} onRetry={retry} />
      </div>
    );
  }

  const day = state.data;
  return (
    <div className="space-y-8">
      <div>
        <PageHeader title={title} back={back} />
        <DayNav prevDate={day.prevDate} nextDate={day.nextDate} />
      </div>
      <DaySummary day={day} />
      <Contributions day={day} />
      <TradesTable trades={day.trades} />
      <CashFlows items={day.cashFlows} />
    </div>
  );
}

function NotFound({ title, back }: { title: string; back: { href: string; label: string } }) {
  return (
    <div>
      <PageHeader title={title} back={back} />
      <section role="alert" className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">이날은 기록이 없어요</p>
        <p className="mt-2 text-sm text-muted break-keep">장이 열리지 않은 날이거나, 계좌 정보를 받지 못한 날이에요.</p>
        <Link href={back.href} className="mt-6 inline-flex h-11 items-center rounded-xl bg-accent px-5 text-sm font-semibold text-white">
          로그로 돌아가기
        </Link>
      </section>
    </div>
  );
}
