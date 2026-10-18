import { CopyButton } from "@/components/connect/CopyButton";

/** WTS 허용 IP 관리에 등록할 우리 서버 IP. 하나씩 복사할 수 있다. */
export function EgressIpList({ ips }: { ips: string[] }) {
  if (ips.length === 0) {
    return <p className="text-sm text-muted">등록할 IP를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>;
  }
  return (
    <ul className="space-y-2" aria-label="등록할 IP">
      {ips.map((ip) => (
        <li key={ip} className="flex items-center justify-between gap-3 rounded-xl border border-border bg-background px-4 py-2">
          <code className="min-w-0 font-mono text-base break-all select-all">{ip}</code>
          <CopyButton value={ip} label={`IP ${ip}`} />
        </li>
      ))}
    </ul>
  );
}
