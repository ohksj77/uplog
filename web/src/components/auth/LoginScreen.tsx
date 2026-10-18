"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useId, useRef, useState, type FormEvent } from "react";
import { safeNextPath } from "@/components/auth/next-path";
import { input, fieldError, primaryButton, secondaryButton } from "@/components/connect/styles";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError } from "@/lib/api/client";
import { USE_MOCK } from "@/lib/api/client";
import { devLogin, getAuthProviders, KAKAO_LOGIN_PATH, mockKakaoLogin } from "@/lib/api/account-client";
import { useApi } from "@/lib/use-api";
import { useMe } from "@/lib/use-me";

export function LoginScreen() {
  const next = safeNextPath(useSearchParams().get("next"));
  const providers = useApi("auth-providers", (s) => getAuthProviders(s));
  const { user } = useMe();

  return (
    <div className="mx-auto max-w-md">
      <PageHeader title="로그인" question="로그인하면 샘플 대신 내 토스증권 계좌의 하루하루를 볼 수 있어요." />

      {user && (
        <p role="status" className="mb-6 rounded-2xl bg-surface p-4 text-sm">
          {user.nickname} 님으로 이미 로그인했어요.{" "}
          <Link href={next} className="font-medium text-accent underline-offset-4 hover:underline">
            계속하기
          </Link>
        </p>
      )}

      {providers.state.status === "loading" && <LoadingBlock height={120} label="로그인 방법을 불러오는 중" />}
      {providers.state.status === "error" && <ErrorBlock error={providers.state.error} onRetry={providers.retry} />}
      {providers.state.status === "success" && (
        <div className="space-y-6">
          {providers.state.data.kakao && <KakaoButton next={next} />}
          {providers.state.data.dev && <DevLoginForm next={next} />}
          {!providers.state.data.kakao && !providers.state.data.dev && (
            <p className="rounded-2xl bg-surface p-5 text-sm text-muted">지금은 로그인할 수 있는 방법이 없어요. 잠시 후 다시 시도해 주세요.</p>
          )}
        </div>
      )}

      <TrustNotes />
    </div>
  );
}

function KakaoButton({ next }: { next: string }) {
  const [busy, setBusy] = useState(false);
  return (
    <div>
      <a
        href={KAKAO_LOGIN_PATH}
        aria-disabled={busy || undefined}
        onClick={(e) => {
          if (busy) return e.preventDefault();
          setBusy(true);
          if (!USE_MOCK) return; // 실제: 백엔드 OAuth2로 전체 페이지 이동
          e.preventDefault();
          void mockKakaoLogin().then(() => window.location.assign(next));
        }}
        className={`${primaryButton} w-full`}
      >
        {busy ? "카카오로 이동하는 중…" : "카카오로 시작하기"}
      </a>
      <p className="mt-2 text-center text-xs text-muted">처음이면 카카오 계정으로 바로 가입돼요.</p>
    </div>
  );
}

function DevLoginForm({ next }: { next: string }) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [nickname, setNickname] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const errorId = `${id}-error`;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const name = nickname.trim();
    if (!name) {
      setError("닉네임을 입력해 주세요.");
      inputRef.current?.focus();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await devLogin(name);
      // 세션이 바뀌었으니 숨겨 둔 화면(Activity)까지 모두 새로 받도록 전체 이동한다.
      window.location.assign(next);
    } catch (err) {
      setBusy(false);
      setError(err instanceof ApiError ? err.message : "로그인하지 못했어요.");
      inputRef.current?.focus();
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="rounded-2xl border border-dashed border-border p-5">
      <h2 className="text-sm font-semibold">개발용 로그인</h2>
      <p className="mt-1 text-xs text-muted">로컬 개발 환경에서만 보여요. 닉네임만으로 로그인해요.</p>
      <label htmlFor={`${id}-nickname`} className="mt-4 block text-sm font-medium">
        닉네임
      </label>
      <input
        ref={inputRef}
        id={`${id}-nickname`}
        name="nickname"
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        maxLength={20}
        autoComplete="nickname"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`${input} mt-2`}
      />
      {error && (
        <p id={errorId} className={fieldError}>
          {error}
        </p>
      )}
      <button type="submit" disabled={busy} className={`${secondaryButton} mt-4 w-full`}>
        {busy ? "로그인하는 중…" : "개발용으로 로그인"}
      </button>
      <span aria-live="polite" className="sr-only">
        {busy ? "로그인하는 중이에요" : ""}
      </span>
    </form>
  );
}

function TrustNotes() {
  return (
    <section aria-labelledby="trust-heading" className="mt-10 space-y-3 text-sm">
      <h2 id="trust-heading" className="font-semibold">
        업로그가 내 계좌에서 하는 일
      </h2>
      <ul className="space-y-2 text-muted">
        <li>
          <span className="font-medium text-foreground">읽기만 해요.</span> 보유 종목, 체결 내역, 매수 가능 금액, 환율을 하루 한 번 받아 와요.
        </li>
        <li>
          <span className="font-medium text-foreground">주문하지 않아요.</span> 토스증권 키에는 주문 권한도 있지만, 업로그 서버는 조회 요청만 보낼 수 있게 막혀 있어요.
        </li>
        <li>
          <span className="font-medium text-foreground">키는 암호화해서 보관해요.</span> 연결을 해제하면 저장된 키를 바로 지워요.
        </li>
      </ul>
    </section>
  );
}
