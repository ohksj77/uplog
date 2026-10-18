import type { ReactNode } from "react";
import type { PortfolioSummary } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatSignedWon, formatWon, toneOf } from "@/lib/format";

const TONE_CLASS = { gain: "text-gain", loss: "text-loss", flat: "text-foreground" } as const;

/** 계좌 숫자 요약: 총 평가금액, 순입금, 손익, TWR, 오늘. */
export function AccountSummary({ summary }: { summary: PortfolioSummary }) {
  const { totalValue, netDeposits, profit, twr, todayPnl, todayReturn, asOf } = summary;
  return (
    <div className="space-y-2">
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <Stat label="총 평가금액" className="col-span-2 sm:col-span-1">
          {formatWon(totalValue)}
        </Stat>
        <Stat label="순입금">{formatWon(netDeposits)}</Stat>
        <Stat label="손익" tone={toneOf(profit)}>
          {formatSignedWon(profit)}
        </Stat>
        <Stat label="수익률(TWR)" tone={toneOf(twr)} hint="입출금 영향을 뺀 시간가중 수익률">
          {formatPercent(twr)}
        </Stat>
        <Stat label="오늘" tone={toneOf(todayPnl)} sub={formatPercent(todayReturn)}>
          {formatSignedWon(todayPnl)}
        </Stat>
      </dl>
      <p className="text-xs text-muted tabular-nums">{formatDate(asOf)} 장 마감 기준</p>
    </div>
  );
}

type StatProps = {
  label: string;
  children: ReactNode;
  tone?: keyof typeof TONE_CLASS;
  sub?: string;
  hint?: string;
  className?: string;
};

function Stat({ label, children, tone = "flat", sub, hint, className = "" }: StatProps) {
  return (
    <div className={`rounded-2xl bg-surface p-4 ${className}`}>
      <dt className="text-xs text-muted" title={hint}>
        {label}
      </dt>
      <dd className={`mt-1 text-base font-semibold tabular-nums break-all sm:text-lg ${TONE_CLASS[tone]}`}>
        {children}
        {sub && <span className="ml-1.5 text-sm font-normal">{sub}</span>}
      </dd>
    </div>
  );
}
