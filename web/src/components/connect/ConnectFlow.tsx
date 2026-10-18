"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type ReactNode, type Ref } from "react";
import { RequireLogin } from "@/components/auth/RequireLogin";
import { ConnectDone } from "@/components/connect/ConnectDone";
import { ContinueOnPc, useHandheld } from "@/components/connect/ContinueOnPc";
import { EgressIpList } from "@/components/connect/EgressIpList";
import { KeyForm } from "@/components/connect/KeyForm";
import { RekeyFlow } from "@/components/connect/RekeyFlow";
import { card, primaryButton, secondaryButton, textLink } from "@/components/connect/styles";
import { WtsGuideIllustration } from "@/components/connect/WtsGuideIllustration";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError } from "@/lib/api/client";
import { chooseTossAccount, connectToss, getTossSetup } from "@/lib/api/account-client";
import type { BrokerAccount, TossCredentials, TossSetup } from "@/lib/api/account-types";
import type { ApiState } from "@/lib/use-api";
import { useApi } from "@/lib/use-api";
import { useMe } from "@/lib/use-me";

type Step = 1 | 2 | 3;
type Phase = { kind: "steps"; step: Step } | { kind: "choose"; accounts: BrokerAccount[] } | { kind: "done" };

const STEPS: { step: Step; label: string }[] = [
  { step: 1, label: "키 발급" },
  { step: 2, label: "IP 등록" },
  { step: 3, label: "키 입력" },
];

/** /connect. ?mode=rekey면 연결한 계좌의 키만 다시 넣는 화면을 보여 준다. */
export function ConnectFlow() {
  return (
    <div className="mx-auto max-w-xl">
      {/* ?mode=는 요청마다 달라서 그 아래는 Suspense 안에서 클라이언트가 그린다. */}
      <Suspense fallback={<LoadingBlock height={320} label="연결 화면을 불러오는 중" />}>
        <ConnectByMode />
      </Suspense>
    </div>
  );
}

function ConnectByMode() {
  const rekey = useSearchParams().get("mode") === "rekey";
  if (rekey) return <RekeyFlow />;
  return (
    <>
      <PageHeader title="토스증권 계좌 연결" question="세 단계면 끝나요. 토스증권 웹은 PC에서 열어 두면 편해요." />
      <RequireLogin next="/connect">
        <ConnectBody />
      </RequireLogin>
    </>
  );
}

/** 단계와 "PC에서 이어 하기"를 함께 둔다. PC 연결을 기다리는 동안에는 "이미 연결됨" 안내 대신 카드가 결과를 보여 준다. */
function ConnectBody() {
  const handheld = useHandheld();
  const { connectionStatus } = useMe();
  const [pcWaiting, setPcWaiting] = useState(false);
  const showPc = pcWaiting || connectionStatus === "NONE";
  return (
    <div className="space-y-6">
      {showPc && handheld && <ContinueOnPc onWaitingChange={setPcWaiting} />}
      <ConnectSteps hideAlreadyConnected={pcWaiting} />
      {showPc && !handheld && <ContinueOnPc onWaitingChange={setPcWaiting} />}
    </div>
  );
}

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "알 수 없는 오류가 발생했어요.");
}

