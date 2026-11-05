"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api, ApiError, json } from "@/lib/api";
import type { AuthStatus, Me, TestAccount } from "./types";

type AuthContextValue = {
  user: Me | null;
  status: AuthStatus;
  login: (email: string, password: string) => Promise<Me>;
  signup: (email: string, password: string, nickname: string) => Promise<Me>;
  testLogin: (account: TestAccount) => Promise<Me>;
  logout: () => Promise<void>;
  /** 토큰 만료 등으로 다시 확인할 때 */
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 로그인 상태의 단일 출처. 토큰은 HttpOnly 쿠키라 스크립트가 읽을 수 없으므로
 * 서버의 /api/auth/me 응답으로 상태를 정한다.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  const applyUser = useCallback((me: Me | null) => {
    setUser(me);
    setStatus(me ? "authenticated" : "anonymous");
  }, []);

  const refresh = useCallback(async () => {
    try {
      applyUser(await api<Me>("/api/auth/me"));
    } catch (e) {
      // 401은 로그인 안 됨. 네트워크 오류도 일단 비로그인으로 보여 준다
      if (!(e instanceof ApiError)) throw e;
      applyUser(null);
    }
  }, [applyUser]);

  useEffect(() => {
    let cancelled = false;
    api<Me>("/api/auth/me")
      .then((me) => !cancelled && applyUser(me))
      .catch(() => !cancelled && applyUser(null));
    return () => {
      cancelled = true;
    };
  }, [applyUser]);

  const value = useMemo<AuthContextValue>(() => {
    const signIn = async (path: string, body: unknown) => {
      const me = await api<Me>(path, { method: "POST", body: json(body) });
      applyUser(me);
      return me;
    };
    return {
      user,
      status,
      refresh,
      login: (email, password) => signIn("/api/auth/login", { email, password }),
      signup: (email, password, nickname) => signIn("/api/auth/signup", { email, password, nickname }),
      testLogin: (account) => signIn("/api/auth/test-login", { account }),
      logout: async () => {
        try {
          await api<void>("/api/auth/logout", { method: "POST" });
        } finally {
          applyUser(null);
        }
      },
    };
  }, [user, status, refresh, applyUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있습니다.");
  }
  return context;
}
