import { MonthColumns, type MonthColumn } from "@/components/insights/shared/MonthColumns";
import { formatMonth } from "@/components/insights/shared/money";
import type { DividendsInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatWon } from "@/lib/format";

// 월별 배당 막대. 배당은 손익 방향이 아니라 들어온 돈이라 중립 색(청록) 한 가지로 그린다.

type Month = DividendsInsight["months"][number];

const TRAILING = 12;

export function DividendMonths({ months }: { months: Month[] }) {
  const columns: MonthColumn[] = months.map((m, i) => {
    const mm = Number(m.month.slice(5, 7));
    const items = m.items.map((it) => `${it.instrument.name} ${formatWon(it.amount)}`).join(", ");
    return {
      key: m.month,
      label: String(mm),
      sub: i === 0 || mm === 1 ? m.month.slice(0, 4) : undefined,
      segments: [{ key: "amount", value: m.amount, className: "bg-series-lump" }],
      dim: i < months.length - TRAILING,
      srText: `${formatMonth(m.month)} ${m.amount > 0 ? `${formatWon(m.amount)}: ${items}` : "배당 없음"}`,
    };
  });

  return (
    <section aria-labelledby="div-months" className="space-y-3">
      <div>
        <h2 id="div-months" className="text-lg font-semibold">
          달마다 들어온 배당
        </h2>
        <p className="mt-1 text-xs text-muted">세후 원화 기준이에요. 진한 막대가 최근 12개월이에요.</p>
      </div>
      <MonthColumns
        columns={columns}
        title="월별 배당 금액"
        hint="막대에 마우스를 올리거나 누르면, 또는 Tab으로 옮기면 그달 배당 내역을 보여줘요."
        detail={(i) => <MonthDetail month={months[i]} />}
      />
    </section>
  );
}

function MonthDetail({ month }: { month: Month }) {
  return (
    <>
      <p>
        <span className="font-medium text-foreground">{formatMonth(month.month)}</span>{" "}
        {month.amount > 0 ? formatWon(month.amount) : "배당 없음"}
      </p>
      {month.items.length > 0 && (
        <ul className="mt-1 space-y-0.5">
          {month.items.map((it) => (
            <li key={`${it.date}-${it.instrument.code}`} className="flex justify-between gap-3">
              <span className="min-w-0 truncate">
                {formatDate(it.date).slice(5)} {it.instrument.name}
              </span>
              <span className="shrink-0">{formatWon(it.amount)}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
