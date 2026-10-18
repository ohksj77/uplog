import type { Currency } from "@/lib/api/portfolio-types";
import { formatWon } from "@/lib/format";

const usd = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 현지 통화 가격: 원화 "72,000원", 달러 "$201.30". */
export function formatLocal(price: number, currency: Currency): string {
  return currency === "USD" ? `$${usd.format(price)}` : formatWon(price);
}
