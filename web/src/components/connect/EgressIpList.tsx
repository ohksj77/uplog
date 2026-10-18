import { CopyButton } from "@/components/connect/CopyButton";

/** WTS 허용 IP 관리에 등록할 우리 서버 IP. 하나씩 복사할 수 있다. */
export function EgressIpList({ ips }: { ips: string[] }) {
  if (ips.length === 0) {
    return (
      <p className="text-sm text-muted break-keep">
        서버의 공인 IP가 아직 설정되지 않았어요. 서버를 <code className="font-mono">UPLOG_EGRESS_IPS</code> 환경 변수와 함께 실행해야 토스증권에
        등록할 IP를 보여 줄 수 있어요.
      </p>
    );
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
