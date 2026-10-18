import Link from "next/link";
import { Signed } from "@/components/result/Signed";
import type { HoldingPeriod } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatSignedWon } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

// 포지션 목록(타임라인의 글 버전).

export function PeriodList({ periods, showInstrument = true }: { periods: HoldingPeriod[]; showInstrument?: boolean }) {
  return (
    <ul className="divide-y divide-border">
      {periods.map((p) => (
        <li key={p.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
          <div className="min-w-0">
            {showInstrument && (
              <Link href={holdingHref(p.instrument.code)} className="block truncate font-medium hover:underline">
                {p.instrument.name}
              </Link>
            )}
            <p className={showInstrument ? "text-xs text-muted" : ""}>
              {formatDate(p.open)} ~ {p.close ? formatDate(p.close) : "보유 중"} · {p.days.toLocaleString("ko-KR")}일
            </p>
          </div>
          <div className="shrink-0 text-right tabular-nums">
            <Signed value={p.returnRate} className="block font-medium">
              {formatPercent(p.returnRate)}
            </Signed>
            <Signed value={p.profit} className="block text-xs">
              {formatSignedWon(p.profit)}
            </Signed>
          </div>
        </li>
      ))}
    </ul>
  );
}
