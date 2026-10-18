"use client";

import { useSearchParams } from "next/navigation";
import { useId, useMemo, useState } from "react";
import type { LogMode } from "@/components/log-grid/types";
import { AccountCta } from "@/components/result/AccountCta";
import { ComparisonTable } from "@/components/result/ComparisonTable";
import { EtfResult } from "@/components/result/EtfResult";
import { EtfTabs } from "@/components/result/EtfTabs";
import { ResultError } from "@/components/result/ResultError";
import { ResultHeader } from "@/components/result/ResultHeader";
import { ResultSkeleton } from "@/components/result/ResultSkeleton";
import { useSimulation } from "@/components/result/use-simulation";
import type { SimulationParams, SimulationResponse } from "@/lib/api/types";
import { parseSearchParams, toSearchParams } from "@/lib/simulation-params";

// 결과 화면 본체. URL 쿼리를 읽어 브라우저에서 시뮬레이션을 부른다.
export function SimulationResultView() {
  const searchParams = useSearchParams();
  const query = searchParams.toString();
  const parsed = useMemo(() => parseSearchParams(new URLSearchParams(query)), [query]);
  const { state, retry } = useSimulation(parsed.ok ? parsed.params : null);

  if (!parsed.ok) {
    return <ResultError title="조건을 다시 확인해 주세요" message={parsed.error} />;
  }
  if (state.status === "loading") return <ResultSkeleton />;
  if (state.status === "error") {
    return (
      <ResultError
        title="결과를 불러오지 못했어요"
        message={state.error.message}
        onRetry={retry}
        backQuery={toSearchParams(parsed.params).toString()}
      />
    );
  }
  return <ResultBody params={parsed.params} data={state.data} />;
}

function ResultBody({ params, data }: { params: SimulationParams; data: SimulationResponse }) {
  const [selected, setSelected] = useState(0);
  const [mode, setMode] = useState<LogMode>("daily");
  const baseId = useId();
  const tabId = (i: number) => `${baseId}-tab-${i}`;
  const panelId = (i: number) => `${baseId}-panel-${i}`;
  const index = Math.min(selected, data.results.length - 1);
  const current = data.results[index];
  const multi = data.results.length > 1;

  if (!current) {
    return <ResultError title="결과가 비어 있어요" message="고른 ETF의 결과를 받지 못했어요." />;
  }

  return (
    <div className="space-y-8">
      <ResultHeader params={params} from={data.from} to={data.to} />
      {multi && <ComparisonTable results={data.results} selected={index} />}
      {multi && <EtfTabs etfs={data.results.map((r) => r.etf)} selected={index} onSelect={setSelected} tabId={tabId} panelId={panelId} />}
      <div
        id={panelId(index)}
        role={multi ? "tabpanel" : undefined}
        aria-labelledby={multi ? tabId(index) : undefined}
      >
        <EtfResult key={current.etf.code} result={current} mode={mode} onModeChange={setMode} />
      </div>
      <AccountCta />
      <p className="text-xs leading-relaxed text-muted">{data.disclaimer}</p>
    </div>
  );
}
