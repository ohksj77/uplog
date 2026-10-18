import Link from "next/link";
import type { PortfolioLog } from "@/lib/api/portfolio-types";
import { formatDate, formatSignedWon } from "@/lib/format";
import { Signed } from "@/components/result/Signed";
import { dayHref } from "@/lib/routes";

/** 화면의 답: 올해 며칠 벌고 며칠 잃었나 + 합계·최고·최악의 날. */
export function LogHeadline({ log, isCurrentYear }: { log: PortfolioLog; isCurrentYear: boolean }) {
  const { gainDays, lossDays, flatDays, totalPnl, bestDay, worstDay } = log.stats;
  const missing = log.days.filter((d) => d.source === "MISSING").length;
  const when = isCurrentYear ? "올해" : `${log.year}년에는`;

  return (
    <div className="space-y-3">
      <p className="text-2xl font-bold tracking-tight break-keep">
        {when} <span className="text-gain">{gainDays}일 벌고</span> <span className="text-loss">{lossDays}일 잃었어요</span>
      </p>
      <p className="text-sm text-muted tabular-nums">
        합계{" "}
        <Signed value={totalPnl} className="font-semibold">
          {formatSignedWon(totalPnl)}
        </Signed>
        {flatDays > 0 && <> · 변동 없는 날 {flatDays}일</>}
        {missing > 0 && <> · 기록 없음 {missing}일</>}
        <span className="ml-1">({formatDate(log.from)} ~ {formatDate(log.to)})</span>
      </p>
      {(bestDay || worstDay) && (
        <div className="grid grid-cols-2 gap-3">
          {bestDay && <DayLink title="가장 많이 번 날" date={bestDay.date} pnl={bestDay.pnl} />}
          {worstDay && <DayLink title="가장 많이 잃은 날" date={worstDay.date} pnl={worstDay.pnl} />}
        </div>
      )}
    </div>
  );
}

function DayLink({ title, date, pnl }: { title: string; date: string; pnl: number }) {
  return (
    <Link
      href={dayHref(date)}
      className="block rounded-2xl bg-surface p-4 transition-colors hover:bg-border focus-visible:outline-2 focus-visible:outline-accent"
    >
      <span className="block text-xs text-muted">{title}</span>
      <Signed value={pnl} className="mt-1 block text-base font-semibold tabular-nums sm:text-lg">
        {formatSignedWon(pnl)}
      </Signed>
      <span className="block text-xs text-muted tabular-nums">{formatDate(date)} →</span>
    </Link>
  );
}
