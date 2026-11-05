"use client";

import { useAuth } from "@/components/auth/AuthProvider";
import { Card } from "@/components/ui";

/** 내 방송 목록 · 방송 만들기는 #6에서 이 화면에 붙는다. */
export function MyAccount() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">내 방송</h1>
      <Card className="!p-5">
        <dl className="grid gap-3 text-sm sm:grid-cols-[6rem_1fr]">
          <dt className="text-muted">닉네임</dt>
          <dd className="font-medium">{user.nickname}</dd>
          <dt className="text-muted">이메일</dt>
          <dd className="break-all">{user.email}</dd>
        </dl>
      </Card>
      <p className="text-sm text-muted">방송 만들기와 내 방송 목록이 이곳에 표시됩니다.</p>
    </div>
  );
}
