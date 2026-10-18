import { ApiError } from "@/lib/api/client";
import type { IsoDate } from "@/lib/api/types";
import type {
  FxInsight,
  FxItem,
  HoldingBucketKey,
  HoldingDetail,
  HoldingPeriod,
  HoldingPeriodsInsight,
  Instrument,
  Sale,
  SaleHorizon,
  SalesInsight,
  Trade,
} from "@/lib/api/portfolio-types";
import { INSTRUMENTS, MOCK_LAST_DATE, closeOn, priceSeries, rng, weekdays } from "@/lib/api/mock/portfolio/fixtures";

// 종목 상세·판 뒤의 그림자·보유 기간·수익 분해 mock. 담당: holdings 영역. fixtures.ts 재료로 만든다.
// 종목마다 정해 둔 거래 계획(거래일 인덱스)으로 체결 내역을 만들고, 나머지는 모두 그 내역에서 계산한다.

type PlanStep = { at: number; side: "BUY" | "SELL"; /** BUY: 원화 예산, SELL: 보유 수량 대비 비율(1 = 전량) */ size: number };

// 거래일 인덱스(0 = 2024-10-14, 마지막 ≈ 522). 단타·스윙·중기·장투, 보유 중이 고루 섞이게 정했다.
const PLANS: Record<string, PlanStep[]> = {
  S00001: [
    { at: 5, side: "BUY", size: 3_000_000 },
    { at: 40, side: "BUY", size: 1_500_000 },
    { at: 120, side: "SELL", size: 0.3 },
    { at: 300, side: "SELL", size: 1 },
    { at: 330, side: "BUY", size: 2_500_000 },
    { at: 400, side: "BUY", size: 1_000_000 },
  ],
  S00002: [
    { at: 70, side: "BUY", size: 1_200_000 },
    { at: 73, side: "SELL", size: 1 },
    { at: 150, side: "BUY", size: 1_500_000 },
    { at: 154, side: "SELL", size: 1 },
    { at: 260, side: "BUY", size: 2_000_000 },
    { at: 300, side: "SELL", size: 1 },
    { at: 480, side: "BUY", size: 1_500_000 },
    { at: 484, side: "SELL", size: 1 },
  ],
  S00003: [
    ...Array.from({ length: 24 }, (_, k): PlanStep => ({ at: 2 + k * 21, side: "BUY", size: 500_000 })),
    { at: 350, side: "SELL", size: 0.2 } satisfies PlanStep,
  ].sort((a, b) => a.at - b.at),
  S00004: [
    { at: 100, side: "BUY", size: 2_000_000 },
    { at: 210, side: "SELL", size: 1 },
    { at: 400, side: "BUY", size: 1_000_000 },
    { at: 405, side: "SELL", size: 1 },
    { at: 500, side: "BUY", size: 1_500_000 },
    { at: 515, side: "SELL", size: 1 },
  ],
  SMPT: [
    { at: 30, side: "BUY", size: 3_000_000 },
    { at: 90, side: "BUY", size: 2_000_000 },
  ],
  SMPC: [
    { at: 60, side: "BUY", size: 2_000_000 },
    { at: 140, side: "SELL", size: 1 },
    { at: 200, side: "BUY", size: 1_000_000 },
    { at: 206, side: "SELL", size: 1 },
    { at: 350, side: "BUY", size: 2_500_000 },
  ],
  SMPS: Array.from({ length: 12 }, (_, k): PlanStep => ({ at: 10 + k * 42, side: "BUY", size: 700_000 })),
  SMPE: [
    { at: 180, side: "BUY", size: 1_800_000 },
    { at: 260, side: "SELL", size: 1 },
    { at: 300, side: "BUY", size: 1_200_000 },
    { at: 330, side: "SELL", size: 1 },
  ],
};

/** 배당을 주는 종목: 분기마다 평가금의 비율 */
const DIVIDEND_YIELD: Record<string, number> = { S00001: 0.004, S00003: 0.003, SMPS: 0.0035 };

const days = weekdays();
const LAST = days.length - 1;
const fxOn = (date: IsoDate) => closeOn("USDKRW", date);
const fxAt = (inst: Instrument, date: IsoDate) => (inst.currency === "USD" ? fxOn(date) : 1);

