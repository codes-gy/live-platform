"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { Card, ErrorMessage, Field, PrimaryButton } from "../ui";
import { useAuth } from "./AuthProvider";
import type { TestAccount } from "./types";

const TEST_ACCOUNTS: { account: TestAccount; label: string; description: string }[] = [
  { account: "STREAMER", label: "방송자로 시작", description: "streamer@test.com · 방송을 만들고 송출" },
  { account: "VIEWER", label: "시청자로 시작", description: "viewer@test.com · 다른 사용자 권한 확인" },
];

export function LoginForm({ next }: { next: string }) {
  const { login, testLogin } = useAuth();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const run = async (key: string, action: () => Promise<unknown>) => {
    setError(null);
    setPending(key);
    try {
      await action();
      router.replace(next);
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "로그인하지 못했습니다.");
      setPending(null);
    }
  };

  const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    run("form", () => login(String(form.get("email")), String(form.get("password"))));
  };

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-4 py-6">
      <Card>
        <h1 className="text-2xl font-bold">로그인</h1>
        <p className="mt-1 text-sm text-muted">방송을 만들고 송출하려면 로그인하세요. 시청은 로그인 없이도 됩니다.</p>

        <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
          <Field label="이메일" id="email" type="email" autoComplete="email" required placeholder="you@example.com" />
          <Field label="비밀번호" id="password" type="password" autoComplete="current-password" required />
          <ErrorMessage message={error} />
          <PrimaryButton type="submit" disabled={pending !== null}>
            {pending === "form" ? "로그인하는 중…" : "로그인"}
          </PrimaryButton>
        </form>

        <p className="mt-4 text-center text-sm text-muted">
          처음이신가요?{" "}
          <Link href={`/signup?next=${encodeURIComponent(next)}`} className="font-medium text-fg underline-offset-4 hover:underline">
            회원가입
          </Link>
        </p>
      </Card>

      <Card className="!p-5">
        <h2 className="text-sm font-semibold">테스트 계정으로 바로 시작</h2>
        <p className="mt-1 text-xs text-muted">비밀번호 입력 없이 로그인합니다. 두 계정으로 소유자와 다른 사용자의 권한 차이를 확인할 수 있습니다.</p>
        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          {TEST_ACCOUNTS.map(({ account, label, description }) => (
            <button
              key={account}
              type="button"
              disabled={pending !== null}
              onClick={() => run(account, () => testLogin(account))}
              className="flex flex-col items-start gap-0.5 rounded-lg border border-line bg-surface-2 px-3 py-2.5 text-left hover:border-live disabled:opacity-60"
            >
              <span className="text-sm font-semibold">{pending === account ? "로그인하는 중…" : label}</span>
              <span className="text-xs text-muted">{description}</span>
            </button>
          ))}
        </div>
      </Card>
    </div>
  );
}