function ConnectSteps({ hideAlreadyConnected }: { hideAlreadyConnected: boolean }) {
  const { connectionStatus, refresh } = useMe();
  const setup = useApi("toss-setup", (s) => getTossSetup(s));
  const [phase, setPhase] = useState<Phase>({ kind: "steps", step: 1 });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  // 계좌를 고르는 단계에서 다시 보낼 키. 이 흐름의 메모리에만 두고, 성공·실패·이탈 즉시 지운다.
  const keysRef = useRef<TossCredentials | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const moveFocus = useRef(false);

  const setupData = setup.state.status === "success" ? setup.state.data : null;
  const ips = setupData?.egressIps ?? [];

  // 단계를 옮기면 새 단계 제목으로 초점을 옮긴다(처음 그릴 때는 옮기지 않는다).
  useEffect(() => {
    if (!moveFocus.current) return;
    moveFocus.current = false;
    headingRef.current?.focus();
  }, [phase]);

  // 다른 화면으로 떠나면(Activity로 숨겨짐) 메모리의 키를 지우고, 계좌 고르기 중이었다면 키 입력으로 되돌린다.
  useLayoutEffect(() => {
    return () => {
      keysRef.current = null;
      setPhase((p) => (p.kind === "choose" ? { kind: "steps", step: 3 } : p));
    };
  }, []);

  function goTo(next: Phase) {
    moveFocus.current = true;
    setPhase(next);
  }

  async function submitKeys(credentials: TossCredentials): Promise<ApiError | null> {
    setBusy(true);
    setError(null);
    try {
      const res = await connectToss(credentials);
      if (res.status === "CHOOSE_ACCOUNT") {
        keysRef.current = credentials;
        goTo({ kind: "choose", accounts: res.accounts });
      } else {
        keysRef.current = null;
        goTo({ kind: "done" });
        void refresh();
      }
      return null;
    } catch (e) {
      keysRef.current = null;
      const err = toApiError(e);
      setError(err);
      return err;
    } finally {
      setBusy(false);
    }
  }

  async function submitAccount(accountSeq: string) {
    const keys = keysRef.current;
    if (!keys) {
      setError(new ApiError(0, "KEYS_CLEARED", "보안을 위해 입력한 키를 지웠어요. 키를 다시 입력해 주세요."));
      setPhase({ kind: "steps", step: 3 });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await chooseTossAccount(keys, accountSeq);
      goTo({ kind: "done" });
      void refresh();
    } catch (e) {
      // 키 입력으로 되돌린다. 초점은 KeyForm이 오류 안내 상자로 옮긴다.
      setError(toApiError(e));
      setPhase({ kind: "steps", step: 3 });
    } finally {
      keysRef.current = null;
      setBusy(false);
    }
  }

  if (phase.kind === "done") return <ConnectDone headingRef={headingRef} />;

  if (connectionStatus !== "NONE") {
    if (hideAlreadyConnected) return null;
    return (
      <section className={`${card} space-y-3`}>
        <p className="font-semibold">이미 연결된 계좌가 있어요</p>
        <p className="text-sm text-muted break-keep">계좌는 하나만 연결할 수 있어요. 다른 계좌로 바꾸려면 연결 설정에서 지금 연결을 해제한 뒤 다시 연결해 주세요.</p>
        <div className="flex flex-wrap gap-2 pt-2">
          <Link href="/settings/connection" className={primaryButton}>
            연결 설정 보기
          </Link>
          <Link href="/" className={secondaryButton}>
            내 계좌 로그 보기
          </Link>
        </div>
      </section>
    );
  }

  if (phase.kind === "choose") {
    return (
      <ChooseAccount
        accounts={phase.accounts}
        busy={busy}
        headingRef={headingRef}
        onSubmit={submitAccount}
        onCancel={() => {
          keysRef.current = null;
          goTo({ kind: "steps", step: 3 });
        }}
      />
    );
  }

  const step = phase.step;
  return (
    <div className="space-y-6">
      {setupData?.simulated && <SimulatedNotice />}
      <StepIndicator current={step} />
      {step === 1 && (
        <StepSection n={1} title="토스증권에서 Open API 키 발급하기" headingRef={headingRef}>
          <KeyIssueGuide setup={setup.state} />
          <div className="flex justify-end">
            <button type="button" onClick={() => goTo({ kind: "steps", step: 2 })} className={`${primaryButton} w-full sm:w-auto`}>
              다음: IP 등록
            </button>
          </div>
        </StepSection>
      )}
      {step === 2 && (
        <StepSection n={2} title="업로그 서버 IP 등록하기" headingRef={headingRef}>
          <p className="text-sm break-keep">
            토스증권은 미리 등록한 IP에서 온 요청만 받아요. 같은 Open API 화면 아래쪽 <strong className="font-semibold">허용 IP 관리</strong>에서{" "}
            <strong className="font-semibold">IP 추가</strong>를 눌러 아래 IP를 모두 추가해 주세요. IP는 여러 개 등록할 수 있어서, 이미 등록한 다른 IP는 그대로
            두어도 돼요.
          </p>
          <WtsGuideIllustration focus="ip" />
          {setup.state.status === "loading" && <LoadingBlock height={112} label="등록할 IP를 불러오는 중" />}
          {setup.state.status === "error" && <ErrorBlock error={setup.state.error} onRetry={setup.retry} />}
          {setup.state.status === "success" && <EgressIpList ips={ips} />}
          <p className="text-xs text-muted break-keep">등록하지 않으면 다음 단계의 연결 확인이 실패해요. 등록이 반영되기까지 잠깐 걸릴 수 있어요.</p>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
            <button type="button" onClick={() => goTo({ kind: "steps", step: 1 })} className={secondaryButton}>
              이전
            </button>
            <button type="button" onClick={() => goTo({ kind: "steps", step: 3 })} className={primaryButton}>
              다음: 키 입력
            </button>
          </div>
        </StepSection>
      )}
      {step === 3 && (
        <StepSection n={3} title="키 입력하고 연결 확인하기" headingRef={headingRef}>
          <KeyWarning />
          <KeyForm busy={busy} error={error} fallbackIps={ips} onBack={() => goTo({ kind: "steps", step: 2 })} onSubmit={submitKeys} loginNext="/connect" />
        </StepSection>
      )}
    </div>
  );
}

