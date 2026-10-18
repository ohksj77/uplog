import { Suspense } from "react";
import { SimulationFormFromUrl, SimulationFormSkeleton, SimulationGate } from "@/components/simulation-form";

export default function Home() {
  return (
    <div className="mx-auto w-full max-w-lg">
      <section className="mb-8">
        <h1 className="text-2xl leading-snug font-bold tracking-tight sm:text-3xl">
          그때 샀다면,
          <br />
          지금 얼마일까?
        </h1>
        <p className="mt-3 text-[15px] leading-relaxed text-muted">
          종목과 기간, 금액을 정하면 꾸준히 나눠 샀을 때와 처음에 한 번에 샀을 때의 결과를 하루 단위로 보여드려요.
        </p>
      </section>

      <SimulationGate fallback={<SimulationFormSkeleton />}>
        <Suspense fallback={<SimulationFormSkeleton />}>
          <SimulationFormFromUrl />
        </Suspense>
      </SimulationGate>

      <p className="mt-8 text-center text-xs text-muted">과거 수익률은 미래 수익을 보장하지 않습니다.</p>
    </div>
  );
}
