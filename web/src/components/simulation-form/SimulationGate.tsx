"use client";

import type { ReactNode } from "react";
import { useMe } from "@/lib/use-me";
import { ConnectFirst } from "@/components/simulation-form/ConnectFirst";

type SimulationGateProps = {
  children: ReactNode;
  /** 로그인·연결 상태를 불러오는 동안 보일 것 */
  fallback: ReactNode;
  asPageTitle?: boolean;
};

/** 계좌를 연결한 사용자에게만 children을 보여 준다. 비로그인·미연결이면 연결 안내. */
export function SimulationGate({ children, fallback, asPageTitle }: SimulationGateProps) {
  const { user, connectionStatus } = useMe();
  if (user === undefined) return <>{fallback}</>;
  if (user === null) return <ConnectFirst loggedIn={false} asPageTitle={asPageTitle} />;
  if (connectionStatus === "NONE") return <ConnectFirst loggedIn asPageTitle={asPageTitle} />;
  return <>{children}</>;
}
