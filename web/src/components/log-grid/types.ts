import type { SimulationDay } from "@/lib/api/types";

/**
 * 로그 보기 전환 (기획서 기능 A):
 * - daily: 그날 수익률(dailyReturn)
 * - cumulative: 그날 기준 누적 수익률(value / invested − 1)
 * - costBasis: 매수한 날만 칠함 — 마지막 종가 / 그날 종가 − 1 (하락장에 산 칸이 가장 빨개짐)
 */
export type LogMode = "daily" | "cumulative" | "costBasis";

export type LogGridProps = {
  days: SimulationDay[];
  mode: LogMode;
  /** 스크린리더용 이름, 예: "KODEX 200 적립식 로그" */
  label: string;
  /** 칸을 누르거나 포커스했을 때. 결과 화면이 상세 정보를 띄울 때 쓴다. */
  onSelectDay?: (day: SimulationDay | null) => void;
  selectedDate?: string | null;
  /** 여러 로그를 위아래로 맞출 때 쓰는 공통 표시 구간. days 밖의 칸은 비워 둔다. */
  range?: { from: string; to: string };
};
