// 기능 B(계좌 연결) 진입점. 아직 없으므로 버튼은 비활성.
export function AccountCta() {
  return (
    <section aria-labelledby="account-cta-heading" className="rounded-2xl bg-surface p-5">
      <h2 id="account-cta-heading" className="text-base font-semibold">
        내 실제 계좌는 어땠을까?
      </h2>
      <p className="mt-1 text-sm text-muted">계좌를 연결하면 이 시뮬레이션과 실제 기록을 나란히 비교할 수 있어요.</p>
      <button
        type="button"
        disabled
        aria-disabled
        className="mt-4 h-11 w-full rounded-xl bg-border text-sm font-semibold text-muted sm:w-auto sm:px-5"
      >
        계좌 연결하고 비교하기 · 준비 중
      </button>
    </section>
  );
}
