import Link from "next/link";

type ResultErrorProps = {
  title: string;
  message: string;
  /** 다시 시도 버튼. 없으면 입력 화면 링크만 보여준다. */
  onRetry?: () => void;
  /** 입력 화면으로 돌아갈 때 붙일 쿼리 */
  backQuery?: string;
};

export function ResultError({ title, message, onRetry, backQuery }: ResultErrorProps) {
  return (
    <section role="alert" className="rounded-2xl bg-surface px-5 py-10 text-center">
      <h1 className="text-lg font-semibold">{title}</h1>
      <p className="mt-2 text-sm text-muted">{message}</p>
      <div className="mt-6 flex flex-wrap justify-center gap-2">
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="h-11 rounded-xl bg-accent px-5 text-sm font-semibold text-white"
          >
            다시 시도
          </button>
        )}
        <Link
          href={backQuery ? `/simulation?${backQuery}` : "/simulation"}
          className="inline-flex h-11 items-center rounded-xl border border-border bg-background px-5 text-sm font-semibold"
        >
          조건 다시 고르기
        </Link>
      </div>
    </section>
  );
}
