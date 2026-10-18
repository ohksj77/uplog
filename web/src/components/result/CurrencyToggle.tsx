"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useId } from "react";
import { CURRENCY_HINT } from "@/components/result/text";
import { SegmentedControl, type SegmentOption } from "@/components/ui/SegmentedControl";
import type { Currency } from "@/lib/api/types";

const OPTIONS: SegmentOption<Currency>[] = [
  { value: "KRW", label: "원화" },
  { value: "USD", label: "달러" },
];

/**
 * 결과 금액의 기준 통화(원화 / 달러). 같은 매수를 어느 통화로 볼지만 바꾼다.
 * 선택은 URL에 currency=USD로 남기고(원화는 기본값이라 뺀다), 쿼리가 바뀌면 결과를 다시 부른다.
 */
export function CurrencyToggle({ value }: { value: Currency }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const hintId = useId();

  const choose = (next: Currency) => {
    const q = new URLSearchParams(searchParams.toString());
    if (next === "USD") q.set("currency", "USD");
    else q.delete("currency");
    router.replace(`${pathname}?${q}`, { scroll: false });
  };

  return (
    <div className="space-y-1.5">
      <div className="w-44">
        <SegmentedControl legend="금액 기준" hideLegend options={OPTIONS} value={value} onChange={choose} describedBy={hintId} />
      </div>
      <p id={hintId} className="text-xs text-muted break-keep">
        {CURRENCY_HINT[value]}
      </p>
    </div>
  );
}
