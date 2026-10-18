import Link from "next/link";

/** firstDate~lastDate 사이 연도(오름차순). 로그 응답 전에도 연도 선택을 그릴 수 있게. */
export function yearsBetween(firstDate: string, lastDate: string): number[] {
  const a = Number(firstDate.slice(0, 4));
  const b = Number(lastDate.slice(0, 4));
  return Array.from({ length: Math.max(0, b - a + 1) }, (_, i) => a + i);
}

type YearTabsProps = {
  years: readonly number[];
  selected: number | null;
  /** 연도를 붙일 경로, 예: "/" → "/?year=2026", "/insights/costs" → "/insights/costs?year=2026" */
  basePath?: string;
};

/** 연도 고르기. URL(?year=)이 상태라 링크로 만든다 — 뒤로 가기·공유가 그대로 된다. */
export function YearTabs({ years, selected, basePath = "/" }: YearTabsProps) {
  return (
    <nav aria-label="연도">
      <ul className="inline-flex flex-wrap rounded-lg bg-surface p-0.5 text-sm">
        {years.map((y) => {
          const current = y === selected;
          return (
            <li key={y}>
              <Link
                href={`${basePath}?year=${y}`}
                scroll={false}
                aria-current={current ? "page" : undefined}
                className={`block rounded-md px-3 py-1.5 tabular-nums outline-none focus-visible:ring-2 focus-visible:ring-accent motion-safe:transition-colors ${
                  current ? "bg-background font-semibold text-foreground shadow-sm" : "text-muted hover:text-foreground"
                }`}
              >
                {y}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
