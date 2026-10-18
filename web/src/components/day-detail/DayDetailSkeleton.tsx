export function DayDetailSkeleton() {
  return (
    <div role="status" aria-live="polite" className="animate-pulse space-y-6 motion-reduce:animate-none">
      <span className="sr-only">그날 기록을 불러오고 있어요.</span>
      <div aria-hidden className="space-y-6">
        <div className="space-y-2">
          <div className="h-4 w-24 rounded bg-surface" />
          <div className="h-7 w-48 rounded bg-surface" />
        </div>
        <div className="h-28 rounded-2xl bg-surface" />
        <div className="h-56 rounded-2xl bg-surface" />
      </div>
    </div>
  );
}
