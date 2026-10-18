import { formatRate, formatWonTenth } from "@/components/insights/shared/money";
import type { DividendsInsight } from "@/lib/api/portfolio-types";

/** 최근 12개월 합·한 달 평균·평가금 대비 비율을 한 문장으로. */
export function DividendHeadline({ data }: { data: DividendsInsight }) {
  const { trailing12m, trailingYield, total } = data;
  return (
    <section aria-label="요약" className="rounded-2xl bg-surface p-5">
      {trailing12m > 0 ? (
        <p className="text-lg leading-snug font-semibold break-keep">
          최근 12개월 배당 {formatWonTenth(trailing12m)}, 한 달 평균{" "}
          {formatWonTenth(trailing12m / 12)}이에요. 계좌 평가금의 {formatRate(trailingYield)}예요.
        </p>
      ) : (
        <p className="text-lg leading-snug font-semibold break-keep">최근 12개월에는 들어온 배당이 없어요.</p>
      )}
      <p className="mt-2 text-sm text-muted break-keep">기록 전체로는 배당을 {formatWonTenth(total)} 받았어요.</p>
    </section>
  );
}
