import type { SalesInsight } from "@/lib/api/portfolio-types";
import { formatSignedWon, formatWon } from "@/lib/format";

export function SalesSummary({ data }: { data: SalesInsight }) {
  const { summary: s, horizon } = data;
  const pending = s.sales - s.judged;
  const net = s.avoidedLoss - s.missedGain;
  return (
    <section aria-label="매도 판단 요약" className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      <div className="col-span-2 rounded-2xl bg-surface p-4 sm:col-span-1">
        <p className="text-sm text-muted">매도 판단 승률</p>
        <p className="mt-1 text-3xl font-bold tabular-nums">{s.judged ? `${Math.round(s.winRate * 100)}%` : "–"}</p>
        <p className="mt-1 text-xs text-muted">
          판정된 {s.judged}건 중 {s.good}건이 판 게 나았어요{pending > 0 && ` · 대기 ${pending}건`}
        </p>
      </div>
      <div className="rounded-2xl bg-surface p-4">
        <p className="text-sm text-muted">피한 손실</p>
        <p className="mt-1 text-lg font-semibold text-gain tabular-nums">{formatWon(s.avoidedLoss)}</p>
      </div>
      <div className="rounded-2xl bg-surface p-4">
        <p className="text-sm text-muted">놓친 이익</p>
        <p className="mt-1 text-lg font-semibold text-loss tabular-nums">{formatWon(s.missedGain)}</p>
      </div>
      <p className="col-span-2 text-sm text-muted sm:col-span-3">
        매도 {horizon}거래일 뒤 종가가 매도가 이하면 판 게 나았다고 봐요. 피한 손실과 놓친 이익을 합치면{" "}
        <span className={`font-medium tabular-nums ${net > 0 ? "text-gain" : net < 0 ? "text-loss" : ""}`}>{formatSignedWon(net)}</span>
        이에요.
      </p>
    </section>
  );
}
