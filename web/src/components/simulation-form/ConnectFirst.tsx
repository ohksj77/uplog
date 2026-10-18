import Link from "next/link";

type ConnectFirstProps = {
  /** 결과 화면처럼 페이지 제목이 따로 없을 때 h1으로 그린다 */
  asPageTitle?: boolean;
};

/**
 * 시뮬레이션은 실제 종목 시세를 사용자 자신이 연결한 토스증권 키로 받는다. 그래서 연결 전이면
 * 입력 폼 대신 연결부터 안내한다(엔진도 403 SIMULATION_REQUIRES_CONNECTION).
 */
export function ConnectFirst({ asPageTitle = false }: ConnectFirstProps) {
  const Heading = asPageTitle ? "h1" : "h2";
  return (
    <section aria-labelledby="connect-first-heading" className="rounded-2xl bg-surface px-5 py-8">
      <Heading id="connect-first-heading" className="text-lg font-semibold break-keep">
        계좌를 연결하면 시뮬레이션할 수 있어요
      </Heading>
      <p className="mt-2 text-sm leading-relaxed text-muted break-keep">
        국내·미국 ETF와 주식의 실제 시세는 연결한 토스증권 키로 받아요. 그래서 시뮬레이션은 내 계좌를 연결한 뒤에 쓸 수
        있어요.
      </p>
      <ul className="mt-4 space-y-1.5 text-sm text-muted">
        <li>· 키는 시세·계좌 조회에만 쓰고 주문은 보내지 않아요.</li>
        <li>· 키는 이 맥의 키체인으로 보호되고, 이 맥 밖으로 나가지 않아요.</li>
      </ul>
      <Link
        href="/connect"
        className="mt-6 inline-flex h-12 w-full items-center justify-center rounded-xl bg-accent text-base font-semibold text-white hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:w-auto sm:px-6"
      >
        토스증권 계좌 연결하기
      </Link>
    </section>
  );
}
