"use client";

import { useEffect, useState } from "react";
import { searchInstruments } from "@/lib/api/client";
import type { Etf } from "@/lib/api/types";

// 이름을 찾기 전 자리표시. 시장은 코드 모양으로 짐작한다(국내는 6자리, 숫자로 시작).
const placeholder = (code: string): Etf => {
  const kr = /^[0-9][0-9A-Z]{5}$/.test(code);
  return { code, name: code, market: kr ? "KR" : "US", type: "ETF", currency: kr ? "KRW" : "USD" };
};

/** URL에는 종목코드만 있으므로, 칩에 보일 이름을 검색 API로 채운다. 못 찾으면 코드를 그대로 보인다. */
export function useResolvedEtfs(initialCodes: string[]) {
  const [etfs, setEtfs] = useState<Etf[]>(() => initialCodes.map(placeholder));
  const [initial] = useState(initialCodes);

  useEffect(() => {
    if (initial.length === 0) return;
    const controller = new AbortController();
    Promise.all(
      initial.map((code) =>
        searchInstruments(code, controller.signal)
          .then((list) => list.find((e) => e.code === code))
          .catch(() => undefined),
      ),
    ).then((found) => {
      if (controller.signal.aborted) return;
      const byCode = new Map(found.filter((e) => e !== undefined).map((e) => [e.code, e]));
      setEtfs((prev) =>
        prev.map((e) => {
          const hit = byCode.get(e.code);
          return hit && e.name === e.code
            ? { code: hit.code, name: hit.name, market: hit.market, type: hit.type, currency: hit.currency }
            : e;
        }),
      );
    });
    return () => controller.abort();
  }, [initial]);

  return [etfs, setEtfs] as const;
}
