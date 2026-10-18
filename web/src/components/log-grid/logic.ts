// 로그 계산 — React 없는 순수 함수. 단위 테스트는 이 파일만 대상으로 하면 된다.
import type { LogMode } from "@/components/log-grid/types";
import type { SimulationDay } from "@/lib/api/types";
import { formatDate, formatPercent, formatWon } from "@/lib/format";

/** −4(가장 큰 손실) … 0(변동 없음) … 4(가장 큰 이익). */
export type LogLevel = -4 | -3 | -2 | -1 | 0 | 1 | 2 | 3 | 4;

/**
 * 단계 경계(절댓값, 소수). [a, b, c]면
 * 0 < |v| < a → 1단계, a ≤ |v| < b → 2단계, b ≤ |v| < c → 3단계, c ≤ |v| → 4단계.
 */
export type LogThresholds = readonly [number, number, number];

export const LOG_THRESHOLDS: Record<LogMode, LogThresholds> = {
  daily: [0.005, 0.01, 0.02],
  cumulative: [0.05, 0.1, 0.2],
  costBasis: [0.05, 0.1, 0.2],
};

/** |v|가 이보다 작으면 0단계(formatPercent 소수 둘째 자리에서 0.00%로 보이는 값). */
export const LOG_ZERO_BAND = 0.00005;

export const LOG_MODE_LABELS: Record<LogMode, string> = {
  daily: "일별 손익",
  cumulative: "누적 수익률",
  costBasis: "매수 단가 대비",
};

export const LOG_MODES: readonly LogMode[] = ["daily", "cumulative", "costBasis"];

/** 값 → 단계(경계 직접 지정). |v|가 0이거나 zeroBand보다 작으면 0단계. null·NaN은 null(중립 칸). */
export function levelFor(value: number | null, [a, b, c]: LogThresholds, zeroBand = 0): LogLevel | null {
  if (value === null || !Number.isFinite(value)) return null;
  const abs = Math.abs(value);
  if (abs === 0 || abs < zeroBand) return 0;
  const step = abs < a ? 1 : abs < b ? 2 : abs < c ? 3 : 4;
  return (value > 0 ? step : -step) as LogLevel;
}

/** 값 → 단계. null·NaN은 null(중립 칸). */
export function logLevel(value: number | null, mode: LogMode): LogLevel | null {
  return levelFor(value, LOG_THRESHOLDS[mode], LOG_ZERO_BAND);
}

/**
 * 분포 기준 경계: 0이 아닌 값의 |v| 사분위수(25·50·75%, 선형 보간).
 * 단계마다 칸 수가 비슷해져서 계좌 크기와 상관없이 진하기가 고르게 퍼진다.
 * round로 경계를 보기 좋은 값(예: 만 원 단위)으로 맞출 수 있다. 값이 없으면 [0, 0, 0].
 */
export function quartileThresholds(values: readonly number[], round: (v: number) => number = (v) => v): LogThresholds {
  const abs = values.filter((v) => Number.isFinite(v) && v !== 0).map(Math.abs).sort((x, y) => x - y);
  if (abs.length === 0) return [0, 0, 0];
  const q = (p: number) => {
    const pos = (abs.length - 1) * p;
    const lo = Math.floor(pos);
    const hi = Math.min(abs.length - 1, lo + 1);
    return abs[lo] + (abs[hi] - abs[lo]) * (pos - lo);
  };
  return [round(q(0.25)), round(q(0.5)), round(q(0.75))];
}

/** 그날 칸에 칠할 값. costBasis에서 매수하지 않은 날, 투입금 0인 날은 null. */
export function logValue(day: SimulationDay, mode: LogMode, lastClose: number): number | null {
  switch (mode) {
    case "daily":
      return day.dailyReturn;
    case "cumulative":
      return day.invested > 0 ? day.value / day.invested - 1 : null;
    case "costBasis":
      return day.purchased && day.close > 0 ? lastClose / day.close - 1 : null;
  }
}

/** 단계 → CSS 색. null(중립)은 --log-empty. */
export function logColor(level: LogLevel | null): string {
  if (level === null) return "var(--log-empty)";
  if (level === 0) return "var(--log-zero)";
  return level > 0 ? `var(--gain-${level})` : `var(--loss-${-level})`;
}

