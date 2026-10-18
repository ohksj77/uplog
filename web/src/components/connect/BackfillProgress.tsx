import type { TossConnection } from "@/lib/api/account-types";

/** 역산 진행률 막대. 화면 낭독기에는 progressbar 값으로 읽힌다(매 폴링마다 알리지는 않는다). */
export function BackfillProgress({ connection, label = "과거 기록 만드는 중" }: { connection: TossConnection; label?: string }) {
  const progress = connection.backfill?.progress ?? 0;
  const pct = Math.round(Math.min(1, Math.max(0, progress)) * 100);
  const pending = connection.backfill?.state === "PENDING";
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3 text-sm">
        <span className="font-medium">{label}</span>
        <span className="text-muted tabular-nums">{pending ? "준비 중" : `${pct}%`}</span>
      </div>
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={pct}
        aria-valuetext={pending ? "준비 중" : `${pct}%`}
        className="mt-2 h-2 overflow-hidden rounded-full bg-border"
      >
        <div className="h-full rounded-full bg-accent motion-safe:transition-[width] motion-safe:duration-500" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

/**
 * "2026-10-18T07:03:00Z" → "10월 18일 오후 4:03", 날짜만 오면("2026-10-18") → "10월 18일".
 * 올해가 아니면 연도를 붙인다.
 */
export function formatDateTime(iso: string | null): string {
  if (!iso) return "아직 없어요";
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(iso);
  // 날짜만 온 값은 UTC 자정으로 읽히면 하루가 밀릴 수 있어 현지 자정으로 만든다.
  const d = dateOnly ? new Date(`${iso}T00:00:00`) : new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const sameYear = d.getFullYear() === new Date().getFullYear();
  return d.toLocaleString("ko-KR", {
    year: sameYear ? undefined : "numeric",
    month: "long",
    day: "numeric",
    ...(dateOnly ? {} : { hour: "numeric", minute: "2-digit" }),
  });
}
