import type { ReactNode } from "react";
import { Signed } from "@/components/result/Signed";
import { STRATEGIES, STRATEGY_LABEL, type Strategy } from "@/components/result/text";
import type { SimulationDay } from "@/lib/api/types";
import { formatDate, formatPercent, formatWon } from "@/lib/format";

type DayDetailProps = {
  date: string;
  days: Record<Strategy, SimulationDay | undefined>;
  onClose: () => void;
};

/** 로그·차트에서 고른 하루의 상세. 두 방식을 나란히 보여준다. */
export function DayDetail({ date, days, onClose }: DayDetailProps) {
  const close = (days.lumpSum ?? days.recurring)?.close;
  return (
    <section aria-label={`${formatDate(date)} 상세`} aria-live="polite" className="rounded-2xl border border-border p-4">
      <div className="mb-3 flex items-start justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold">
            <time dateTime={date}>{formatDate(date)}</time>
          </h3>
          {close != null && <p className="text-xs text-muted">종가 {formatWon(close)}</p>}
        </div>
        <button type="button" onClick={onClose} className="rounded-md px-2 py-1 text-xs text-muted hover:bg-surface">
          닫기
        </button>
      </div>
      <div className="grid grid-cols-2 gap-3">
        {STRATEGIES.map((s) => (
          <DayColumn key={s} title={STRATEGY_LABEL[s]} day={days[s]} />
        ))}
      </div>
    </section>
  );
}

function DayColumn({ title, day }: { title: string; day: SimulationDay | undefined }) {
  return (
    <div className="min-w-0">
      <p className="mb-1 text-xs font-medium text-muted">{title}</p>
      {!day ? (
        <p className="text-sm text-muted">아직 투자 전이에요.</p>
      ) : (
        <dl className="space-y-1 text-sm">
          <Row label="평가금액">{formatWon(day.value)}</Row>
          <Row label="투입금">{formatWon(day.invested)}</Row>
          <Row label="그날 수익률">
            <Signed value={day.dailyReturn}>{formatPercent(day.dailyReturn)}</Signed>
          </Row>
          <Row label="매수">{day.purchased ? "샀어요" : "안 샀어요"}</Row>
        </dl>
      )}
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="font-medium tabular-nums break-all">{children}</dd>
    </div>
  );
}