function StepIndicator({ current }: { current: Step }) {
  return (
    <ol aria-label="연결 단계" className="grid grid-cols-3 gap-2 text-xs">
      {STEPS.map(({ step, label }) => {
        const state = step < current ? "done" : step === current ? "current" : "todo";
        return (
          <li key={step} aria-current={state === "current" ? "step" : undefined} className="space-y-1.5">
            <span className={`block h-1 rounded-full ${state === "todo" ? "bg-border" : "bg-accent"}`} aria-hidden />
            <span className={state === "current" ? "font-semibold text-foreground" : "text-muted"}>
              {step}. {label}
              {state === "done" && <span className="sr-only"> (마침)</span>}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function StepSection({ n, title, headingRef, children }: { n: Step; title: string; headingRef: Ref<HTMLHeadingElement>; children: ReactNode }) {
  return (
    <section aria-labelledby={`connect-step-${n}`} className="space-y-5">
      <h2 id={`connect-step-${n}`} ref={headingRef} tabIndex={-1} className="text-lg font-semibold break-keep outline-none">
        <span className="text-muted">{n}/3</span> {title}
      </h2>
      {children}
    </section>
  );
}

function KeyIssueGuide({ setup }: { setup: ApiState<TossSetup> }) {
  const data = setup.status === "success" ? setup.data : null;
  return (
    <div className="space-y-4">
      <ol className="list-decimal space-y-2 pl-5 text-sm break-keep marker:text-muted">
        <li>PC에서 토스증권 웹(www.tossinvest.com)에 로그인해요.</li>
        <li>
          오른쪽 사이드바 맨 아래의 <strong className="font-semibold">설정(톱니바퀴)</strong>을 누르고, 설정 창 왼쪽 메뉴에서{" "}
          <strong className="font-semibold">Open API</strong>를 골라요.
        </li>
        <li>
          <strong className="font-semibold">Open API Key 관리</strong>에서 키를 발급해요. Client Id는 옆의 <strong className="font-semibold">복사</strong> 버튼으로
          복사할 수 있어요.
        </li>
        <li>
          <strong className="font-semibold">Client Secret은 발급한 그 순간에만 보여요.</strong> 바로 복사해서 업로그에 붙여 넣거나 안전한 곳에 적어 두세요. 나중에는
          tssk_live_******** 처럼 가려져서 다시 볼 수 없고, 잃어버리면 재발급해야 해요.
        </li>
      </ol>
      <WtsGuideIllustration focus="key" />
      {data?.keyPageUrl && (
        <a href={data.keyPageUrl} target="_blank" rel="noopener noreferrer" className={`${textLink} inline-block text-sm`}>
          토스증권 웹 열기<span className="sr-only"> (새 창)</span> ↗
        </a>
      )}
      <div className={`${card} space-y-2 text-sm`}>
        <p className="font-medium">업로그 전용 키를 새로 발급해 주세요</p>
        <p className="text-muted break-keep">
          키 하나에는 접속 토큰이 하나만 유효해요. 다른 도구에서 쓰던 키를 함께 쓰면 서로의 접속을 끊어서, 수집이 실패할 수 있어요. 키는 발급 1년 뒤 만료돼요.
        </p>
        {data && data.notes.length > 0 && (
          <ul className="list-disc space-y-1 pl-5 text-muted marker:text-muted">
            {data.notes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function KeyWarning() {
  return (
    <aside aria-labelledby="key-warning-title" className="space-y-2 rounded-2xl border border-border p-4 text-sm">
      <p id="key-warning-title" className="font-semibold">
        이 키로는 주문도 할 수 있어요
      </p>
      <ul className="list-disc space-y-1 pl-5 text-muted break-keep marker:text-muted">
        <li>토스증권 Open API 키에는 조회만 따로 주는 권한이 없어요. 그래서 키를 아무에게도 보여 주지 마세요.</li>
        <li>업로그는 조회 API만 써요. 서버가 주문 요청 자체를 보낼 수 없게 막혀 있어요.</li>
        <li>키는 암호화해서 보관하고, 연결을 해제하면 바로 지워요.</li>
      </ul>
    </aside>
  );
}

function ChooseAccount({
  accounts,
  busy,
  headingRef,
  onSubmit,
  onCancel,
}: {
  accounts: BrokerAccount[];
  busy: boolean;
  headingRef: Ref<HTMLHeadingElement>;
  onSubmit: (accountSeq: string) => void;
  onCancel: () => void;
}) {
  const [selected, setSelected] = useState<string>(accounts[0]?.accountSeq ?? "");

  function submit(e: FormEvent) {
    e.preventDefault();
    if (selected) onSubmit(selected);
  }

  return (
    <section aria-labelledby="choose-account-title" className="space-y-5">
      <h2 id="choose-account-title" ref={headingRef} tabIndex={-1} className="text-lg font-semibold outline-none">
        연결할 계좌를 골라 주세요
      </h2>
      <p className="text-sm text-muted break-keep">키가 맞아요. 이 키로 볼 수 있는 종합매매 계좌가 여러 개라, 업로그에 연결할 계좌 하나를 골라 주세요.</p>
      <form onSubmit={submit} className="space-y-5">
        <fieldset>
          <legend className="sr-only">연결할 계좌</legend>
          <div className="space-y-2">
            {accounts.map((a) => (
              <label
                key={a.accountSeq}
                className="flex cursor-pointer items-center gap-3 rounded-xl border border-border p-4 has-[:checked]:border-accent has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-accent"
              >
                <input
                  type="radio"
                  name="accountSeq"
                  value={a.accountSeq}
                  checked={selected === a.accountSeq}
                  onChange={() => setSelected(a.accountSeq)}
                  className="size-4 accent-accent"
                />
                <span className="min-w-0">
                  <span className="block font-mono font-medium">{a.maskedAccountNo}</span>
                  <span className="block text-xs text-muted">{a.type === "BROKERAGE" ? "종합매매 계좌" : a.type}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <p aria-live="polite" className="min-h-5 text-sm text-muted">
          {busy ? "계좌를 연결하고 있어요." : ""}
        </p>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
          <button type="button" onClick={onCancel} disabled={busy} className={secondaryButton}>
            키 다시 입력
          </button>
          <button type="submit" disabled={busy || !selected} className={primaryButton}>
            {busy ? "연결하는 중…" : "이 계좌 연결하기"}
          </button>
        </div>
      </form>
    </section>
  );
}

/** 개발용 가짜 토스 서버에 연결되는 환경. 진짜 키를 넣어도 진짜 토스로 가지 않는다는 걸 먼저 알린다. */
function SimulatedNotice() {
  return (
    <aside role="note" aria-label="개발용 가짜 서버 안내" className="space-y-1 rounded-2xl border-2 border-dashed border-border p-4 text-sm break-keep">
      <p className="font-semibold">지금은 개발용 가짜 토스 서버에 연결돼요</p>
      <p className="text-muted">
        진짜 토스증권이 아니라서 연결해도 내 계좌가 아닌 가짜 데이터가 보여요. 진짜 키는 넣지 마세요. 아무 client_id와 client_secret{" "}
        <code className="font-mono">dev</code>로 연결해 볼 수 있어요.
      </p>
    </aside>
  );
}
