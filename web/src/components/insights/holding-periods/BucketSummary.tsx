import type { ReactNode } from "react";
import { Signed } from "@/components/result/Signed";
import type { HoldingBucketKey, HoldingPeriodsInsight } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon } from "@/lib/format";

// 보유 기간 구간별 요약. 판단 문장은 사실만 말하고 권하지 않는다.

const SHORT_NAME: Record<HoldingBucketKey, string> = {
  SHORT: "단타(7일 이하)",
  SWING: "스윙(8–90일)",
  MID: "중기(91–365일)",
  LONG: "장투(1년 초과)",
};

type Bucket = HoldingPeriodsInsight["buckets"][number];

export function bucketVerdict(buckets: Bucket[]): string {
  const filled = buckets.filter((b) => b.count > 0);
  if (filled.length === 0) return "아직 보유 기록이 없어요.";
  const best = Math.max(...filled.map((b) => b.winRate));
  const top = filled.filter((b) => b.winRate === best);
  const names = top.map((b) => SHORT_NAME[b.key]).join("·");
  if (filled.length === 1) return `모든 포지션이 ${names}이고 승률은 ${Math.round(best * 100)}%예요.`;
  return `${names}의 승률이 ${Math.round(best * 100)}%로 가장 높아요.`;
}

export function BucketSummary({ buckets }: { buckets: Bucket[] }) {
  return (
    <section aria-labelledby="bucket-title" className="space-y-3">
      <h2 id="bucket-title" className="text-lg font-semibold">
        보유 기간별 성적
      </h2>
      <p className="font-medium">{bucketVerdict(buckets)}</p>
      <ul className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {buckets.map((b) => (
          <li key={b.key} className="rounded-2xl bg-surface p-4">
            <p className="text-sm font-semibold break-keep">{b.label}</p>
            {b.count === 0 ? (
              <p className="mt-3 text-sm text-muted">포지션 없음</p>
            ) : (
              <dl className="mt-2 space-y-1 text-sm">
                <Row label="포지션">{b.count}개</Row>
                <Row label="승률">{Math.round(b.winRate * 100)}%</Row>
                <Row label="평균 수익률">
                  <Signed value={b.avgReturn}>{formatPercent(b.avgReturn, 1)}</Signed>
                </Row>
                <Row label="손익">
                  <Signed value={b.profit}>{formatSignedWon(b.profit)}</Signed>
                </Row>
              </dl>
            )}
          </li>
        ))}
      </ul>
      <p className="text-xs text-muted">승률은 손익이 0보다 큰 포지션의 비율이에요. 보유 중인 포지션은 오늘 평가 기준으로 넣었어요.</p>
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-x-2">
      <dt className="text-muted">{label}</dt>
      <dd className="font-medium tabular-nums">{children}</dd>
    </div>
  );
}
