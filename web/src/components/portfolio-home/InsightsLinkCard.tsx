import Link from "next/link";

export function InsightsLinkCard() {
  return (
    <Link
      href="/insights"
      className="flex items-center justify-between gap-4 rounded-2xl bg-surface p-5 transition-colors hover:bg-border focus-visible:outline-2 focus-visible:outline-accent"
    >
      <span>
        <span className="block font-semibold">분석 보기</span>
        <span className="mt-1 block text-sm text-muted break-keep">판 뒤의 주가, 지수와 비교, 수익을 만든 종목까지 더 깊이 들여다봐요.</span>
      </span>
      <span aria-hidden className="text-muted">
        →
      </span>
    </Link>
  );
}
