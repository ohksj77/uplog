import { SampleAccountBadge } from "@/components/ui/AsyncState";
import { USE_MOCK } from "@/lib/api/client";

/** mock 데이터로 그릴 때만 샘플 계좌 배지를 단다. */
export function MockBadge() {
  return USE_MOCK ? <SampleAccountBadge /> : null;
}