type Position = { qty: number; costLocal: number; costWon: number };

type Built = {
  instrument: Instrument;
  trades: Trade[];
  /** 매도 체결 → 그때의 실현 손익 */
  realized: Map<Trade, number>;
  dividends: { date: IsoDate; amount: number }[];
  periods: Omit<HoldingPeriod, "id">[];
  position: Position;
  firstIndex: number;
};

let built: Built[] | null = null;

function build(): Built[] {
  if (built) return built;
  built = INSTRUMENTS.map(buildInstrument);
  return built;
}

function buildInstrument(inst: Instrument): Built {
  const plan = PLANS[inst.code] ?? [];
  const rand = rng(`holdings:${inst.code}`);
  const usd = inst.currency === "USD";
  const trades: Trade[] = [];
  const realized = new Map<Trade, number>();
  const dividends: { date: IsoDate; amount: number }[] = [];
  const periods: Omit<HoldingPeriod, "id">[] = [];
  const pos: Position = { qty: 0, costLocal: 0, costWon: 0 };
  // 진행 중인 라운드트립의 원화 합계
  let round: { open: IsoDate; buy: number; sell: number; costs: number; div: number } | null = null;

  const yieldRate = DIVIDEND_YIELD[inst.code] ?? 0;
  const steps = [...plan];
  for (let i = 0; i <= LAST; i++) {
    const date = days[i];
    // 분기 배당: 3·6·9·12월 둘째 주쯤, 보유 중일 때만
    if (yieldRate > 0 && pos.qty > 0 && [3, 6, 9, 12].includes(Number(date.slice(5, 7))) && date.slice(8) >= "10" && days[i - 1]?.slice(8) < "10") {
      const amount = Math.round(pos.qty * closeOn(inst.code, date) * fxAt(inst, date) * yieldRate);
      dividends.push({ date, amount });
      if (round) round.div += amount;
    }
    while (steps.length && steps[0].at === i) {
      const step = steps.shift()!;
      const price = closeOn(inst.code, date);
      const fx = fxAt(inst, date);
      const qty =
        step.side === "BUY"
          ? Math.max(1, Math.round((step.size * (0.85 + rand() * 0.3)) / (price * fx)))
          : step.size >= 1
            ? pos.qty
            : Math.max(1, Math.round(pos.qty * step.size));
      if (qty <= 0) continue;
      const amount = Math.round(qty * price * fx);
      const fee = Math.round(amount * (usd ? 0.001 : 0.00015));
      const tax = step.side === "SELL" && !usd ? Math.round(amount * 0.0018) : 0;
      const trade: Trade = { date, instrument: inst, side: step.side, quantity: qty, priceLocal: price, fxRate: fx, amount, fee, tax };
      trades.push(trade);

      if (step.side === "BUY") {
        if (pos.qty === 0) round = { open: date, buy: 0, sell: 0, costs: 0, div: 0 };
        pos.qty += qty;
        pos.costLocal += qty * price;
        pos.costWon += amount;
        round!.buy += amount;
        round!.costs += fee;
      } else {
        const share = qty / pos.qty;
        const costWon = pos.costWon * share;
        realized.set(trade, Math.round(amount - costWon - fee - tax));
        pos.qty -= qty;
        pos.costLocal -= pos.costLocal * share;
        pos.costWon -= costWon;
        round!.sell += amount;
        round!.costs += fee + tax;
        if (pos.qty === 0) {
          pos.costLocal = 0;
          pos.costWon = 0;
          periods.push(periodOf(inst, round!, date, 0));
          round = null;
        }
      }
    }
  }
  if (round) {
    const last = days[LAST];
    periods.push(periodOf(inst, round, null, Math.round(pos.qty * closeOn(inst.code, last) * fxAt(inst, last))));
  }
  const firstIndex = plan.length ? plan[0].at : LAST;
  return { instrument: inst, trades, realized, dividends, periods, position: pos, firstIndex };
}

