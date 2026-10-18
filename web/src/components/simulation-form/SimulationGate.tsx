"use client";

import type { ReactNode } from "react";
import { useMe } from "@/lib/use-me";
import { ConnectFirst } from "@/components/simulation-form/ConnectFirst";

type SimulationGateProps = {
  children: ReactNode;
  /** 연결 상태를 불러오는 동안 보일 것 */
  fallback: ReactNode;
  asPageTitle?: boolean;
};

/** 계좌를 연결했으면 children을, 연결 전이면 연결 안내를 보여 준다. */
export function SimulationGate({ children, fallback, asPageTitle }: SimulationGateProps) {
  const { loading, connectionStatus } = useMe();
  if (loading) return <>{fallback}</>;
  if (connectionStatus === "NONE") return <ConnectFirst asPageTitle={asPageTitle} />;
  return <>{children}</>;
}
