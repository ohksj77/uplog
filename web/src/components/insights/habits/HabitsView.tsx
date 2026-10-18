"use client";

import { ActivityChart } from "@/components/insights/habits/ActivityChart";
import { AveragingDown } from "@/components/insights/habits/AveragingDown";
import { HoldingCompare, holdingVerdict } from "@/components/insights/habits/HoldingCompare";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getHabitsInsight } from "@/lib/api/portfolio-client";
import { useApi } from "@/lib/use-api";

export function HabitsView() {
  const { state, retry } = useApi("habits", (signal) => getHabitsInsight(signal));
  if (state.status === "loading") return <LoadingBlock height={520} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;

  const { holding, averagingDown, activity } = state.data;
  const verdict = holdingVerdict(holding);

  return (
    <div className="space-y-10">
      <section aria-label="요약" className="rounded-2xl bg-surface p-5">
        <p className="text-lg leading-snug font-semibold break-keep">{verdict.main}</p>
        <p className="mt-2 text-sm text-muted break-keep">{verdict.sub}</p>
      </section>
      <HoldingCompare holding={holding} />
      <AveragingDown data={averagingDown} />
      <ActivityChart activity={activity} />
    </div>
  );
}
