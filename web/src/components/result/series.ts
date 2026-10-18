import type { Strategy } from "@/components/result/text";

// 차트에서 두 방식을 구분하는 색(globals.css의 --series-*). 빨강·파랑은 이익·손실 의미로 남겨 두고 청록·호박색을 쓴다.
export const STRATEGY_COLOR: Record<Strategy, string> = {
  lumpSum: "text-series-lump",
  recurring: "text-series-recurring",
};

export const PRINCIPAL_COLOR = "text-muted";
