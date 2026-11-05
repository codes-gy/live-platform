import type { NextConfig } from "next";

/**
 * 브라우저는 항상 같은 출처의 /api 로 요청하고, Next.js가 백엔드(Spring)로 넘긴다.
 * 그래서 로그인 쿠키(HttpOnly · SameSite=Lax)가 프론트와 같은 출처로 붙고 CORS가 필요 없다.
 * BACKEND_URL은 빌드 시점에 읽힌다(배포 환경 변수에 넣는다).
 */
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

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
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
