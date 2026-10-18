"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { loginHref } from "@/components/auth/next-path";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { useMe } from "@/lib/use-me";

/** 로그인한 사용자에게만 children을 보여 준다. 비로그인은 /login?next=…로 보낸다. */
export function RequireLogin({ next, children }: { next: string; children: ReactNode }) {
  const { state, user, refresh } = useMe();
  const router = useRouter();
  const guest = state.status === "success" && user === null;

  useEffect(() => {
    if (guest) router.replace(loginHref(next));
  }, [guest, next, router]);

  if (state.status === "loading") return <LoadingBlock height={320} label="로그인 정보를 확인하는 중" />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={() => void refresh()} />;
  if (!user) {
    return (
      <section className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">로그인이 필요해요</p>
        <p className="mt-2 text-sm text-muted">로그인 화면으로 이동하고 있어요.</p>
        <Link href={loginHref(next)} className="mt-4 inline-block text-sm font-medium text-accent underline-offset-4 hover:underline">
          로그인하러 가기
        </Link>
      </section>
    );
  }
  return children;
}