function periodOf(
  inst: Instrument,
  r: { open: IsoDate; buy: number; sell: number; costs: number; div: number },
  close: IsoDate | null,
  valueNow: number,
): Omit<HoldingPeriod, "id"> {
  const profit = r.sell + valueNow + r.div - r.buy - r.costs;
  return {
    instrument: inst,
    open: r.open,
    close,
    days: calendarDays(r.open, close ?? MOCK_LAST_DATE),
    returnRate: r.buy ? round4(profit / r.buy) : 0,
    profit: Math.round(profit),
    status: close ? "CLOSED" : "OPEN",
  };
}

const calendarDays = (a: IsoDate, b: IsoDate) => Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000);
const round4 = (v: number) => Math.round(v * 10_000) / 10_000;

function allPeriods(): HoldingPeriod[] {
  return build()
    .flatMap((b) => b.periods)
    .sort((a, b) => (a.open < b.open ? -1 : a.open > b.open ? 1 : a.instrument.code.localeCompare(b.instrument.code)))
    .map((p, i) => ({ id: `p-${i + 1}`, ...p }));
}

/** 모든 매도. id는 날짜 순서로 매긴다. */
function allSales(horizon: SaleHorizon): Sale[] {
  const sells = build()
    .flatMap((b) => b.trades.filter((t) => t.side === "SELL").map((t) => ({ b, t })))
    .sort((x, y) => (x.t.date < y.t.date ? -1 : x.t.date > y.t.date ? 1 : 0));
  return sells.map(({ b, t }, i) => saleOf(`s-${i + 1}`, t, b.realized.get(t) ?? 0, horizon));
}

function saleOf(id: string, t: Trade, realizedProfit: number, horizon: SaleHorizon): Sale {
  const series = priceSeries(t.instrument.code);
  const at = days.indexOf(t.date);
  const after = (n: number) => (at + n <= LAST ? round4(series[at + n].closeLocal / t.priceLocal - 1) : null);
  const now = series[LAST].closeLocal;
  const h = at + horizon <= LAST ? series[at + horizon] : null;
  const verdict = h == null ? "PENDING" : h.closeLocal <= t.priceLocal ? "GOOD" : "BAD";
  const afterEffect = h == null ? 0 : -Math.round(t.quantity * (h.closeLocal - t.priceLocal) * fxAt(t.instrument, h.date));
  return {
    id,
    date: t.date,
    instrument: t.instrument,
    quantity: t.quantity,
    priceLocal: t.priceLocal,
    fxRate: t.fxRate,
    amount: t.amount,
    realizedProfit,
    priceNowLocal: now,
    changeSinceSale: round4(now / t.priceLocal - 1),
    changeAfter: { d5: after(5), d20: after(20), d60: after(60) },
    verdict,
    afterEffect: afterEffect === 0 ? 0 : afterEffect,
    series: series.slice(Math.max(0, at - 60)),
  };
}

function fxOf(b: Built): FxItem | null {
  const { qty, costLocal, costWon } = b.position;
  if (b.instrument.currency !== "USD" || qty === 0) return null;
  const last = days[LAST];
  const p0 = costLocal / qty;
  const f0 = costWon / costLocal;
  const p1 = closeOn(b.instrument.code, last);
  const f1 = fxOn(last);
  const priceEffect = Math.round(qty * (p1 - p0) * f0);
  const profit = Math.round(qty * p1 * f1) - Math.round(costWon);
  return {
    instrument: b.instrument,
    quantity: qty,
    avgCostLocal: Math.round(p0 * 100) / 100,
    avgFxRate: Math.round(f0 * 10) / 10,
    priceLocal: p1,
    fxRate: f1,
    priceEffect,
    // 반올림 차이는 환율 효과 쪽에 넣어 합이 정확히 맞게 한다
    fxEffect: profit - priceEffect,
    profit,
  };
}

