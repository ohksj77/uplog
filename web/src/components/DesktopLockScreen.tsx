"use client";

import { useEffect, useRef, useSyncExternalStore } from "react";
import { isDesktopLocked, subscribeDesktopLock } from "@/lib/desktop-lock";

/**
 * 엔진이 403 DESKTOP_LOCKED를 주면(앱 창 밖 브라우저에서 열었거나, 앱을 다시 실행해 이 창의 열쇠가 바뀐 경우)
 * 화면 전체를 덮어 앱에서 열라고 안내한다. 그 아래 화면은 더 쓸 수 없으므로 inert로 막는다.
 */
export function DesktopLockScreen() {
  const locked = useSyncExternalStore(subscribeDesktopLock, isDesktopLocked, () => false);
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (!locked) return;
    const siblings = Array.from(document.body.children).filter((el) => el.id !== "desktop-lock");
    siblings.forEach((el) => el.setAttribute("inert", ""));
    headingRef.current?.focus();
    return () => siblings.forEach((el) => el.removeAttribute("inert"));
  }, [locked]);

  if (!locked) return null;
  return (
    <div
      id="desktop-lock"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="desktop-lock-title"
      aria-describedby="desktop-lock-body"
      className="fixed inset-0 z-50 flex items-center justify-center bg-background px-4"
    >
      <div className="w-full max-w-md space-y-3 text-center">
        <h1 id="desktop-lock-title" ref={headingRef} tabIndex={-1} className="text-xl font-bold tracking-tight outline-none">
          UpLog 앱에서 열어 주세요
        </h1>
        <div id="desktop-lock-body" className="space-y-2 text-sm text-muted break-keep">
          <p>이 화면은 UpLog 앱 창에서만 열 수 있어요. 내 계좌 기록과 키를 지키기 위해서예요.</p>
          <p>
            맥의 메뉴 막대나 Dock에서 UpLog를 열어 주세요. 앱 창인데도 이 안내가 보이면 앱을 종료한 뒤 다시 열어 주세요.
          </p>
        </div>
      </div>
    </div>
  );
}
