"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { loginHref, safeNextPath } from "@/components/auth/next-path";
import { primaryButton, textLink } from "@/components/connect/styles";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError } from "@/lib/api/client";
import { redeemHandoff } from "@/lib/api/account-client";
import type { HandoffRedeemResult } from "@/lib/api/account-types";

// 코드는 한 번만 쓸 수 있다. 개발 모드의 effect 두 번 실행이나 다시 그리기에도 요청이 한 번만 나가도록 코드별로 결과를 묶어 둔다.
const redeeming = new Map<string, Promise<HandoffRedeemResult>>();
function redeemOnce(token: string): Promise<HandoffRedeemResult> {
  let p = redeeming.get(token);
  if (!p) {
    p = redeemHandoff(token);
    redeeming.set(token, p);
  }
  return p;
}

type State = { kind: "working" } | { kind: "done"; nickname: string } | { kind: "invalid"; message: string };

const MOVE_DELAY_MS = 1500;
const INVALID_MESSAGE = "이 링크는 쓸 수 없어요. 이미 썼거나 10분이 지났을 수 있어요. 휴대폰에서 링크를 새로 만들거나, 이 기기에서 직접 로그인해 주세요.";

/** /continue?token=… — 휴대폰에서 만든 1회용 코드로 이 기기(PC)에 로그인하고 연결 화면으로 보낸다. */
export function ContinueScreen() {
  const params = useSearchParams();
  const token = params.get("token")?.trim() ?? "";
  const next = safeNextPath(params.get("next"), "/connect");
  const [state, setState] = useState<State>(token ? { kind: "working" } : { kind: "invalid", message: INVALID_MESSAGE });
  const statusRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (!token) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let cancelled = false;
    redeemOnce(token)
      .then((res) => {
        if (cancelled) return;
        setState({ kind: "done", nickname: res.nickname });
        // 세션이 바뀌었으니 숨겨 둔 화면(Activity)까지 새로 받도록 전체 이동한다. 주소창의 코드도 함께 사라진다.
        timer = setTimeout(() => window.location.replace(next), MOVE_DELAY_MS);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const err = e instanceof ApiError ? e : null;
        setState({ kind: "invalid", message: err?.code === "HANDOFF_INVALID" ? err.message || INVALID_MESSAGE : (err?.message ?? INVALID_MESSAGE) });
      });
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [token, next]);

  useEffect(() => {
    if (state.kind !== "working") statusRef.current?.focus();
  }, [state.kind]);

  return (
    <div className="mx-auto max-w-md">
      <PageHeader title="PC에서 이어 하기" question="휴대폰에서 만든 링크로 이 기기에서 이어서 해요." />
      <section aria-live="polite" className="space-y-4 rounded-2xl bg-surface p-5">
        {state.kind === "working" && <p className="text-sm">로그인 정보를 확인하고 있어요…</p>}
        {state.kind === "done" && (
          <>
            <p ref={statusRef} tabIndex={-1} className="font-semibold outline-none break-keep">
              {state.nickname ? `${state.nickname}님으로 이어서 할게요` : "이어서 할게요"}
            </p>
            <p className="text-sm text-muted break-keep">잠시 뒤 연결 화면으로 이동해요.</p>
            <a href={next} className={`${primaryButton} w-full sm:w-auto`}>
              바로 이동하기
            </a>
          </>
        )}
        {state.kind === "invalid" && (
          <>
            <p ref={statusRef} tabIndex={-1} className="font-semibold outline-none">
              이어 하지 못했어요
            </p>
            <p className="text-sm text-muted break-keep">{state.message}</p>
            <Link href={loginHref(next)} className={`${textLink} inline-block text-sm`}>
              로그인하러 가기
            </Link>
          </>
        )}
      </section>
    </div>
  );
}
