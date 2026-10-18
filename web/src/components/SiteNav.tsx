"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "로그", match: (p: string) => p === "/" || p.startsWith("/days") || p.startsWith("/holdings") },
  { href: "/insights", label: "분석", match: (p: string) => p.startsWith("/insights") },
  { href: "/simulation", label: "시뮬레이션", match: (p: string) => p.startsWith("/simulation") },
];

export function SiteNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="주요 메뉴">
      <ul className="flex gap-1 text-sm">
        {LINKS.map((l) => {
          const active = l.match(pathname);
          return (
            <li key={l.href}>
              <Link
                href={l.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-lg px-3 py-2 ${active ? "font-semibold text-foreground" : "text-muted hover:text-foreground"}`}
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
