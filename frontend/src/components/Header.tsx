"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "./auth/AuthProvider";
import { Logo } from "./Logo";

export function Header() {
  const { user, status, logout } = useAuth();
  const router = useRouter();
  const [pending, setPending] = useState(false);

  const onLogout = async () => {
    setPending(true);
    try {
      await logout();
      router.push("/");
      router.refresh();
    } finally {
      setPending(false);
    }
  };

  return (
    <header className="sticky top-0 z-20 border-b border-line bg-bg/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Logo />
          <nav className="hidden items-center gap-4 text-sm text-muted sm:flex">
            <Link href="/" className="hover:text-fg">
              방송 목록
            </Link>
            {user && (
              <Link href="/my" className="hover:text-fg">
                내 방송
              </Link>
            )}
          </nav>
        </div>

        <div className="flex items-center gap-2 text-sm" aria-live="polite">
          {status === "loading" && <span className="h-8 w-24 animate-pulse rounded-md bg-surface-2" />}
          {status === "anonymous" && (
            <>
              <Link href="/login" className="rounded-md px-3 py-1.5 text-muted hover:text-fg">
                로그인
              </Link>
              <Link href="/signup" className="rounded-md bg-surface-2 px-3 py-1.5 font-medium hover:bg-line">
                회원가입
              </Link>
            </>
          )}
          {user && (
            <>
              <Link href="/my" className="max-w-32 truncate rounded-md px-2 py-1.5 font-medium hover:bg-surface-2">
                {user.nickname}
              </Link>
              <button
                type="button"
                onClick={onLogout}
                disabled={pending}
                className="rounded-md px-3 py-1.5 text-muted hover:text-fg disabled:opacity-50"
              >
                로그아웃
              </button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
