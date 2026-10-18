import { MonthColumns, type MonthColumn } from "@/components/insights/shared/MonthColumns";
import type { CostsInsight } from "@/lib/api/portfolio-types";
import { formatWon } from "@/lib/format";

// 월별 비용 막대. 아래 수수료(호박), 위 세금(회색). 비용은 손익 방향이 아니라 빨강·파랑을 쓰지 않는다.

export const COST_PARTS = [
  { key: "fee", label: "수수료", className: "bg-series-recurring" },
  { key: "tax", label: "세금", className: "bg-muted" },
] as const;

type Month = CostsInsight["months"][number];

export function CostMonths({ year, months }: { year: number; months: Month[] }) {
  const columns: MonthColumn[] = months.map((m) => {
    const mm = Number(m.month.slice(5, 7));
    return {
      key: m.month,
      label: String(mm),
      segments: COST_PARTS.map((p) => ({ key: p.key, value: m[p.key], className: p.className })),
      srText: `${mm}월 수수료 ${formatWon(m.fee)}, 세금 ${formatWon(m.tax)}`,
    };
  });

  return (
    <section aria-labelledby="cost-months" className="space-y-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="cost-months" className="text-lg font-semibold">
          달마다 나간 비용
        </h2>
        <ul aria-hidden className="flex gap-3 text-xs text-muted">
          {COST_PARTS.map((p) => (
            <li key={p.key} className="flex items-center gap-1.5">
              <span className={`inline-block h-2.5 w-2.5 rounded-sm ${p.className}`} />
              {p.label}
            </li>
          ))}
        </ul>
      </div>
      <MonthColumns
        columns={columns}
        title={`${year}년 월별 비용`}
        minColWidth={22}
        height={140}
        hint="막대에 마우스를 올리거나 누르면, 또는 Tab으로 옮기면 그달 수수료와 세금을 보여줘요."
        detail={(i) => <MonthDetail month={months[i]} />}
      />
    </section>
  );
}

function MonthDetail({ month }: { month: Month }) {
  const cost = month.fee + month.tax;
  return (
    <>
      <p>
        <span className="font-medium text-foreground">
          {month.month.slice(0, 4)}년 {Number(month.month.slice(5, 7))}월
        </span>{" "}
        {cost > 0 ? `비용 ${formatWon(cost)}` : "비용 없음"}
      </p>
      {cost > 0 && (
        <p className="mt-1">
          수수료 {formatWon(month.fee)} · 세금 {formatWon(month.tax)}
        </p>
      )}
    </>
  );
}
