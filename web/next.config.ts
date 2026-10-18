import type { NextConfig } from "next";

const API_ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    // 기본(afterFiles) rewrite라 /login 같은 앱 경로가 먼저 잡히고, 남은 OAuth2 경로만 백엔드로 간다.
    // 카카오 로그인 왕복(/oauth2/authorization/kakao → /login/oauth2/code/kakao)을 같은 출처에서 처리한다.
    return [
      { source: "/api/:path*", destination: `${API_ORIGIN}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${API_ORIGIN}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${API_ORIGIN}/login/oauth2/:path*` },
    ];
  },
};

export default nextConfig;
