import type { MonthlyInsight } from "@/lib/api/portfolio-types";
import { formatPercent } from "@/lib/format";

// 화면이 답하는 질문에 대한 한 줄 답. 사실만 말한다.

const monthName = (m: { year: number; month: number }) => `${m.year}년 ${m.month}월`;

export function monthlyHeadline(data: MonthlyInsight): { main: string; sub?: string } {
  const recorded = data.years.reduce((n, y) => n + y.months.filter((m) => m.return != null).length, 0);
  if (recorded === 0) return { main: "아직 한 달 치 기록도 없어요." };
  const { positiveMonths: pos, negativeMonths: neg, best, worst } = data;
  const flat = recorded - pos - neg;
  const main =
    `${recorded}개월 중 ${pos}개월 벌고 ${neg}개월 잃었어요.` + (flat > 0 ? ` 수익률이 0인 달은 ${flat}개월이에요.` : "");
  const subs: string[] = [];
  if (best && best.return > 0) subs.push(`가장 좋았던 달은 ${monthName(best)}(${formatPercent(best.return, 1)})`);
  if (worst && worst.return < 0) subs.push(`가장 나빴던 달은 ${monthName(worst)}(${formatPercent(worst.return, 1)})`);
  return { main, sub: subs.length ? `${subs.join(", ")}이에요.` : undefined };
}

export function MonthlyHeadline({ data }: { data: MonthlyInsight }) {
  const { main, sub } = monthlyHeadline(data);
  return (
    <section aria-label="요약" className="rounded-2xl bg-surface p-5">
      <p className="text-lg leading-snug font-semibold break-keep">{main}</p>
      {sub && <p className="mt-2 text-sm text-muted break-keep">{sub}</p>}
    </section>
  );
}
