"use client";

import { useAuth } from "@/components/auth/AuthProvider";
import { MyBroadcasts } from "@/components/broadcast/MyBroadcasts";

export function MyAccount() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">{user.nickname}</h1>
        <p className="break-all text-sm text-muted">{user.email}</p>
      </div>
      {/* 계정이 바뀌면 이전 사용자의 목록 상태를 버린다 */}
      <MyBroadcasts key={user.id} ownerId={user.id} />
    </div>
  );
}
