"use client";

import { useSearchParams } from "next/navigation";
import { DEFAULT_PARAMS, parseSearchParams } from "@/lib/simulation-params";
import { SimulationForm } from "@/components/simulation-form/SimulationForm";

/** 결과 화면의 "조건 바꾸기"(/?<같은 쿼리>)로 돌아오면 그 값으로 채운다. <Suspense> 안에서 쓴다. */
export function SimulationFormFromUrl() {
  const query = useSearchParams().toString();
  const parsed = parseSearchParams(new URLSearchParams(query));
  return <SimulationForm key={query} initial={parsed.ok ? parsed.params : DEFAULT_PARAMS} />;
}
