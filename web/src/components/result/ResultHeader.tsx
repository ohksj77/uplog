import Link from "next/link";
import { CopyLinkButton } from "@/components/result/CopyLinkButton";
import { CurrencyToggle } from "@/components/result/CurrencyToggle";
import { PERIOD_LABEL, describePlan } from "@/components/result/text";
import type { SimulationParams } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { toSearchParams } from "@/lib/simulation-params";

type ResultHeaderProps = {
  params: SimulationParams;
  from: string;
  to: string;
};

export function ResultHeader({ params, from, to }: ResultHeaderProps) {
  return (
    <header className="space-y-3">
      <p className="text-sm text-muted">
        <span className="font-medium text-foreground">{PERIOD_LABEL[params.period]}</span>{" "}
        <time dateTime={from}>{formatDate(from)}</time> – <time dateTime={to}>{formatDate(to)}</time>
      </p>
      <h1 className="text-2xl font-bold tracking-tight break-keep">{describePlan(params)}했다면</h1>
      <div className="flex flex-wrap items-center gap-2">
        <Link
          href={`/simulation?${toSearchParams(params)}`}
          className="inline-flex h-9 items-center rounded-lg bg-surface px-3 text-sm font-medium"
        >
          조건 바꾸기
        </Link>
        <CopyLinkButton />
      </div>
      <CurrencyToggle value={params.currency ?? "KRW"} />
    </header>
  );
}
