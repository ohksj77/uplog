import type { ApiError } from "@/lib/api/client";

/** 불러오는 중 자리표시자. 높이는 실제 내용과 비슷하게 준다. */
export function LoadingBlock({ height = 240, label = "불러오는 중" }: { height?: number; label?: string }) {
  return (
    <div role="status" aria-live="polite" className="animate-pulse rounded-2xl bg-surface motion-reduce:animate-none" style={{ height }}>
      <span className="sr-only">{label}</span>
    </div>
  );
}

export function ErrorBlock({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  const notConnected = error.code === "ACCOUNT_NOT_CONNECTED";
  return (
    <section role="alert" className="rounded-2xl bg-surface px-5 py-10 text-center">
      <p className="font-semibold">{notConnected ? "연결된 계좌가 없어요" : "불러오지 못했어요"}</p>
      <p className="mt-2 text-sm text-muted">{error.message}</p>
      {onRetry && !notConnected && (
        <button type="button" onClick={onRetry} className="mt-6 h-11 rounded-xl bg-accent px-5 text-sm font-semibold text-white">
          다시 시도
        </button>
      )}
    </section>
  );
}

export function SampleAccountBadge() {
  return (
    <span className="rounded-md border border-border px-1.5 py-0.5 text-xs text-muted" title="합성 데이터로 만든 데모 계좌예요">
      샘플 계좌
    </span>
  );
}
