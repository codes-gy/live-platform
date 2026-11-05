import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * 로그인이 필요한 화면에 쿠키 없이 들어오면 화면을 그리기 전에 로그인으로 보낸다.
 * 토큰이 유효한지는 백엔드가 판단한다(만료 · 위조는 RequireAuth가 다시 거른다).
 */
export function proxy(request: NextRequest) {
  if (request.cookies.has("access_token")) {
    return NextResponse.next();
  }
  const login = new URL("/login", request.url);
  login.searchParams.set("next", request.nextUrl.pathname + request.nextUrl.search);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/my/:path*", "/studio/:path*", "/broadcasts/new", "/broadcasts/:id/edit"],
};
