"use client";

import Image from "next/image";
import { useState } from "react";

export type AvatarUser = { id: number; nickname: string; avatarUpdatedAt: string | null };

const SIZES = { sm: "h-7 w-7 text-xs", md: "h-12 w-12 text-lg", lg: "h-20 w-20 text-3xl" } as const;

export function avatarSrc(user: AvatarUser): string | null {
  return user.avatarUpdatedAt && `/api/users/${user.id}/avatar?v=${encodeURIComponent(user.avatarUpdatedAt)}`;
}

/** 프로필 이미지. 없으면(또는 못 불러오면) 닉네임 첫 글자. */
export function Avatar({ user, size = "md", live = false }: { user: AvatarUser; size?: keyof typeof SIZES; live?: boolean }) {
  const src = avatarSrc(user);
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const shown = src && src !== failedSrc ? src : null;
  return (
    <span
      aria-hidden
      className={`relative flex shrink-0 items-center justify-center overflow-hidden rounded-full font-bold ${SIZES[size]} ${
        live ? "bg-live text-white ring-4 ring-live/20" : "bg-surface text-muted ring-1 ring-line"
      }`}
    >
      {shown ? (
        <Image src={shown} alt="" fill unoptimized className="object-cover" onError={() => setFailedSrc(shown)} />
      ) : (
        ([...user.nickname.trim()][0] ?? "?")
      )}
    </span>
  );
}
