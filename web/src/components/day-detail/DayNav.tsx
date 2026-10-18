import Link from "next/link";
import { formatDate } from "@/lib/format";
import { dayHref } from "@/lib/routes";

/** 이전·다음 기록일로. 없으면 자리만 남긴다. */
export function DayNav({ prevDate, nextDate }: { prevDate: string | null; nextDate: string | null }) {
  const base = "flex min-h-11 flex-col justify-center rounded-xl bg-surface px-4 py-2 text-sm hover:bg-border focus-visible:outline-2 focus-visible:outline-accent";
  return (
    <nav aria-label="다른 날" className="grid grid-cols-2 gap-3">
      {prevDate ? (
        <Link href={dayHref(prevDate)} rel="prev" className={base}>
          <span className="text-xs text-muted">← 이전 거래일</span>
          <span className="tabular-nums">{formatDate(prevDate)}</span>
        </Link>
      ) : (
        <span />
      )}
      {nextDate ? (
        <Link href={dayHref(nextDate)} rel="next" className={`${base} items-end text-right`}>
          <span className="text-xs text-muted">다음 거래일 →</span>
          <span className="tabular-nums">{formatDate(nextDate)}</span>
        </Link>
      ) : (
        <span />
      )}
    </nav>
  );
}
