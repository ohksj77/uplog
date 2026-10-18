// 로그 모양 자리표시자. 서버 셸(Suspense fallback)과 요청 중 화면이 함께 쓴다.

const COLS = 26;
const ROWS = 5;

function LogBlock() {
  return (
    <div className="rounded-2xl bg-surface p-5">
      <div className="mb-4 h-4 w-20 rounded bg-border" />
      <div className="grid gap-[3px] overflow-hidden" style={{ gridTemplateColumns: `repeat(${COLS}, minmax(0, 1fr))` }}>
        {Array.from({ length: COLS * ROWS }, (_, i) => (
          <span key={i} className="aspect-square rounded-[3px] bg-[var(--log-empty)]" />
        ))}
      </div>
    </div>
  );
}

export function ResultSkeleton() {
  return (
    <div role="status" aria-live="polite" className="animate-pulse space-y-4 motion-reduce:animate-none">
      <span className="sr-only">결과를 계산하고 있어요.</span>
      <div className="space-y-2" aria-hidden>
        <div className="h-4 w-40 rounded bg-surface" />
        <div className="h-7 w-64 max-w-full rounded bg-surface" />
      </div>
      <div className="space-y-4" aria-hidden>
        <LogBlock />
        <LogBlock />
        <div className="h-60 rounded-2xl bg-surface" />
      </div>
    </div>
  );
}
