import Link from "next/link";
import { OpenEnd } from "@/components/insights/holding-periods/ReturnLegend";
import { returnColor } from "@/components/insights/holding-periods/return-color";
import type { IsoDate } from "@/lib/api/types";
import type { HoldingPeriod } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatSignedWon } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

// 보유 기간 타임라인. 날짜 축 하나를 공유하고 종목마다 한 줄, 포지션마다 막대 하나.
// 좁은 화면에서는 이 상자 안에서만 가로로 스크롤한다(종목 이름은 왼쪽에 붙어 있음).

type TimelineProps = {
  periods: HoldingPeriod[];
  from: IsoDate;
  to: IsoDate;
  /** 막대를 종목 상세로 잇는다 */
  link?: boolean;
  /** 이 너비보다 좁으면 가로 스크롤 */
  minWidth?: number;
  showNames?: boolean;
};

const DAY = 86_400_000;

export function periodLabel(p: HoldingPeriod): string {
  const range = `${formatDate(p.open)}~${p.close ? formatDate(p.close) : "보유 중"}`;
  return `${p.instrument.name} ${range}, ${p.days}일, 수익률 ${formatPercent(p.returnRate)}, 손익 ${formatSignedWon(p.profit)}`;
}

export function Timeline({ periods, from, to, link = true, minWidth = 640, showNames = true }: TimelineProps) {
  const t0 = Date.parse(from);
  const span = Math.max(DAY, Date.parse(to) - t0);
  const pos = (d: IsoDate) => Math.min(100, Math.max(0, ((Date.parse(d) - t0) / span) * 100));
  const ticks = axisTicks(from, to);
  const rows = groupByInstrument(periods);

  return (
    <div className="overflow-x-auto overscroll-x-contain">
      <div className="relative pr-3" style={{ minWidth }}>
        <div className="relative mr-2 h-5 text-[11px] text-muted" aria-hidden>
          {ticks.map((t) => (
            <span key={t.date} className="absolute top-0 -translate-x-1/2 whitespace-nowrap" style={{ left: `${pos(t.date)}%` }}>
              {t.label}
            </span>
          ))}
          <span className="absolute top-0 right-0 whitespace-nowrap">오늘</span>
        </div>

        <div className="relative">
          <div className="pointer-events-none absolute inset-0 mr-2" aria-hidden>
            {ticks.map((t) => (
              <span key={t.date} className="absolute inset-y-0 border-l border-border" style={{ left: `${pos(t.date)}%` }} />
            ))}
            <span className="absolute inset-y-0 right-0 border-l border-dashed border-muted" />
          </div>

          <ul className="relative">
            {rows.map(({ code, name, items }) => (
              <li key={code} className="py-1.5">
                {showNames && (
                  <span className="sticky left-0 mb-1 inline-block max-w-[50vw] truncate bg-background pr-2 text-xs font-medium">
                    {name}
                  </span>
                )}
                <div className="relative mr-2 h-5">
                  {items.map((p) => (
                    <Bar key={p.id} period={p} left={pos(p.open)} right={p.close ? pos(p.close) : 100} link={link} />
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

function Bar({ period: p, left, right, link }: { period: HoldingPeriod; left: number; right: number; link: boolean }) {
  const color = returnColor(p.returnRate);
  const open = p.status === "OPEN";
  const className = `absolute inset-y-0 block ring-1 ring-foreground/15 ring-inset ${open ? "rounded-l" : "rounded"} ${
    link ? "hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-accent" : ""
  }`;
  const style = { left: `${left}%`, width: `${Math.max(0, right - left)}%`, minWidth: 3, background: color };
  const label = periodLabel(p);
  const end = open && <OpenEnd className="absolute top-1/2 left-full -translate-y-1/2 text-muted" color={color} />;
  return link ? (
    <Link href={holdingHref(p.instrument.code)} className={className} style={style} aria-label={label} title={label}>
      {end}
    </Link>
  ) : (
    <span className={className} style={style} role="img" aria-label={label} title={label}>
      {end}
    </span>
  );
}

function groupByInstrument(periods: HoldingPeriod[]) {
  const rows = new Map<string, { code: string; name: string; items: HoldingPeriod[] }>();
  for (const p of periods) {
    const row = rows.get(p.instrument.code) ?? { code: p.instrument.code, name: p.instrument.name, items: [] };
    row.items.push(p);
    rows.set(p.instrument.code, row);
  }
  return [...rows.values()];
}

/** 기간 길이에 따라 1·3·6개월 간격. 1월은 연도, 나머지는 "N월". */
function axisTicks(from: IsoDate, to: IsoDate): { date: IsoDate; label: string }[] {
  const days = (Date.parse(to) - Date.parse(from)) / DAY;
  const step = days > 500 ? 6 : days > 150 ? 3 : 1;
  const out: { date: IsoDate; label: string }[] = [];
  let y = Number(from.slice(0, 4));
  let m = Number(from.slice(5, 7)) + 1;
  for (;;) {
    if (m > 12) {
      m -= 12;
      y += 1;
    }
    const date = `${y}-${String(m).padStart(2, "0")}-01`;
    if (date >= to) break;
    if ((m - 1) % step === 0) out.push({ date, label: m === 1 ? String(y) : `${m}월` });
    m += 1;
  }
  // 오른쪽 끝 "오늘"과 겹치지 않게 마지막 눈금이 너무 가까우면 뺀다
  const tooClose = (d: IsoDate) => (Date.parse(to) - Date.parse(d)) / DAY < days * 0.06;
  return out.filter((t) => !tooClose(t.date));
}
