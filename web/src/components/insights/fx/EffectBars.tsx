import { Signed } from "@/components/result/Signed";
import { formatSignedWon } from "@/lib/format";

// 주가 효과·환율 효과를 0을 가운데 둔 양방향 막대로. 막대 색은 효과 종류(보라·호박), 부호는 방향과 숫자 색으로 보인다.

export const EFFECTS = [
  { key: "priceEffect", label: "주가 효과", bar: "bg-series-lump" },
  { key: "fxEffect", label: "환율 효과", bar: "bg-series-recurring" },
] as const;

export function EffectBars({ priceEffect, fxEffect, max }: { priceEffect: number; fxEffect: number; max: number }) {
  const values = { priceEffect, fxEffect };
  return (
    <dl className="space-y-1.5">
      {EFFECTS.map((e) => {
        const v = values[e.key];
        const pct = max > 0 ? (Math.abs(v) / max) * 50 : 0;
        return (
          <div key={e.key} className="grid grid-cols-[4.5rem_1fr_auto] items-center gap-2 text-sm">
            <dt className="text-xs text-muted">{e.label}</dt>
            <dd className="relative h-3" aria-hidden>
              <span className="absolute inset-y-[-3px] left-1/2 border-l border-muted" />
              <span
                className={`absolute inset-y-0 ${e.bar} ${v >= 0 ? "rounded-r" : "rounded-l"}`}
                style={v >= 0 ? { left: "50%", width: `${pct}%` } : { right: "50%", width: `${pct}%` }}
              />
            </dd>
            <dd className="min-w-[6.5rem] text-right font-medium tabular-nums">
              <Signed value={v}>{formatSignedWon(v)}</Signed>
            </dd>
          </div>
        );
      })}
    </dl>
  );
}

export function EffectLegend() {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted" aria-hidden>
      {EFFECTS.map((e) => (
        <li key={e.key} className="flex items-center gap-1.5">
          <span className={`inline-block h-2.5 w-4 rounded-sm ${e.bar}`} />
          {e.label}
        </li>
      ))}
      <li>가운데 선 오른쪽 이익, 왼쪽 손실</li>
    </ul>
  );
}
