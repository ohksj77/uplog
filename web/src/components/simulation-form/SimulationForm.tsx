"use client";

import { useId, useState, useTransition, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { AmountType, Frequency, Period, SimulationParams, Weekday } from "@/lib/api/types";
import { DEFAULT_PARAMS, MAX_AMOUNT, MAX_ETFS, MIN_AMOUNT, toSearchParams } from "@/lib/simulation-params";
import { formatWon } from "@/lib/format";
import { EtfPicker } from "@/components/etf-picker";
import { AmountInput } from "@/components/simulation-form/AmountInput";
import { SegmentedControl, type SegmentOption } from "@/components/ui/SegmentedControl";
import { useResolvedEtfs } from "@/components/simulation-form/useResolvedEtfs";

const PERIOD_OPTIONS: SegmentOption<Period>[] = [
  { value: "6M", label: "6개월" },
  { value: "1Y", label: "1년" },
  { value: "3Y", label: "3년" },
];

const FREQUENCY_OPTIONS: SegmentOption<Frequency>[] = [
  { value: "DAILY", label: "매일" },
  { value: "WEEKLY", label: "매주" },
  { value: "MONTHLY", label: "매월" },
];

const WEEKDAY_OPTIONS: SegmentOption<Weekday>[] = [
  { value: "MONDAY", label: "월" },
  { value: "TUESDAY", label: "화" },
  { value: "WEDNESDAY", label: "수" },
  { value: "THURSDAY", label: "목" },
  { value: "FRIDAY", label: "금" },
];

const AMOUNT_TYPE_OPTIONS: SegmentOption<AmountType>[] = [
  { value: "PER_PURCHASE", label: "회차당 금액" },
  { value: "TOTAL", label: "총 금액" },
];

const DAYS_OF_MONTH = Array.from({ length: 31 }, (_, i) => i + 1);

function amountError(amount: number | null): string | null {
  if (amount === null) return "금액을 입력해 주세요.";
  if (amount < MIN_AMOUNT) return `${formatWon(MIN_AMOUNT)} 이상 입력해 주세요.`;
  if (amount > MAX_AMOUNT) return "10억 원 이하로 입력해 주세요.";
  return null;
}

export type SimulationFormProps = {
  /** URL에서 읽은 값. 없으면 DEFAULT_PARAMS */
  initial?: SimulationParams;
};

export function SimulationForm({ initial = DEFAULT_PARAMS }: SimulationFormProps) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const ids = useId();

  const [etfs, setEtfs] = useResolvedEtfs(initial.etfs);
  const [period, setPeriod] = useState<Period>(initial.period);
  const [frequency, setFrequency] = useState<Frequency>(initial.frequency);
  const [dayOfWeek, setDayOfWeek] = useState<Weekday>(initial.dayOfWeek ?? "MONDAY");
  const [dayOfMonth, setDayOfMonth] = useState<number>(initial.dayOfMonth ?? 1);
  const [amountType, setAmountType] = useState<AmountType>(initial.amountType);
  const [amount, setAmount] = useState<number | null>(initial.amount);
  const [amountTouched, setAmountTouched] = useState(false);

  const amountMsg = amountError(amount);
  const etfMissing = etfs.length === 0;
  const valid = !etfMissing && amountMsg === null;

  function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!valid || amount === null) return;
    const params: SimulationParams = {
      etfs: etfs.map((x) => x.code),
      period,
      frequency,
      amountType,
      amount,
      ...(frequency === "WEEKLY" && { dayOfWeek }),
      ...(frequency === "MONTHLY" && { dayOfMonth }),
    };
    startTransition(() => router.push(`/simulation/result?${toSearchParams(params)}`));
  }

  const etfInputId = `${ids}-etf`;
  const etfHintId = `${ids}-etf-hint`;
  const amountId = `${ids}-amount`;
  const amountHintId = `${ids}-amount-hint`;
  const domId = `${ids}-dom`;
  const showAmountError = amountMsg !== null && (amountTouched || amount !== null);

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-7">
      <div>
        <label htmlFor={etfInputId} className="mb-2 block text-sm font-medium">
          종목 <span className="font-normal text-muted">(최대 {MAX_ETFS}개)</span>
        </label>
        <EtfPicker value={etfs} onChange={setEtfs} inputId={etfInputId} describedBy={etfHintId} />
        <p id={etfHintId} className="mt-2 text-xs text-muted">
          국내·미국 ETF와 주식을 고를 수 있어요. 여러 개를 고르면 같은 조건으로 나란히 비교해요. 미국 종목은 원화로
          환산해 보여드려요.
        </p>
      </div>

      <SegmentedControl legend="기간" options={PERIOD_OPTIONS} value={period} onChange={setPeriod} />

      <div className="space-y-3">
        <SegmentedControl legend="적립 주기" options={FREQUENCY_OPTIONS} value={frequency} onChange={setFrequency} />
        {frequency === "WEEKLY" && (
          <SegmentedControl legend="매수 요일" options={WEEKDAY_OPTIONS} value={dayOfWeek} onChange={setDayOfWeek} />
        )}
        {frequency === "MONTHLY" && (
          <div>
            <label htmlFor={domId} className="mb-2 block text-sm font-medium">
              매수 날짜
            </label>
            <div className="flex items-center gap-2">
              <select
                id={domId}
                value={dayOfMonth}
                onChange={(e) => setDayOfMonth(Number(e.target.value))}
                aria-describedby={`${domId}-hint`}
                className="h-11 rounded-xl border border-border bg-background px-3 text-base focus-visible:border-accent focus-visible:outline-2 focus-visible:outline-accent/30"
              >
                {DAYS_OF_MONTH.map((d) => (
                  <option key={d} value={d}>
                    매월 {d}일
                  </option>
                ))}
              </select>
            </div>
            <p id={`${domId}-hint`} className="mt-2 text-xs text-muted">
              그 달에 없는 날짜(예: 31일)면 말일에 사요.
            </p>
          </div>
        )}
        {frequency === "DAILY" && <p className="text-xs text-muted">거래일마다 한 번씩 사요.</p>}
      </div>

      <div className="space-y-3">
        <SegmentedControl
          legend="금액 기준"
          options={AMOUNT_TYPE_OPTIONS}
          value={amountType}
          onChange={setAmountType}
        />
        <div>
          <label htmlFor={amountId} className="mb-2 block text-sm font-medium">
            {amountType === "PER_PURCHASE" ? "한 번에 살 금액" : "총 투자 금액"}
          </label>
          <div onBlur={() => setAmountTouched(true)}>
            <AmountInput
              id={amountId}
              value={amount}
              onChange={setAmount}
              describedBy={amountHintId}
              invalid={showAmountError}
            />
          </div>
          <p id={amountHintId} className={`mt-2 text-xs ${showAmountError ? "text-gain" : "text-muted"}`} aria-live="polite">
            {showAmountError
              ? amountMsg
              : amountType === "PER_PURCHASE"
                ? "살 때마다 이 금액만큼 사요."
                : "이 금액을 매수 횟수로 나눠서 사요."}
          </p>
        </div>
      </div>

      <div className="space-y-3 border-t border-border pt-6">
        <p className="text-sm text-muted">
          결과에서는 같은 총 금액을 첫날 한 번에 넣은 경우(거치식)도 함께 보여드려요.
        </p>
        <button
          type="submit"
          disabled={!valid || pending}
          className="h-13 w-full rounded-xl bg-accent text-base font-semibold text-white transition-opacity hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
        >
          {pending ? "계산하는 중…" : "결과 보기"}
        </button>
        {etfMissing && <p className="text-center text-xs text-muted">종목을 하나 이상 골라 주세요.</p>}
      </div>
    </form>
  );
}
