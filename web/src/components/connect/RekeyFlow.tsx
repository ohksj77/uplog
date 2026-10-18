"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { RequireLogin } from "@/components/auth/RequireLogin";
import { ContinueOnPc } from "@/components/connect/ContinueOnPc";
import { EgressIpList } from "@/components/connect/EgressIpList";
import { KeyForm } from "@/components/connect/KeyForm";
import { card, primaryButton } from "@/components/connect/styles";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError } from "@/lib/api/client";
import { getTossSetup, replaceTossCredentials } from "@/lib/api/account-client";
import type { TossCredentials } from "@/lib/api/account-types";
import { useApi } from "@/lib/use-api";
import { useMe } from "@/lib/use-me";

const NEXT = "/connect?mode=rekey";

/**
 * /connect?mode=rekey — 연결한 계좌는 그대로 두고 토스증권 키만 새로 넣는다(PUT /api/connections/toss/credentials).
 * 성공하면 연결 설정으로 돌아가 알림을 보여 준다.
 */
export function RekeyFlow() {
  return (
    <>
      <PageHeader
        title="토스증권 키 다시 넣기"
        question="연결한 계좌와 기록은 그대로 두고, 키만 새로 바꿔요."
        back={{ href: "/settings/connection", label: "연결 설정" }}
      />
      <RequireLogin next={NEXT}>
        <RekeyBody />
      </RequireLogin>
    </>
  );
}

function RekeyBody() {
  const id = useId();
  const router = useRouter();
  const { connectionStatus, refresh } = useMe();
  const setup = useApi("toss-setup", (s) => getTossSetup(s));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const ips = setup.state.status === "success" ? setup.state.data.egressIps : [];

  if (connectionStatus === "NONE") {
    return (
      <section className={`${card} space-y-3`}>
        <p className="font-semibold">연결된 계좌가 없어요</p>
        <p className="text-sm text-muted break-keep">키를 바꿀 계좌가 없어요. 먼저 토스증권 계좌를 연결해 주세요.</p>
        <Link href="/connect" className={`${primaryButton} mt-2 w-full sm:w-auto`}>
          계좌 연결하기
        </Link>
      </section>
    );
  }

  async function submit(credentials: TossCredentials): Promise<ApiError | null> {
    setBusy(true);
    setError(null);
    try {
      await replaceTossCredentials(credentials);
      await refresh();
      router.push("/settings/connection?rekeyed=1");
      return null;
    } catch (e) {
      const err = e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "알 수 없는 오류가 발생했어요.");
      setError(err);
      return err;
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <ContinueOnPc watchConnection={false} nextPath={NEXT} />

      <section aria-labelledby={`${id}-when`} className={`${card} space-y-2 text-sm`}>
        <h2 id={`${id}-when`} className="font-semibold">
          이럴 때 키를 다시 넣어요
        </h2>
        <ul className="list-disc space-y-1 pl-5 text-muted break-keep marker:text-muted">
          <li>같은 키를 다른 도구에서도 써서 업로그의 접속이 끊겼을 때. 업로그 전용 키를 새로 발급해 넣어 주세요.</li>
          <li>토스증권에서 키를 재발급했을 때. 재발급하면 예전 키는 바로 못 쓰게 돼요.</li>
          <li>키가 만료됐을 때. 토스증권 키는 발급 1년 뒤 만료돼요.</li>
        </ul>
        <p className="text-muted break-keep">
          새 키는 토스증권 웹의 설정 &gt; Open API &gt; Open API Key 관리에서 재발급해요. Client Secret은 발급한 순간에만 보이니 바로 복사해 주세요. 새 키는
          연결한 계좌를 연 토스 계정에서 발급해야 해요.
        </p>
      </section>

      <details className="rounded-2xl border border-border p-4 text-sm">
        <summary className="cursor-pointer font-medium outline-none focus-visible:outline-2 focus-visible:outline-accent">허용 IP 다시 확인하기</summary>
        <div className="mt-3 space-y-3">
          <p className="text-muted break-keep">허용 IP 관리에 아래 IP가 그대로 있는지 확인해 주세요. 없으면 &lsquo;IP 추가&rsquo;로 더해 주세요.</p>
          {setup.state.status === "loading" && <LoadingBlock height={64} label="등록할 IP를 불러오는 중" />}
          {setup.state.status === "error" && <ErrorBlock error={setup.state.error} onRetry={setup.retry} />}
          {setup.state.status === "success" && <EgressIpList ips={ips} />}
        </div>
      </details>

      <section aria-labelledby={`${id}-form`} className="space-y-4">
        <h2 id={`${id}-form`} className="text-lg font-semibold">
          새 키 넣기
        </h2>
        <KeyForm
          mode="rekey"
          busy={busy}
          error={error}
          fallbackIps={ips}
          onBack={() => router.push("/settings/connection")}
          backLabel="취소"
          loginNext={NEXT}
          onSubmit={submit}
        />
      </section>
    </div>
  );
}
