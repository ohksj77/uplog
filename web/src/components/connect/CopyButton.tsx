"use client";

import { useEffect, useState } from "react";

/** 값을 클립보드에 복사한다. 결과는 버튼 글자와 화면 낭독기 알림으로 알려 준다. */
export function CopyButton({ value, label }: { value: string; label: string }) {
  const [result, setResult] = useState<"copied" | "failed" | null>(null);

  useEffect(() => {
    if (!result) return;
    const t = setTimeout(() => setResult(null), 2000);
    return () => clearTimeout(t);
  }, [result]);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setResult("copied");
    } catch {
      setResult("failed");
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={copy}
        aria-label={`${label} 복사`}
        className="inline-flex h-9 shrink-0 items-center rounded-lg border border-border bg-background px-3 text-sm font-medium hover:bg-surface outline-none focus-visible:outline-2 focus-visible:outline-accent"
      >
        {result === "copied" ? "복사했어요" : "복사"}
      </button>
      <span aria-live="polite" className="sr-only">
        {result === "copied" ? `${label}를 복사했어요` : result === "failed" ? "복사하지 못했어요. 직접 선택해서 복사해 주세요." : ""}
      </span>
    </>
  );
}
