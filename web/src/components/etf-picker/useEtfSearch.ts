"use client";

import { useEffect, useState } from "react";
import { ApiError, searchInstruments } from "@/lib/api/client";
import type { EtfListItem } from "@/lib/api/types";

const DEBOUNCE_MS = 200;

type Result = { query: string; items: EtfListItem[]; error: null } | { query: string; items: null; error: string };

export type EtfSearchState =
  | { status: "idle" | "loading"; items: EtfListItem[] }
  | { status: "done"; items: EtfListItem[] }
  | { status: "error"; items: EtfListItem[]; error: string };

/** enabled일 때만 검색한다. 입력이 멈추고 DEBOUNCE_MS 뒤에 요청하고, 이전 요청은 취소한다. */
export function useEtfSearch(query: string, enabled: boolean): EtfSearchState {
  const q = query.trim();
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchInstruments(q, controller.signal)
        .then((items) => setResult({ query: q, items, error: null }))
        .catch((e: unknown) => {
          if (controller.signal.aborted) return;
          const message = e instanceof ApiError ? e.message : "종목 목록을 불러오지 못했어요.";
          setResult({ query: q, items: null, error: message });
        });
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [q, enabled]);

  if (!enabled) return { status: "idle", items: [] };
  // 이전 검색어의 결과는 새 결과가 올 때까지 그대로 보여 준다.
  const stale = result?.items ?? [];
  if (!result || result.query !== q) return { status: "loading", items: stale };
  if (result.error !== null) return { status: "error", items: [], error: result.error };
  return { status: "done", items: result.items };
}
