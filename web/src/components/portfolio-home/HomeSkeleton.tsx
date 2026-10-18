// 메인 자리표시자. 서버 셸(Suspense fallback)에 담긴다.

const COLS = 26;

export function HomeSkeleton() {
  return (
    <div role="status" aria-live="polite" className="animate-pulse space-y-6 motion-reduce:animate-none">
      <span className="sr-only">계좌를 불러오고 있어요.</span>
      <div aria-hidden className="space-y-6">
        <div className="space-y-2">
          <div className="h-7 w-40 rounded bg-surface" />
          <div className="h-4 w-56 max-w-full rounded bg-surface" />
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 5 }, (_, i) => (
            <div key={i} className={`h-20 rounded-2xl bg-surface ${i === 0 ? "col-span-2 sm:col-span-1" : ""}`} />
          ))}
        </div>
        <div className="h-8 w-72 max-w-full rounded bg-surface" />
        <div className="rounded-2xl bg-surface p-4">
          <div className="grid gap-[3px] overflow-hidden" style={{ gridTemplateColumns: `repeat(${COLS}, minmax(0, 1fr))` }}>
            {Array.from({ length: COLS * 5 }, (_, i) => (
              <span key={i} className="aspect-square rounded-[3px] bg-[var(--log-empty)]" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
