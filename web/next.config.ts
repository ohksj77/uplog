import type { NextConfig } from "next";
import { PHASE_DEVELOPMENT_SERVER } from "next/constants";

// 맥 앱(docs/design/desktop-app.md): `next build`는 out/에 정적 export를 만들고, 로컬 엔진이 그 파일을 같은 출처로 서빙한다.
// export에는 rewrites·headers·redirects가 없으므로, /api를 엔진으로 넘기는 rewrite는 `next dev`에서만 켠다
// (개발용 엔진은 --uplog.desktop.dev-open=true로 :8080에 띄운다).
const API_ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";

// cacheComponents(PPR)·partialPrefetching은 export와 함께 쓸 수 없어서("PPR cannot be enabled in export mode") 끈다.
// 개발과 빌드가 같게 동작하도록 dev에서도 끈다. 모든 화면은 정적 셸 + 클라이언트에서 /api를 부르는 구조라 잃는 것이 없다.
const base: NextConfig = {
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default function config(phase: string): NextConfig {
  if (phase === PHASE_DEVELOPMENT_SERVER) {
    return {
      ...base,
      async rewrites() {
        return [{ source: "/api/:path*", destination: `${API_ORIGIN}/api/:path*` }];
      },
    };
  }
  return {
    ...base,
    output: "export",
    // 엔진은 /x → x.html 순으로 찾는다. 기본(trailingSlash: false)이 x.html을 만든다.
    images: { unoptimized: true },
  };
}
