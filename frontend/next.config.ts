import type { NextConfig } from "next";

/**
 * 브라우저는 항상 같은 출처의 /api 로 요청하고, Next.js가 백엔드(Spring)로 넘긴다.
 * 그래서 로그인 쿠키(HttpOnly · SameSite=Lax)가 프론트와 같은 출처로 붙고 CORS가 필요 없다.
 * BACKEND_URL은 빌드 시점에 읽힌다(배포 환경 변수에 넣는다).
 */
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  // 기본 gzip 압축이 SSE(댓글 스트림)를 모아 두었다가 보내서 실시간 수신이 멈춘다. 압축은 앞단(프록시)이 맡는다.
  compress: false,
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
