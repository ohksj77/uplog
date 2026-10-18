import type { PortfolioLog } from "@/lib/api/portfolio-types";
import type { Streak, StreakStats } from "@/lib/api/types";
import { formatDate } from "@/lib/format";

// 문구는 시뮬레이션 결과의 연속 기록(StreakMetrics)과 맞춘다.

const TONE_CLASS = { gain: "text-gain", loss: "text-loss" } as const;

export function AccountStreaks({ streaks }: { streaks: PortfolioLog["streaks"] }) {
  return (
    <section aria-labelledby="streak-heading" className="space-y-3">
      <h3 id="streak-heading" className="text-base font-semibold">
        연속 기록
      </h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <StreakCard title="최장 연속 이익" tone="gain" stats={streaks.gain} verb="이익" />
        <StreakCard title="최장 연속 손실" tone="loss" stats={streaks.loss} verb="손실" />
      </div>
    </section>
  );
}

function StreakCard({ title, tone, stats, verb }: { title: string; tone: "gain" | "loss"; stats: StreakStats; verb: string }) {
  const { longest, current } = stats;
  return (
    <article className="rounded-2xl bg-surface p-4">
      <h4 className="text-xs text-muted">{title}</h4>
      {longest ? (
        <>
          <p className={`mt-1 text-xl font-semibold tabular-nums ${TONE_CLASS[tone]}`}>{longest.length}거래일 연속</p>
          <p className="text-xs text-muted tabular-nums">{range(longest)}</p>
        </>
      ) : (
        <p className="mt-1 text-base font-semibold">기록 없음</p>
      )}
      <p className="mt-3 text-sm tabular-nums">
        {current ? (
          <>
            지금 <strong className="font-semibold">{current.length}거래일째</strong> {verb} 중
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
