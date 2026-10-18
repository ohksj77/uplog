import type { SimulationResult, Streak, StreakStats, StreakUnit } from "@/lib/api/types";
import { formatDate } from "@/lib/format";

type StreakMetricsProps = {
  results: { lumpSum: SimulationResult; recurring: SimulationResult };
};

const UNIT_TEXT: Record<StreakUnit, string> = { DAY: "거래일", WEEK: "주", MONTH: "개월" };

/**
 * 연속 기록. 상승·하락은 같은 ETF의 일별 수익률이라 두 방식이 사실상 같으므로 기간 전체를 덮는 거치식 기준 한 번만 보여 준다.
 * 적립 연속은 적립식에서 가져온다.
 */
export function StreakMetrics({ results }: StreakMetricsProps) {
  const { gain, loss } = results.lumpSum.streaks;
  const purchase = results.recurring.streaks.purchase;

  return (
    <section aria-labelledby="streak-heading" className="space-y-3">
      <h2 id="streak-heading" className="text-lg font-semibold">
        연속 기록
      </h2>
      <div className="grid gap-3 sm:grid-cols-3">
        <StreakCard title="최장 연속 상승" tone="gain" stats={gain} unit="DAY" verb="상승" />
        <StreakCard title="최장 연속 하락" tone="loss" stats={loss} unit="DAY" verb="하락" />
        {purchase && <StreakCard title="연속 적립" tone="flat" stats={purchase} unit={purchase.unit} verb="적립" />}
      </div>
    </section>
  );
}

type StreakCardProps = {
  title: string;
  tone: "gain" | "loss" | "flat";
  stats: StreakStats;
  unit: StreakUnit;
  verb: string;
};

const TONE_CLASS = { gain: "text-gain", loss: "text-loss", flat: "text-foreground" } as const;

function StreakCard({ title, tone, stats, unit, verb }: StreakCardProps) {
  const { longest, current } = stats;
  return (
    <article className="rounded-2xl bg-surface p-4">
      <h3 className="text-xs text-muted">{title}</h3>
      {longest ? (
        <>
          <p className={`mt-1 text-xl font-semibold tabular-nums ${TONE_CLASS[tone]}`}>
            {longest.length}
            {UNIT_TEXT[unit]} 연속
          </p>
          <p className="text-xs text-muted tabular-nums">{range(longest)}</p>
        </>
      ) : (
        <p className="mt-1 text-base font-semibold">기록 없음</p>
      )}
      <p className="mt-3 text-sm tabular-nums">
        {current ? (
          <>
            지금 <strong className="font-semibold">{current.length}{UNIT_TEXT[unit]}째</strong> {verb} 중
          </>
        ) : (
          <span className="text-muted">지금은 이어지는 {verb} 기록이 없어요</span>
        )}
      </p>
    </article>
  );
}

function range(s: Streak): string {
  return s.start === s.end ? formatDate(s.start) : `${formatDate(s.start)} → ${formatDate(s.end)}`;
}