export function mockHoldingDetail(code: string): HoldingDetail {
  const b = build().find((x) => x.instrument.code === code);
  if (!b || b.trades.length === 0) {
    throw new ApiError(404, "INSTRUMENT_NOT_FOUND", "이 종목의 보유 이력을 찾지 못했어요.");
  }
  const last = days[LAST];
  const { qty, costLocal, costWon } = b.position;
  const priceLocal = closeOn(code, last);
  const fxRate = fxAt(b.instrument, last);
  const value = Math.round(qty * priceLocal * fxRate);
  const profit = value - Math.round(costWon);
  const periodIds = new Map(allPeriods().map((p) => [`${p.instrument.code}:${p.open}`, p.id]));
  return {
    instrument: b.instrument,
    position:
      qty > 0
        ? {
            quantity: qty,
            avgCostLocal: b.instrument.currency === "USD" ? Math.round((costLocal / qty) * 100) / 100 : Math.round(costLocal / qty),
            priceLocal,
            fxRate,
            value,
            profit,
            returnRate: round4(profit / costWon),
          }
        : null,
    realizedProfit: [...b.realized.values()].reduce((a, v) => a + v, 0),
    dividends: b.dividends.reduce((a, d) => a + d.amount, 0),
    prices: priceSeries(code).slice(Math.max(0, b.firstIndex - 60)),
    trades: b.trades,
    periods: b.periods.map((p) => ({ id: periodIds.get(`${code}:${p.open}`) ?? "p-0", ...p })),
    sales: allSales(20).filter((s) => s.instrument.code === code),
    fx: fxOf(b),
  };
}

/** 지금 보유 중인 종목(비중 제외). 메인 보유 목록 mock이 같은 숫자를 쓰도록 내보낸다. */
export function mockOpenPositions(): (NonNullable<HoldingDetail["position"]> & { instrument: Instrument })[] {
  return build()
    .filter((b) => b.position.qty > 0)
    .map((b) => ({ instrument: b.instrument, ...mockHoldingDetail(b.instrument.code).position! }));
}

export function mockSales(horizon: SaleHorizon): SalesInsight {
  const sales = allSales(horizon).reverse(); // 최근 매도 먼저
  const judged = sales.filter((s) => s.verdict !== "PENDING");
  const good = judged.filter((s) => s.verdict === "GOOD");
  return {
    horizon,
    summary: {
      sales: sales.length,
      judged: judged.length,
      good: good.length,
      winRate: judged.length ? round4(good.length / judged.length) : 0,
      avoidedLoss: good.reduce((a, s) => a + s.afterEffect, 0),
      missedGain: judged.filter((s) => s.verdict === "BAD").reduce((a, s) => a + Math.abs(s.afterEffect), 0),
    },
    sales,
  };
}

const BUCKETS: { key: HoldingBucketKey; label: string; fits: (d: number) => boolean }[] = [
  { key: "SHORT", label: "단타 (7일 이하)", fits: (d) => d <= 7 },
  { key: "SWING", label: "스윙 (8–90일)", fits: (d) => d >= 8 && d <= 90 },
  { key: "MID", label: "중기 (91–365일)", fits: (d) => d >= 91 && d <= 365 },
  { key: "LONG", label: "장투 (1년 초과)", fits: (d) => d > 365 },
];

export function mockHoldingPeriods(): HoldingPeriodsInsight {
  const periods = allPeriods();
  return {
    periods,
    buckets: BUCKETS.map(({ key, label, fits }) => {
      const ps = periods.filter((p) => fits(p.days));
      const n = ps.length;
      return {
        key,
        label,
        count: n,
        winRate: n ? round4(ps.filter((p) => p.profit > 0).length / n) : 0,
        avgReturn: n ? round4(ps.reduce((a, p) => a + p.returnRate, 0) / n) : 0,
        profit: ps.reduce((a, p) => a + p.profit, 0),
      };
    }),
  };
}

export function mockFx(): FxInsight {
  const items = build()
    .map(fxOf)
    .filter((x): x is FxItem => x != null)
    .sort((a, b) => Math.abs(b.profit) - Math.abs(a.profit));
  const sum = (f: (x: FxItem) => number) => items.reduce((a, x) => a + f(x), 0);
  return {
    asOf: MOCK_LAST_DATE,
    items,
    total: { priceEffect: sum((x) => x.priceEffect), fxEffect: sum((x) => x.fxEffect), profit: sum((x) => x.profit) },
  };
}