// ── 날짜 ────────────────────────────────────────────────────────────────

const DAY_MS = 86_400_000;
const WEEKDAY_KO = ["일", "월", "화", "수", "목", "금", "토"] as const;

/** "YYYY-MM-DD" → 1970-01-01부터의 일수(UTC). */
export function dayNumber(iso: string): number {
  return Math.round(Date.parse(`${iso}T00:00:00Z`) / DAY_MS);
}

export function isoOfDayNumber(n: number): string {
  return new Date(n * DAY_MS).toISOString().slice(0, 10);
}

/** 0 = 월 … 6 = 일 */
function mondayIndex(n: number): number {
  // 1970-01-01은 목요일(월=0 기준 3).
  return (((n + 3) % 7) + 7) % 7;
}

/** "2026-10-16" → "2026.10.16 (금)" */
export function formatLogDate(iso: string): string {
  const dow = (mondayIndex(dayNumber(iso)) + 1) % 7;
  return `${formatDate(iso)} (${WEEKDAY_KO[dow]})`;
}

// ── 배치 ────────────────────────────────────────────────────────────────

export const LOG_ROW_LABELS = ["월", "화", "수", "목", "금"] as const;

/** 날짜만 아는 칸 자리. 어떤 로그(시뮬레이션·계좌)든 같은 배치를 쓴다. */
export type LogSlot =
  | { kind: "out" } // 기간 밖(첫 주 앞, 마지막 주 뒤) — 보이지 않는 자리
  | { kind: "gap"; date: string } // 기간 안 거래 없는 평일(휴장 등)
  | {
      kind: "day";
      date: string;
      /** 입력 배열에서의 위치 */
      index: number;
    };

/** 시뮬레이션 로그 칸: 자리 + 값·단계. */
export type LogCell =
  | Exclude<LogSlot, { kind: "day" }>
  | (Extract<LogSlot, { kind: "day" }> & { value: number | null; level: LogLevel | null; purchased: boolean });

export type LogMonthLabel = { column: number; text: string };

export type LogLayoutOf<C> = {
  /** rows[요일 0–4][주 열] */
  rows: C[][];
  columns: number;
  monthLabels: LogMonthLabel[];
  /** days[index] → [row, column] */
  positions: [number, number][];
  /** 날짜 → days 인덱스 */
  indexByDate: Map<string, number>;
  /** 0열 월요일의 dayNumber. 날짜 → 열 = floor((dayNumber − start) / 7) */
  start: number;
};

export type LogDateLayout = LogLayoutOf<LogSlot>;
export type LogLayout = LogLayoutOf<LogCell>;

const MIN_LABEL_GAP = 3; // 열

/** days(날짜 오름차순)를 GitHub식 주×요일 격자로 배치하고 칸마다 값·단계를 계산한다. */
export function buildLogLayout(
  days: readonly SimulationDay[],
  mode: LogMode,
  range?: { from: string; to: string },
): LogLayout {
  const base = layoutLogDates(days.map((d) => d.date), range);
  const lastClose = days.length ? days[days.length - 1].close : 0;
  const rows = base.rows.map((row) =>
    row.map((slot): LogCell => {
      if (slot.kind !== "day") return slot;
      const day = days[slot.index];
      const value = logValue(day, mode, lastClose);
      return { ...slot, value, level: logLevel(value, mode), purchased: day.purchased };
    }),
  );
  return { ...base, rows };
}

