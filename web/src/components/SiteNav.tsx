"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense } from "react";

const LINKS = [
  { href: "/", label: "로그", match: (p: string) => p === "/" || p.startsWith("/days") || p.startsWith("/holdings") },
  { href: "/insights", label: "분석", match: (p: string) => p.startsWith("/insights") },
  { href: "/simulation", label: "시뮬레이션", match: (p: string) => p.startsWith("/simulation") },
];

/** 현재 경로에 맞춰 메뉴를 강조한다. usePathname은 동적 경로에서 Suspense 안에 있어야 해서 SiteNav가 감싼다. */
function ActiveNav() {
  return <NavList pathname={usePathname()} />;
}

export function SiteNav() {
  return (
    <Suspense fallback={<NavList pathname={null} />}>
      <ActiveNav />
    </Suspense>
  );
}

function NavList({ pathname }: { pathname: string | null }) {
  return (
    <nav aria-label="주요 메뉴">
      <ul className="flex text-sm sm:gap-1">
        {LINKS.map((l) => {
          const active = pathname != null && l.match(pathname);
          return (
            <li key={l.href}>
              <Link
                href={l.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-lg px-2 py-2 whitespace-nowrap sm:px-3 ${active ? "font-semibold text-foreground" : "text-muted hover:text-foreground"}`}
              >
                {l.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
