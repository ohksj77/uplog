import Link from "next/link";
import type { Ref } from "react";
import { loginHref } from "@/components/auth/next-path";
import { connectErrorView, type KeyFormMode } from "@/components/connect/connect-errors";
import { EgressIpList } from "@/components/connect/EgressIpList";
import { primaryButton, textLink } from "@/components/connect/styles";
import { WtsGuideIllustration } from "@/components/connect/WtsGuideIllustration";
import type { ApiError } from "@/lib/api/client";
import { AccountApiError } from "@/lib/api/account-types";

type Props = {
  id: string;
  error: ApiError;
  /** 오류 본문에 IP가 없을 때 쓸 IP(setup 응답) */
  fallbackIps: string[];
  mode?: KeyFormMode;
  /** 로그인이 풀렸을 때 돌아올 경로 */
  loginNext?: string;
  /** IP 오류에서, 이 흐름의 메모리에 둔 키로 다시 확인한다. 키가 남아 있을 때만 준다. */
  onRecheck?: () => void;
  busy?: boolean;
  ref?: Ref<HTMLDivElement>;
};

/** 연결 오류 안내. 오류가 나면 이 상자로 초점을 옮겨 바로 읽히게 한다. */
export function ConnectErrorPanel({ id, error, fallbackIps, mode = "connect", loginNext = "/connect", onRecheck, busy = false, ref }: Props) {
  const view = connectErrorView(error, mode);
  const ips = error instanceof AccountApiError && error.egressIps.length > 0 ? error.egressIps : fallbackIps;
  return (
    <div ref={ref} id={id} tabIndex={-1} className="space-y-3 rounded-2xl border border-border bg-surface p-4 outline-none focus-visible:outline-2 focus-visible:outline-accent">
      <p className="font-semibold">{view.title}</p>
      <p className="text-sm text-muted break-keep">{view.body}</p>
      {view.help === "ip" && (
        <>
          <WtsGuideIllustration focus="ip" />
          <EgressIpList ips={ips} />
          {onRecheck && (
            <button type="button" onClick={onRecheck} disabled={busy} className={`${primaryButton} w-full`}>
              {busy ? "다시 확인하는 중…" : "허용 IP를 등록했어요 · 다시 확인"}
            </button>
          )}
          {onRecheck && <p className="text-xs text-muted break-keep">방금 넣은 키는 이 화면에서만 10분 동안 기억해 둬요. 키를 다시 넣지 않아도 돼요.</p>}
        </>
      )}
      {view.help === "settings" && (
        <Link href="/settings/connection" className={`${textLink} inline-block text-sm`}>
          연결 설정으로 가기
        </Link>
      )}
      {view.help === "login" && (
        <Link href={loginHref(loginNext)} className={`${textLink} inline-block text-sm`}>
          다시 로그인하기
        </Link>
      )}
    </div>
  );
}
