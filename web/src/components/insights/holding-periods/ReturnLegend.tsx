import { returnColor } from "@/components/insights/holding-periods/return-color";

// 막대 색 범례. 손실 진함 → 이익 진함.

const STEPS = [-0.3, -0.15, -0.05, -0.01, 0.01, 0.05, 0.15, 0.3];

export function ReturnLegend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted">
      <div className="flex items-center gap-1.5" role="img" aria-label="막대 색은 수익률. 빨강은 이익, 파랑은 손실이고 진할수록 커요. 경계는 2%, 10%, 25%.">
        <span aria-hidden>손실</span>
        <span className="flex" aria-hidden>
          {STEPS.map((r) => (
            <span key={r} className="h-3 w-4 first:rounded-l last:rounded-r" style={{ background: returnColor(r) }} />
          ))}
        </span>
        <span aria-hidden>이익</span>
      </div>
      <span className="flex items-center gap-1.5" aria-hidden>
        <OpenEnd className="text-muted" /> 보유 중(오늘까지)
      </span>
    </div>
  );
}

/** 보유 중 막대 끝 표시(오른쪽 화살촉) */
export function OpenEnd({ className = "", color }: { className?: string; color?: string }) {
  return (
    <svg width={7} height={12} viewBox="0 0 7 12" className={className} aria-hidden>
      <path d="M0,0 L7,6 L0,12 Z" fill={color ?? "currentColor"} />
    </svg>
  );
}
