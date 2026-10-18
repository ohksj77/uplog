import { formatManShort, formatRate, formatWonTenth } from "@/components/insights/shared/money";
import type { CostsInsight } from "@/lib/api/portfolio-types";

/** 그해 비용 합과 내역, 비용 전 수익 대비 비율. costShare가 null이면 비용 전에도 수익이 없었다는 뜻이다. */
export function CostHeadline({ data, isLatestYear }: { data: CostsInsight; isLatestYear: boolean }) {
  const { total, costShare, profitBeforeCost } = data;
  const when = isLatestYear ? "올해" : `${data.year}년에`;

  if (total.cost === 0) {
    return (
      <section aria-label="요약" className="rounded-2xl bg-surface p-5">
        <p className="text-lg leading-snug font-semibold break-keep">{when}는 체결이 없어 비용이 나가지 않았어요.</p>
      </section>
    );
  }

  return (
    <section aria-label="요약" className="rounded-2xl bg-surface p-5">
      <p className="text-lg leading-snug font-semibold break-keep">
        {when} 비용으로 {formatWonTenth(total.cost)} 나갔어요
        <span className="font-normal text-muted">
          (수수료 {formatManShort(total.fee)} · 세금 {formatManShort(total.tax)})
        </span>
        .
      </p>
      <p className="mt-2 text-sm text-muted break-keep">
        {costShare != null
          ? `비용을 빼기 전 수익(${formatWonTenth(profitBeforeCost)})의 ${formatRate(costShare, 1)}예요.`
          : "이 기간에는 비용을 빼기 전에도 수익이 나지 않아, 비용이 수익의 몇 %인지는 따지지 않았어요."}
      </p>
    </section>
  );
}
