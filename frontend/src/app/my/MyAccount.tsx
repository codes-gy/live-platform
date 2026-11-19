"use client";

import { useAuth } from "@/components/auth/AuthProvider";
import { ProfileImageEditor } from "@/components/auth/ProfileImageEditor";
import { MyBroadcasts } from "@/components/broadcast/MyBroadcasts";

export function MyAccount() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="flex flex-col gap-6">
      <ProfileImageEditor user={user} />
      {/* 계정이 바뀌면 이전 사용자의 목록 상태를 버린다 */}
      <MyBroadcasts key={user.id} ownerId={user.id} />
    </div>
  );
}