/** 날짜(오름차순)를 주×요일 격자 자리로 배치한다. 값·색은 부르는 쪽이 정한다. */
export function layoutLogDates(dates: readonly string[], range?: { from: string; to: string }): LogDateLayout {
  const indexByDate = new Map<string, number>();
  if (dates.length === 0) return { rows: LOG_ROW_LABELS.map(() => []), columns: 0, monthLabels: [], positions: [], indexByDate, start: 0 };

  const first = dayNumber(dates[0]);
  const last = dayNumber(dates[dates.length - 1]);
  const from = range ? Math.min(first, dayNumber(range.from)) : first;
  const to = range ? Math.max(last, dayNumber(range.to)) : last;
  const start = from - mondayIndex(from); // 첫 주 월요일
  const columns = Math.floor((to - start) / 7) + 1;

  const rows: LogSlot[][] = LOG_ROW_LABELS.map(() => Array.from({ length: columns }, (): LogSlot => ({ kind: "out" })));
  const positions: [number, number][] = new Array(dates.length);

  // 기간 안의 평일은 먼저 휴장(gap)으로 채운다.
  for (let n = first; n <= last; n++) {
    const r = mondayIndex(n);
    if (r > 4) continue;
    rows[r][Math.floor((n - start) / 7)] = { kind: "gap", date: isoOfDayNumber(n) };
  }

  dates.forEach((date, index) => {
    indexByDate.set(date, index);
    const n = dayNumber(date);
    const r = mondayIndex(n);
    const c = Math.floor((n - start) / 7);
    positions[index] = [Math.min(r, 4), c];
    if (r > 4) return; // 주말 데이터는 계약상 없지만, 있으면 칸 없이 넘긴다.
    rows[r][c] = { kind: "day", date, index };
  });

  // 월 라벨: 그 달의 첫 평일이 들어 있는 열.
  const monthLabels: LogMonthLabel[] = [];
  let prevMonth = "";
  for (let c = 0; c < columns; c++) {
    for (let r = 0; r < 5; r++) {
      const cell = rows[r][c];
      if (cell.kind === "out") continue;
      const ym = cell.date.slice(0, 7);
      if (ym !== prevMonth) {
        const [y, m] = ym.split("-");
        const month = Number(m);
        const prev = monthLabels[monthLabels.length - 1];
        const text = month === 1 || !prev ? `${y}.${month}월` : `${month}월`;
        if (prev && c - prev.column < MIN_LABEL_GAP) {
          // 첫 라벨이 다음 달과 붙으면 첫 라벨을 버린다(연도는 다음 라벨로 넘김).
          if (monthLabels.length === 1) monthLabels[0] = { column: c, text: `${y}.${month}월` };
        } else {
          monthLabels.push({ column: c, text });
        }
        prevMonth = ym;
      }
      break; // 열마다 가장 이른 칸만 본다.
    }
  }

  return { rows, columns, monthLabels, positions, indexByDate, start };
}

// ── 키보드 이동 ─────────────────────────────────────────────────────────

export type LogMove = "prevDay" | "nextDay" | "prevWeek" | "nextWeek" | "first" | "last";

/**
 * 키보드 이동 대상 인덱스. 하루 = 이전/다음 거래일, 한 주 = 7일 전/후 같은 요일
 * (그날이 휴장이면 같은 방향으로 가장 가까운 거래일).
 */
export function moveLogIndex(days: readonly { date: string }[], indexByDate: Map<string, number>, from: number, move: LogMove): number {
  const lastIndex = days.length - 1;
  switch (move) {
    case "prevDay":
      return Math.max(0, from - 1);
    case "nextDay":
      return Math.min(lastIndex, from + 1);
    case "first":
      return 0;
    case "last":
      return lastIndex;
    case "prevWeek":
    case "nextWeek": {
      const dir = move === "nextWeek" ? 1 : -1;
      const target = dayNumber(days[from].date) + 7 * dir;
      for (let k = 0; k < 7; k++) {
        const hit = indexByDate.get(isoOfDayNumber(target + k * dir));
        if (hit !== undefined) return hit;
      }
      // 마지막(첫) 주처럼 같은 요일 칸이 없으면 끝 칸으로.
      return dir > 0 ? lastIndex : 0;
    }
  }
}

// ── 문구 ────────────────────────────────────────────────────────────────

export function formatLogValue(value: number | null, mode: LogMode): string {
  if (value === null) return mode === "costBasis" ? "매수 안 함" : "—";
  return formatPercent(value);
}

/** 칸의 스크린리더 문구(툴팁과 같은 정보). */
export function logCellLabel(day: SimulationDay, value: number | null, mode: LogMode): string {
  const parts = [formatLogDate(day.date), `${LOG_MODE_LABELS[mode]} ${formatLogValue(value, mode)}`, `평가금액 ${formatWon(day.value)}`];
  if (day.purchased) parts.push("매수일");
  return parts.join(", ");
}
