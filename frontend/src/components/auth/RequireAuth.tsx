"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { loginPath } from "@/lib/navigation";
import { useAuth } from "./AuthProvider";

/**
 * 로그인해야 볼 수 있는 화면. proxy.ts가 쿠키가 없는 요청을 먼저 돌려보내고,
 * 쿠키는 있지만 만료 · 위조된 경우는 여기서 /api/auth/me 결과로 다시 걸러 로그인으로 보낸다.
 */
export function RequireAuth({ children, fallback }: { children: React.ReactNode; fallback?: React.ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(loginPath(pathname));
    }
  }, [status, router, pathname]);

  if (status !== "authenticated") {
    if (fallback) return fallback;
    return (
      <div className="flex flex-1 items-center justify-center py-24 text-sm text-muted" role="status">
        로그인 상태를 확인하고 있습니다…
      </div>
    );
  }
  return <>{children}</>;
}
