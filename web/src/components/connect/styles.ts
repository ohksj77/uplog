// 로그인·연결 화면이 함께 쓰는 버튼·입력 클래스. 색은 토큰만 쓴다.

const focus = "outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

export const primaryButton = `inline-flex h-12 items-center justify-center rounded-xl bg-accent px-5 text-sm font-semibold text-white disabled:opacity-60 ${focus}`;

export const secondaryButton = `inline-flex h-12 items-center justify-center rounded-xl border border-border px-5 text-sm font-semibold hover:bg-surface disabled:opacity-60 ${focus}`;

export const textLink = `font-medium text-accent underline-offset-4 hover:underline ${focus} rounded`;

export const input = `h-12 w-full min-w-0 rounded-xl border border-border bg-background px-4 text-base aria-[invalid=true]:border-foreground ${focus}`;

/** 입력 아래 오류 문구. 이익/손실 색(빨강·파랑)과 헷갈리지 않게 색 대신 굵기와 기호로 구분한다. */
export const fieldError = "mt-2 text-sm font-medium text-foreground before:mr-1 before:content-['!']";

export const card = "rounded-2xl bg-surface p-5";
