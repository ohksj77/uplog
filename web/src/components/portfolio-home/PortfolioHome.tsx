"use client";

import { useSearchParams } from "next/navigation";
import { AccountLog } from "@/components/portfolio-home/AccountLog";
import { AccountStreaks } from "@/components/portfolio-home/AccountStreaks";
import { AccountSummary } from "@/components/portfolio-home/AccountSummary";
import { HoldingsList } from "@/components/portfolio-home/HoldingsList";
import { InsightsLinkCard } from "@/components/portfolio-home/InsightsLinkCard";
import { LogHeadline } from "@/components/portfolio-home/LogHeadline";
import { YearTabs, yearsBetween } from "@/components/ui/YearTabs";
import { ErrorBlock, LoadingBlock, SampleAccountBadge } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { getHoldings, getPortfolioLog, getPortfolioSummary } from "@/lib/api/portfolio-client";
import { useApi } from "@/lib/use-api";

const HOME_QUESTION = "올해 나는 며칠 벌고 며칠 잃었나";

/** "?year=2025" → 2025. 없거나 이상하면 null(서버 기본 = 마지막 기록 연도). */
export function parseYear(raw: string | null): number | null {
  return raw && /^\d{4}$/.test(raw) ? Number(raw) : null;
}

// 메인: 계좌 요약 → 연도 → 답(며칠 벌고 잃었나) → 로그 → 연속 기록 → 보유 종목 → 분석.
export function PortfolioHome() {
  const year = parseYear(useSearchParams().get("year"));
  const summary = useApi("summary", (s) => getPortfolioSummary(s));
  const log = useApi(`log:${year ?? "latest"}`, (s) => getPortfolioLog(year, s));
  const holdings = useApi("holdings", (s) => getHoldings(s));

  const account = summary.state.status === "success" ? summary.state.data.account : null;
  const lastYear = account ? Number(account.lastDate.slice(0, 4)) : null;
  const years = log.state.status === "success" ? log.state.data.availableYears : account ? yearsBetween(account.firstDate, account.lastDate) : [];
  const shownYear = log.state.status === "success" ? log.state.data.year : (year ?? lastYear);

  return (
    <div className="space-y-10">
      <div>
        <PageHeader title={account?.name ?? "내 계좌"} question={HOME_QUESTION} aside={account?.sample ? <SampleAccountBadge /> : undefined} />
        {summary.state.status === "loading" && <LoadingBlock height={148} label="계좌 요약을 불러오는 중" />}
        {summary.state.status === "error" && <ErrorBlock error={summary.state.error} onRetry={summary.retry} />}
        {summary.state.status === "success" && <AccountSummary summary={summary.state.data} />}
      </div>

      <section aria-labelledby="log-heading" className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="log-heading" className="text-lg font-semibold">
            수익 로그
          </h2>
          {years.length > 1 && <YearTabs years={years} selected={shownYear} />}
        </div>
        {log.state.status === "loading" && <LoadingBlock height={260} label="로그를 불러오는 중" />}
        {log.state.status === "error" && <ErrorBlock error={log.state.error} onRetry={log.retry} />}
        {log.state.status === "success" && (
          <>
            <LogHeadline log={log.state.data} isCurrentYear={lastYear === null || log.state.data.year === lastYear} />
            <AccountLog log={log.state.data} />
            <AccountStreaks streaks={log.state.data.streaks} />
          </>
        )}
      </section>

      <section aria-labelledby="holdings-heading" className="space-y-4">
        <h2 id="holdings-heading" className="text-lg font-semibold">
          현재 보유 종목
        </h2>
        {holdings.state.status === "loading" && <LoadingBlock height={220} label="보유 종목을 불러오는 중" />}
        {holdings.state.status === "error" && <ErrorBlock error={holdings.state.error} onRetry={holdings.retry} />}
        {holdings.state.status === "success" && <HoldingsList holdings={holdings.state.data} />}
      </section>

      <InsightsLinkCard />
    </div>
  );
}
