import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Link from "next/link";
import { ConnectionLink } from "@/components/ConnectionLink";
import { DesktopLockScreen } from "@/components/DesktopLockScreen";
import { SiteNav } from "@/components/SiteNav";
import { ThemeToggle } from "@/components/ThemeToggle";
import { THEME_SCRIPT } from "@/lib/theme";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "업로그",
  description: "내 계좌의 하루하루를 로그로 쌓아 보여드려요.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ko"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      // data-theme은 첫 페인트 전 스크립트가 붙이므로 서버 렌더와 다를 수 있다.
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body className="flex min-h-full flex-col">
        <header className="border-b border-border">
          {/* 좁은 창에서도 한 줄에 들어가도록: 메뉴 간격을 줄이고, 테마·연결 설정은 아이콘 크기로 둔다. */}
          <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between gap-2 px-4 sm:gap-4">
            <Link href="/" className="shrink-0 font-semibold tracking-tight">
              업로그
            </Link>
            <div className="flex min-w-0 items-center gap-0.5 sm:gap-1">
              <SiteNav />
              <ThemeToggle />
              <ConnectionLink />
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">{children}</main>
        <DesktopLockScreen />
      </body>
    </html>
  );
}
