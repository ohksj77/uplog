import type { Metadata } from "next";
import { InsightsHub } from "@/components/insights/InsightsHub";

export const metadata: Metadata = { title: "분석 | UpLog" };

export default function InsightsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">분석</h1>
      <InsightsHub />
    </div>
  );
}
