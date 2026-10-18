"use client";

import { useEffect, useRef, useState } from "react";
import { CopyButton } from "@/components/connect/CopyButton";
import { secondaryButton } from "@/components/connect/styles";
import { ApiError } from "@/lib/api/client";
import { getPublicIp } from "@/lib/api/account-client";

type State = { status: "idle" } | { status: "loading" } | { status: "success"; ip: string } | { status: "error"; message: string };

/**
 * 이 맥의 공인 IP. WTS 허용 IP 관리에 등록할 값이다.
 * 밖으로 나가는 요청(엔진 → api.ipify.org)이라 사용자가 누를 때만 묻는다.
 */
export function PublicIpCheck() {
  const [state, setState] = useState<State>({ status: "idle" });
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function check() {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setState({ status: "loading" });
    try {
      const { ip } = await getPublicIp(controller.signal);
      if (!controller.signal.aborted) setState({ status: "success", ip });
    } catch (e) {
      if (controller.signal.aborted) return;
      const message =
        e instanceof ApiError && e.status !== 0
          ? e.message
          : "공인 IP를 확인하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.";
      setState({ status: "error", message });
    }
  }

  const busy = state.status === "loading";
  return (
    <div className="space-y-3">
      {state.status === "success" ? (
        <div className="flex items-center justify-between gap-3 rounded-xl border border-border bg-background px-4 py-2">
          <span className="min-w-0">
            <span className="block text-xs text-muted">이 맥의 공인 IP</span>
            <code className="font-mono text-base break-all select-all">{state.ip}</code>
          </span>
          <CopyButton value={state.ip} label={`IP ${state.ip}`} />
        </div>
      ) : null}
      <button type="button" onClick={check} disabled={busy} className={`${secondaryButton} w-full sm:w-auto`}>
        {busy ? "확인하는 중…" : state.status === "success" ? "다시 확인" : "내 공인 IP 확인"}
      </button>
      <p aria-live="polite" className={state.status === "error" ? "text-sm font-medium break-keep" : "sr-only"}>
        {state.status === "error" ? state.message : state.status === "success" ? `이 맥의 공인 IP는 ${state.ip}예요.` : ""}
      </p>
      <p className="text-xs text-muted break-keep">
        집 인터넷의 공인 IP는 공유기를 다시 켜거나 통신사 사정으로 바뀔 수 있어요. 바뀌면 수집이 멈추니, 그때는 여기서 다시 확인하고
        토스증권 웹의 설정(톱니바퀴) &gt; Open API &gt; 허용 IP 관리에서 &lsquo;IP 추가&rsquo;로 새 IP를 더해 주세요.
      </p>
    </div>
  );
}
