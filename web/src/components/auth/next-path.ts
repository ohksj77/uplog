/** ?next= 값을 같은 사이트 안의 경로로만 받는다(열린 리다이렉트 방지). */
export function safeNextPath(raw: string | null | undefined, fallback = "/"): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) return fallback;
  // 로그인 화면으로 되돌아가는 고리는 끊는다.
  if (raw === "/login" || raw.startsWith("/login?") || raw.startsWith("/login/")) return fallback;
  return raw;
}

export function loginHref(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}
