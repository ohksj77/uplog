import type { Basis } from "@/components/insights/underwater/UnderwaterView";
import type { UnderwaterInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatWon } from "@/lib/format";

/** 화면이 답하는 질문에 대한 한 줄 답. 수익률 기준은 current를 그대로 쓴다. */
export function UnderwaterHeadline({ data, basis }: { data: UnderwaterInsight; basis: Basis }) {
  const { current } = data;
  const lines = basis === "rate" ? rateLines(current) : amountLines(data);
  const tone = lines.deeper ? "text-loss" : "text-foreground";

  return (
    <section aria-label="요약" className="rounded-2xl bg-surface p-5">
      <p className={`text-lg leading-snug font-semibold break-keep ${tone}`}>{lines.main}</p>
      {lines.sub && <p className="mt-2 text-sm text-muted break-keep">{lines.sub}</p>}
    </section>
  );
}

type Lines = { main: string; sub?: string; deeper?: boolean };

function rateLines(current: UnderwaterInsight["current"]): Lines {
  const past = formatPercent(current.deepestPast, 1);
  if (current.drawdown >= 0) {
    return {
      main: "지금은 고점이에요.",
      sub: current.deepestPast < 0 ? `지금까지 가장 깊었던 하락은 ${past}였어요.` : "아직 고점 아래로 내려간 적이 없어요.",
    };
  }
  const now = `지금은 고점 대비 ${formatPercent(current.drawdown, 1)}, ${current.days}일째예요.`;
  const compare =
    current.deepestPast === 0
      ? "처음 겪는 하락이에요."
      : current.deeperThanPast
        ? `과거 가장 깊었던 ${past}보다 깊어요.`
        : `과거 가장 깊었던 ${past}보다 얕아요.`;
  return {
    main: `${now} ${compare}`,
    sub: `${formatDate(current.peak)} 고점에서 내려온 뒤 아직 회복하지 못했어요.`,
    deeper: current.deeperThanPast,
  };
}

function amountLines({ series, current }: UnderwaterInsight): Lines {
  const now = series[series.length - 1]?.drawdownAmount ?? 0;
  // 금액 기준 과거 최저: 지금 하락 구간(수익률 기준 고점)보다 앞선 날들 중 가장 작은 값
  const before = series.filter((p) => current.drawdown >= 0 || p.date < current.peak);
  const past = Math.min(0, ...before.map((p) => p.drawdownAmount));
  if (now >= 0) {
    return {
      main: "지금 평가손익은 고점이에요.",
      sub: past < 0 ? `지금까지 가장 크게 줄었을 때는 고점보다 ${formatWon(-past)} 적었어요.` : undefined,
    };
  }
  const compare =
    past === 0
      ? "처음 겪는 하락이에요."
      : `과거 가장 컸던 ${formatWon(-past)}보다 ${now < past ? "커요" : "작아요"}.`;
  return {
    main: `지금 평가손익은 고점보다 ${formatWon(-now)} 적어요. ${compare}`,
    sub: "금액 기준은 계좌가 커질수록 같은 비율의 하락도 크게 나와요.",
    deeper: past < 0 && now < past,
  };
}
