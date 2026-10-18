// TrailChart 범례. 화면마다 보이는 항목만 고른다.

type Item = "solid" | "dashed" | "ref" | "guide" | "buy" | "sell";

const LABEL: Record<Item, string> = {
  solid: "보유 중",
  dashed: "매도 뒤",
  ref: "매도가",
  guide: "판정 시점",
  buy: "매수",
  sell: "매도",
};

export function TrailLegend({ items, labels = {} }: { items: Item[]; labels?: Partial<Record<Item, string>> }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted" aria-hidden>
      {items.map((it) => (
        <li key={it} className="flex items-center gap-1.5">
          <Swatch item={it} />
          {labels[it] ?? LABEL[it]}
        </li>
      ))}
    </ul>
  );
}

function Swatch({ item }: { item: Item }) {
  switch (item) {
    case "solid":
      return <span className="inline-block w-4 border-t-2 border-foreground" />;
    case "dashed":
      return <span className="inline-block w-4 border-t-2 border-dashed border-muted" />;
    case "ref":
      return <span className="inline-block w-4 border-t border-dotted border-trade-sell" />;
    case "guide":
      return <span className="inline-block h-3 border-l-2 border-border" />;
    case "buy":
      return <span className="text-trade-buy">▲</span>;
    case "sell":
      return <span className="text-trade-sell">▼</span>;
  }
}
