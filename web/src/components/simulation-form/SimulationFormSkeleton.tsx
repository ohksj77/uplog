// URL을 읽기 전(프리렌더) 자리표시자. 폼과 높이를 비슷하게 맞춘다.
export function SimulationFormSkeleton() {
  const block = "animate-pulse rounded-xl bg-surface";
  return (
    <div aria-busy="true" aria-label="입력 폼을 불러오는 중" className="space-y-7">
      {[12, 12, 12, 24].map((h, i) => (
        <div key={i} className="space-y-2">
          <div className={`${block} h-4 w-20`} />
          <div className={block} style={{ height: `${h * 4}px` }} />
        </div>
      ))}
      <div className={`${block} h-13`} />
    </div>
  );
}
