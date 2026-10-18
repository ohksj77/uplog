import { Signed } from "@/components/result/Signed";
import type { HabitsInsight, HoldingSideStats } from "@/lib/api/portfolio-types";
import { formatPercent } from "@/lib/format";

// 끝난 포지션을 이익/손실로 나눠 보유 일수를 비교한다. 판단 문장은 사실만 말한다.

type Holding = HabitsInsight["holding"];

const times = (v: number) => `${v.toFixed(1)}배`;

export function holdingVerdict({ winners, losers, ratio, verdict }: Holding): { main: string; sub: string } {
  const basis = `끝난 포지션 ${winners.count + losers.count}개 기준이에요. 이익 ${winners.count}개는 평균 ${winners.avgDays}일, 손실 ${losers.count}개는 평균 ${losers.avgDays}일 들고 있었어요.`;
  switch (verdict) {
    case "HOLD_LOSERS":
      return { main: `손실 난 포지션을 이익 난 포지션보다 ${times(ratio ?? 0)} 오래 들고 있었어요.`, sub: basis };
    case "CUT_LOSSES":
      return { main: `이익 난 포지션을 손실 난 포지션보다 ${times(ratio ? 1 / ratio : 0)} 오래 들고 있었어요.`, sub: basis };
    case "BALANCED":
      return { main: "이익 난 포지션과 손실 난 포지션을 비슷한 기간 들고 있었어요.", sub: basis };
    case "INSUFFICIENT":
      return {
        main: "끝난 포지션이 아직 적어서 판단하기 일러요.",
        sub: `지금까지 끝난 포지션은 이익 ${winners.count}개, 손실 ${losers.count}개예요. 양쪽 모두 2개 이상이면 비교해요.`,
      };
  }
}

export function HoldingCompare({ holding }: { holding: Holding }) {
  const { winners, losers } = holding;
  const max = Math.max(winners.avgDays, losers.avgDays, winners.medianDays, losers.medianDays, 1);

  return (
    <section aria-labelledby="habits-holding" className="space-y-3">
      <h2 id="habits-holding" className="text-lg font-semibold">
        이익과 손실, 얼마나 들고 있었나
      </h2>
      <ul className="space-y-4">
        <SideRow label="이익 난 포지션" tone="gain" stats={winners} max={max} />
        <SideRow label="손실 난 포지션" tone="loss" stats={losers} max={max} />
      </ul>
      <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted" aria-hidden>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-4 rounded-[3px] bg-muted/50" /> 평균 보유 일수
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-0.5 bg-foreground" /> 중앙값
        </span>
      </p>
      <p className="text-xs text-muted">
        매수로 보유를 시작해 전량 매도로 끝난 포지션만 셌어요. 포지션 수익률이 0 이하면 손실로 봐요. 일수는 달력일이에요.
      </p>
    </section>
  );
}

function SideRow({ label, tone, stats, max }: { label: string; tone: "gain" | "loss"; stats: HoldingSideStats; max: number }) {
  const pct = (v: number) => `${(v / max) * 100}%`;
  return (
    <li className="space-y-1.5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 text-sm">
        <span className="font-medium">
          {label} <span className="text-muted">{stats.count}개</span>
        </span>
        {stats.count > 0 && (
          <span className="text-xs text-muted tabular-nums">
            평균 수익률{" "}
            <Signed value={stats.avgReturn} className="font-medium">
              {formatPercent(stats.avgReturn, 1)}
            </Signed>
          </span>
        )}
      </div>
      {stats.count === 0 ? (
        <p className="text-sm text-muted">아직 없어요.</p>
      ) : (
        <>
          <div aria-hidden className="relative h-4 rounded-[4px] bg-surface">
            <span
              className={`absolute inset-y-0 left-0 rounded-[4px] ${tone === "gain" ? "bg-gain" : "bg-loss"}`}
              style={{ width: pct(stats.avgDays) }}
            />
            <span className="absolute -inset-y-1 w-0.5 -translate-x-1/2 bg-foreground" style={{ left: pct(stats.medianDays) }} />
          </div>
          <p className="text-sm tabular-nums">
            평균 <span className="font-semibold">{stats.avgDays}일</span>
            <span className="text-muted"> · 중앙값 {stats.medianDays}일</span>
          </p>
        </>
      )}
    </li>
  );
}
