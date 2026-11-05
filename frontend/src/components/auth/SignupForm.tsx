"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { Card, ErrorMessage, Field, PrimaryButton } from "../ui";
import { useAuth } from "./AuthProvider";

export function SignupForm({ next }: { next: string }) {
  const { signup } = useAuth();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password"));
    if (password !== String(form.get("passwordConfirm"))) {
      setError("비밀번호가 서로 다릅니다.");
      return;
    }
    setError(null);
    setPending(true);
    try {
      await signup(String(form.get("email")), password, String(form.get("nickname")));
      router.replace(next);
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "가입하지 못했습니다.");
      setPending(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-md py-6">
      <Card>
        <h1 className="text-2xl font-bold">회원가입</h1>
        <p className="mt-1 text-sm text-muted">가입하면 바로 로그인됩니다.</p>

        <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
          <Field label="이메일" id="email" type="email" autoComplete="email" required maxLength={255} />
          <Field label="닉네임" id="nickname" autoComplete="nickname" required maxLength={30} hint="방송과 채팅에 보이는 이름입니다. 30자까지." />
          <Field label="비밀번호" id="password" type="password" autoComplete="new-password" required minLength={8} maxLength={64} hint="8자 이상" />
          <Field label="비밀번호 확인" id="passwordConfirm" type="password" autoComplete="new-password" required />
          <ErrorMessage message={error} />
          <PrimaryButton type="submit" disabled={pending}>
            {pending ? "가입하는 중…" : "가입하고 시작하기"}
          </PrimaryButton>
        </form>

        <p className="mt-4 text-center text-sm text-muted">
          이미 계정이 있나요?{" "}
          <Link href={`/login?next=${encodeURIComponent(next)}`} className="font-medium text-fg underline-offset-4 hover:underline">
            로그인
          </Link>
        </p>
      </Card>
    </div>
  );
}
