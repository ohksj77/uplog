import type { ReactNode } from "react";

// 토스증권 PC 웹의 설정 > Open API 창을 간단히 그린 안내 그림.
// 실제 화면 캡처는 개인 정보(키·IP·발급일)가 들어 있어 쓰지 않고, 색 토큰만 쓴 도식으로 그린다.
//
// 나중에 개인 정보를 지운 캡처를 쓰고 싶으면 아래 경로를 채우면 된다(public/ 아래 경로, 예: "/guide/wts-key.png").
// 채운 단계는 도식 대신 그 이미지를 보여 준다. 비워 두면(null) 도식을 그린다.
export const WTS_SCREENSHOTS: Record<WtsGuideFocus, { src: string; width: number; height: number } | null> = {
  key: null, // TODO: 키 발급 단계 캡처(Open API Key 관리 영역)
  ip: null, // TODO: IP 등록 단계 캡처(허용 IP 관리 영역)
};

export type WtsGuideFocus = "key" | "ip";

const CAPTION: Record<WtsGuideFocus, string> = {
  key: "설정 창의 Open API 메뉴. 'Open API Key 관리'에서 Client Id를 복사하고, Client Secret은 발급할 때 바로 복사해요.",
  ip: "같은 화면 아래 '허용 IP 관리'에서 'IP 추가'를 눌러 이 맥의 공인 IP를 더해요.",
};

/** 단계별로 봐야 할 곳을 강조한 WTS 설정 창 그림. 그림은 장식이고, 같은 내용을 캡션 글로도 읽힌다. */
export function WtsGuideIllustration({ focus }: { focus: WtsGuideFocus }) {
  const shot = WTS_SCREENSHOTS[focus];
  return (
    <figure className="space-y-2">
      {shot ? (
        // 정적 캡처라 최적화가 필요 없다. 크기를 주어 레이아웃이 밀리지 않게 한다.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={shot.src} width={shot.width} height={shot.height} alt="" className="h-auto w-full rounded-xl border border-border" />
      ) : (
        <SettingsPopup focus={focus} />
      )}
      <figcaption className="text-xs text-muted break-keep">{CAPTION[focus]}</figcaption>
    </figure>
  );
}

function SettingsPopup({ focus }: { focus: WtsGuideFocus }) {
  return (
    <div aria-hidden className="overflow-hidden rounded-xl border border-border bg-background text-[11px] leading-tight select-none">
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <span className="font-semibold">설정</span>
        <span className="text-muted">✕</span>
      </div>
      <div className="flex min-h-0">
        {/* 왼쪽 메뉴 */}
        <ul className="w-[30%] max-w-28 shrink-0 space-y-1 border-r border-border p-2">
          <li className="h-2.5 w-4/5 rounded bg-border" />
          <li className="h-2.5 w-3/5 rounded bg-border" />
          <li className="rounded-md bg-surface px-1.5 py-1 font-semibold text-accent outline-2 outline-accent">Open API</li>
          <li className="h-2.5 w-2/3 rounded bg-border" />
        </ul>
        {/* 오른쪽 내용 */}
        <div className="min-w-0 flex-1 space-y-2 p-2">
          <Section title="Open API Key 관리" active={focus === "key"}>
            <Row label="Client Id">
              <span className="min-w-0 truncate font-mono">tsck_live_••••</span>
              <Chip highlight={focus === "key"}>복사</Chip>
            </Row>
            <Row label="Client Secret">
              <span className="min-w-0 truncate font-mono">tssk_live_****</span>
            </Row>
            <Row label="발급일 · 만료일">
              <span className="text-muted">YYYY.MM.DD</span>
            </Row>
            <Row label="상태">
              <span>활성</span>
              <Chip>재발급</Chip>
            </Row>
          </Section>
          <Section title="허용 IP 관리" active={focus === "ip"} action={<Chip highlight={focus === "ip"}>IP 추가</Chip>}>
            <Row label="000.000.000.000">
              <Chip>삭제</Chip>
            </Row>
          </Section>
          <p className="text-muted underline underline-offset-2">Open API 가이드 문서</p>
        </div>
      </div>
    </div>
  );
}

function Section({ title, active, action, children }: { title: string; active: boolean; action?: ReactNode; children: ReactNode }) {
  return (
    <div className={`space-y-1 rounded-lg p-1.5 ${active ? "bg-surface outline-2 outline-accent" : "opacity-60"}`}>
      <div className="flex items-center justify-between gap-1">
        <span className="font-semibold">{title}</span>
        {action}
      </div>
      {children}
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-1">
      <span className="shrink-0 text-muted">{label}</span>
      <span className="flex min-w-0 items-center gap-1">{children}</span>
    </div>
  );
}

function Chip({ highlight = false, children }: { highlight?: boolean; children: ReactNode }) {
  return (
    <span className={`shrink-0 rounded px-1.5 py-0.5 ${highlight ? "bg-accent font-semibold text-white" : "border border-border"}`}>{children}</span>
  );
}
