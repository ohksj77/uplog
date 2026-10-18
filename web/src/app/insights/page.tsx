import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "분석 | 업로그" };

const INSIGHTS = [
  { href: "/insights/sales", title: "판 뒤의 그림자", question: "그때 판 게 잘한 일이었나" },
  { href: "/insights/shadow", title: "그림자 계좌", question: "종목 고르기가 지수보다 나았나" },
  { href: "/insights/waterfall", title: "손익 폭포수", question: "누가 내 수익을 만들고 깎았나" },
  { href: "/insights/holding-periods", title: "보유 기간 타임라인", question: "단타와 장투 중 뭘 잘하나" },
  { href: "/insights/fx", title: "수익 분해", question: "이 수익은 종목 덕인가 환율 덕인가" },
  { href: "/insights/underwater", title: "수중 차트", question: "지금 하락은 전에 버틴 것보다 큰가" },
] as const;

export default function InsightsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">분석</h1>
      <ul className="grid gap-3 sm:grid-cols-2">
        {INSIGHTS.map((i) => (
          <li key={i.href}>
            <Link
              href={i.href}
              className="block rounded-2xl bg-surface p-4 transition-colors hover:bg-border focus-visible:outline-2 focus-visible:outline-accent"
            >
              <span className="block font-semibold">{i.title}</span>
              <span className="mt-1 block text-sm text-muted">{i.question}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
