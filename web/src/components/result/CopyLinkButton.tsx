"use client";

import { useEffect, useState } from "react";

type CopyState = "idle" | "copied" | "failed";

/** 지금 주소를 복사한다. 공유 카드는 나중에. */
export function CopyLinkButton() {
  const [state, setState] = useState<CopyState>("idle");

  useEffect(() => {
    if (state === "idle") return;
    const t = setTimeout(() => setState("idle"), 2000);
    return () => clearTimeout(t);
  }, [state]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setState("copied");
    } catch {
      setState("failed");
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={copy}
        className="inline-flex h-9 items-center rounded-lg bg-surface px-3 text-sm font-medium"
      >
        {state === "copied" ? "복사했어요" : "링크 복사"}
      </button>
      <span className="sr-only" aria-live="polite">
        {state === "copied" ? "링크를 복사했어요." : state === "failed" ? "링크를 복사하지 못했어요." : ""}
      </span>
      {state === "failed" && <span className="text-xs text-muted">복사하지 못했어요. 주소창의 링크를 써 주세요.</span>}
    </>
  );
}
