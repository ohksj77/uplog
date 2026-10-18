"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense, useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { loginHref } from "@/components/auth/next-path";
import { logout } from "@/lib/api/account-client";
import { useMe } from "@/lib/use-me";

const iconButton =
  "inline-flex size-9 shrink-0 items-center justify-center rounded-lg text-muted hover:bg-surface hover:text-foreground outline-none focus-visible:outline-2 focus-visible:outline-accent";

/**
 * 헤더 오른쪽 사용자 메뉴. 비로그인은 로그인 링크, 로그인은 닉네임 첫 글자 버튼 → 펼침 메뉴.
 * 360px에서는 아이콘 크기(36px)만 차지하고, sm 이상에서 글자를 함께 보여 준다.
 */
export function UserMenu() {
  return (
    <Suspense fallback={<span className="size-9 shrink-0" aria-hidden />}>
      <UserMenuInner />
    </Suspense>
  );
}

function UserMenuInner() {
  const pathname = usePathname();
  const { state, user } = useMe();

  if (state.status === "loading") return <span className="size-9 shrink-0" aria-hidden />;
  if (!user) {
    const next = pathname && pathname !== "/login" ? pathname : "/";
    return (
      <Link href={loginHref(next)} aria-label="로그인" className={`${iconButton} sm:w-auto sm:px-3 sm:text-sm sm:font-medium`}>
        <LoginIcon />
        <span className="hidden sm:inline">로그인</span>
      </Link>
    );
  }
  const connection = state.status === "success" ? state.data.connection : null;
  return <AccountMenu nickname={user.nickname} connection={connection} />;
}

type Connection = { status: string; maskedAccountNo: string | null } | null;

function AccountMenu({ nickname, connection }: { nickname: string; connection: Connection }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const connected = connection != null && connection.status !== "NONE";

  // 다른 화면으로 떠나면(Activity로 숨겨짐) 메뉴를 닫는다.
  useLayoutEffect(() => () => setOpen(false), []);

  // 바깥을 누르거나 Esc를 누르면 닫는다. Esc는 버튼으로 초점을 돌려준다.
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    const onFocus = (e: FocusEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    document.addEventListener("focusin", onFocus);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("focusin", onFocus);
    };
  }, [open]);

  async function onLogout() {
    setBusy(true);
    try {
      await logout();
    } finally {
      // 세션·계좌가 바뀌었으니 숨겨 둔 화면(Activity)의 이전 계좌 데이터까지 버리도록 전체 이동한다.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- 의도한 전체 새로고침
      window.location.assign("/");
    }
  }

  const initial = Array.from(nickname.trim())[0] ?? "?";
  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        aria-controls={`${id}-menu`}
        aria-label={`${nickname} 님 계정 메뉴`}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex h-9 shrink-0 items-center gap-2 rounded-lg px-0.5 text-sm hover:bg-surface outline-none focus-visible:outline-2 focus-visible:outline-accent sm:pr-2"
      >
        <span aria-hidden className="inline-flex size-8 items-center justify-center rounded-full bg-surface font-semibold text-foreground">
          {initial}
        </span>
        <span aria-hidden className="hidden max-w-24 truncate sm:inline">
          {nickname}
        </span>
      </button>
      <div
        id={`${id}-menu`}
        hidden={!open}
        className="absolute top-full right-0 z-20 mt-2 w-60 max-w-[calc(100vw-2rem)] rounded-xl border border-border bg-background p-1.5 shadow-lg"
      >
        <div className="px-3 py-2">
          <p className="truncate font-semibold">{nickname} 님</p>
          <p className="mt-0.5 text-xs text-muted">
            {connected ? (
              <>
                토스증권 <span className="font-mono">{connection?.maskedAccountNo ?? ""}</span>
              </>
            ) : (
              "연결된 계좌가 없어요"
            )}
          </p>
        </div>
        <ul className="border-t border-border pt-1.5 text-sm">
          <li>
            <Link
              href={connected ? "/settings/connection" : "/connect"}
              onNavigate={() => setOpen(false)}
              className="block rounded-lg px-3 py-2.5 hover:bg-surface outline-none focus-visible:outline-2 focus-visible:outline-accent"
            >
              {connected ? "계좌 연결 설정" : "계좌 연결하기"}
            </Link>
          </li>
          <li>
            <button
              type="button"
              onClick={onLogout}
              disabled={busy}
              className="block w-full rounded-lg px-3 py-2.5 text-left hover:bg-surface outline-none focus-visible:outline-2 focus-visible:outline-accent"
            >
              {busy ? "로그아웃하는 중…" : "로그아웃"}
            </button>
          </li>
        </ul>
      </div>
    </div>
  );
}

function LoginIcon() {
  return (
    <svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden className="sm:hidden">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </svg>
  );
}
