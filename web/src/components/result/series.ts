import type { Strategy } from "@/components/result/text";

// 차트에서 두 방식을 구분하는 색(globals.css의 --series-*). 초록·빨강은 이익·손실 의미로 남겨 둔다.
export const STRATEGY_COLOR: Record<Strategy, string> = {
  lumpSum: "text-series-lump",
  recurring: "text-series-recurring",
};

export const PRINCIPAL_COLOR = "text-muted";
