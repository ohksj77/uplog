import Link from "next/link";
import type { ReactNode } from "react";

type PageHeaderProps = {
  title: string;
  /** 이 화면이 답하는 질문, 예: "그때 판 게 잘한 일이었나" */
  question?: string;
  back?: { href: string; label: string };
  /** 제목 옆 배지(예: 샘플 계좌) 또는 오른쪽 도구 */
  aside?: ReactNode;
};

export function PageHeader({ title, question, back, aside }: PageHeaderProps) {
  return (
    <header className="mb-6 space-y-2">
      {back && (
        <Link href={back.href} className="inline-block text-sm text-muted hover:text-foreground">
          ← {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-bold tracking-tight break-keep">{title}</h1>
        {aside}
      </div>
      {question && <p className="text-sm text-muted">{question}</p>}
    </header>
  );
}
