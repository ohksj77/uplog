// 엔진은 업로그 앱 창이 받은 쿠키가 없는 /api 요청을 403 DESKTOP_LOCKED로 막는다(docs/design/desktop-app.md "실행 계약").
// 브라우저에서 주소를 직접 열었거나, 앱을 다시 실행해 토큰이 바뀐 경우다. 한 번이라도 받으면 화면 전체를 안내로 덮는다
// (components/DesktopLockScreen). fetch 층(lib/api/client.ts)이 알려 준다.

export const DESKTOP_LOCKED = "DESKTOP_LOCKED";

let locked = false;
const listeners = new Set<() => void>();

export function markDesktopLocked() {
  if (locked) return;
  locked = true;
  listeners.forEach((l) => l());
}

export function subscribeDesktopLock(onChange: () => void) {
  listeners.add(onChange);
  return () => {
    listeners.delete(onChange);
  };
}

export function isDesktopLocked(): boolean {
  return locked;
}
