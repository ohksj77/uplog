"use client";

import { useEffect, useState } from "react";
import { searchEtfs } from "@/lib/api/client";
import type { Etf } from "@/lib/api/types";

const placeholder = (code: string): Etf => ({ code, name: code, currency: "KRW", sample: false });

/** URL에는 종목코드만 있으므로, 칩에 보일 이름을 검색 API로 채운다. 못 찾으면 코드를 그대로 보인다. */
export function useResolvedEtfs(initialCodes: string[]) {
  const [etfs, setEtfs] = useState<Etf[]>(() => initialCodes.map(placeholder));
  const [initial] = useState(initialCodes);

  useEffect(() => {
    if (initial.length === 0) return;
    const controller = new AbortController();
    Promise.all(
      initial.map((code) =>
        searchEtfs(code, controller.signal)
          .then((list) => list.find((e) => e.code === code))
          .catch(() => undefined),
      ),
    ).then((found) => {
      if (controller.signal.aborted) return;
      const byCode = new Map(found.filter((e) => e !== undefined).map((e) => [e.code, e]));
      setEtfs((prev) =>
        prev.map((e) => {
          const hit = byCode.get(e.code);
          return hit && e.name === e.code ? { code: hit.code, name: hit.name, currency: hit.currency, sample: hit.sample } : e;
        }),
      );
    });
    return () => controller.abort();
  }, [initial]);

  return [etfs, setEtfs] as const;
}
