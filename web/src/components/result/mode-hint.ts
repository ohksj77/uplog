import type { LogMode } from "@/components/log-grid/types";

/** 로그 보기마다 한 줄 설명. 토글 자체는 @/components/log-grid 의 LogModeToggle을 쓴다. */
export const MODE_HINT: Record<LogMode, string> = {
  daily: "그날 하루 수익률이에요.",
  cumulative: "그날까지 넣은 돈 대비 수익률이에요.",
  costBasis: "산 날만 칠해요. 그날 산 몫이 지금 몇 % 올랐는지예요.",
};
