import Link from "next/link";
import type { Ref } from "react";
import { loginHref } from "@/components/auth/next-path";
import { connectErrorView } from "@/components/connect/connect-errors";
import { EgressIpList } from "@/components/connect/EgressIpList";
import { textLink } from "@/components/connect/styles";
import type { ApiError } from "@/lib/api/client";
import { AccountApiError } from "@/lib/api/account-types";

type Props = {
  id: string;
  error: ApiError;
  /** 오류 본문에 IP가 없을 때 쓸 IP(setup 응답) */
  fallbackIps: string[];
  ref?: Ref<HTMLDivElement>;
};

/** 연결 오류 안내. 오류가 나면 이 상자로 초점을 옮겨 바로 읽히게 한다. */
export function ConnectErrorPanel({ id, error, fallbackIps, ref }: Props) {
  const view = connectErrorView(error);
  const ips = error instanceof AccountApiError && error.egressIps.length > 0 ? error.egressIps : fallbackIps;
  return (
    <div ref={ref} id={id} tabIndex={-1} className="space-y-3 rounded-2xl border border-border bg-surface p-4 outline-none focus-visible:outline-2 focus-visible:outline-accent">
      <p className="font-semibold">{view.title}</p>
      <p className="text-sm text-muted break-keep">{view.body}</p>
      {view.help === "ip" && <EgressIpList ips={ips} />}
      {view.help === "settings" && (
        <Link href="/settings/connection" className={`${textLink} inline-block text-sm`}>
          연결 설정으로 가기
        </Link>
      )}
      {view.help === "login" && (
        <Link href={loginHref("/connect")} className={`${textLink} inline-block text-sm`}>
          다시 로그인하기
        </Link>
      )}
    </div>
  );
}
