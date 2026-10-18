import type { Instrument, WaterfallInsight, WaterfallKind } from "@/lib/api/portfolio-types";

// 폭포수 막대 계산. 0에서 출발해 항목마다 누적하고, 마지막에 합계 막대를 둔다.

export type WaterfallItem = WaterfallInsight["items"][number];

export type WaterfallStep = {
  key: string;
  label: string;
  kind: WaterfallKind | "GROUP" | "TOTAL";
  instrument: Instrument | null;
  amount: number;
  /** 누적 시작·끝(원). 합계 막대는 0 → total. */
  start: number;
  end: number;
  /** "그 외 N종목"에 묶인 항목 */
  members: WaterfallItem[];
};

export const KIND_LABEL: Record<WaterfallKind, string> = {
  INSTRUMENT: "종목",
  DIVIDEND: "배당",
  FEE: "수수료",
  TAX: "세금",
  FX: "환율",
  OTHER: "기타",
};

/** 종목은 영향이 큰 keep개만 따로 두고 나머지는 한 줄로 묶는다. 합은 그대로다. */
export function buildSteps(items: WaterfallItem[], total: number, keep = 6): WaterfallStep[] {
  const instruments = items.filter((it) => it.kind === "INSTRUMENT");
  const others = items.filter((it) => it.kind !== "INSTRUMENT");
  const byImpact = [...instruments].sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount));
  // 하나만 남으면 묶지 않는다
  const kept = new Set(byImpact.length > keep + 1 ? byImpact.slice(0, keep) : byImpact);
  const rest = instruments.filter((it) => !kept.has(it));

  const rows: Omit<WaterfallStep, "start" | "end">[] = instruments
    .filter((it) => kept.has(it))
    .map((it, i) => ({ ...it, key: it.instrument?.code ?? `i-${i}`, members: [] }));
  if (rest.length > 0) {
    rows.push({
      key: "group",
      label: `그 외 ${rest.length}종목`,
      kind: "GROUP",
      instrument: null,
      amount: rest.reduce((s, it) => s + it.amount, 0),
      members: rest,
    });
  }
  others.forEach((it) => rows.push({ ...it, key: it.kind, members: [] }));

  let acc = 0;
  const steps: WaterfallStep[] = rows.map((r) => {
    const start = acc;
    acc += r.amount;
    return { ...r, start, end: acc };
  });
  steps.push({ key: "total", label: "기간 손익", kind: "TOTAL", instrument: null, amount: total, start: 0, end: total, members: [] });
  return steps;
}
