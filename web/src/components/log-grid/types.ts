import type { LogLevel } from "@/components/log-grid/logic";
import type { SimulationDay } from "@/lib/api/types";
import type { MoneyCurrency } from "@/lib/format";

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
  /** 평가금액을 나타낼 통화(시뮬레이션 기준 통화). 기본 KRW */
  currency?: MoneyCurrency;
};

// ── 범용 격자(LogGridBase) ──────────────────────────────────────────────

/** 툴팁 한 줄. tone이 있으면 값을 이익 빨강 / 손실 파랑으로 칠한다. */
export type LogTooltipRow = { label: string; value: string; tone?: "gain" | "loss" | "flat" };

export type LogTooltip = { title: string; rows: LogTooltipRow[]; note?: string };

/** 격자 칸 하나. 날짜 오름차순 배열로 넘긴다. */
export type LogGridCell = {
  date: string;
  /** 칠할 단계(logColor). null이면 중립 색 */
  level: LogLevel | null;
  /** 기록 없는 날: 칠하지 않고 테두리만 그린다 */
  hollow?: boolean;
  /** 가운데 점(예: 매수일) */
  marker?: "dot" | null;
  /** 스크린리더 문구(툴팁과 같은 정보) */
  label: string;
  tooltip: LogTooltip;
  /** false면 눌러도 onActivate를 부르지 않는다. 기본 true */
  activatable?: boolean;
};

/** 격자 아래에 그리는 기간 표시(예: "체결 내역으로 복원"). 시각 보조이고, 같은 정보는 칸 문구에도 넣는다. */
export type LogGridBand = { from: string; to: string; label: string };

export type LogGridBaseProps = {
  cells: readonly LogGridCell[];
  /** 스크린리더용 격자 이름 */
  label: string;
  selectedDate?: string | null;
  /** 표시 구간. cells 밖의 칸은 비워 둔다. */
  range?: { from: string; to: string };
  /** 칸을 누르거나 Enter/Space. 인덱스는 cells 기준 */
  onActivate?: (index: number) => void;
  /** 고른 날이 있을 때 Esc */
  onClear?: () => void;
  bands?: readonly LogGridBand[];
  /** cells가 비었을 때 문구 */
  emptyText?: string;
};
