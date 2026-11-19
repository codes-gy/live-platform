"use client";

import { useRef, useState } from "react";
import { api } from "@/lib/api";
import { Avatar } from "../Avatar";
import { ErrorMessage } from "../ui";
import { useAuth } from "./AuthProvider";
import type { Me } from "./types";

const SIZE = 256;
const button = "rounded-lg border border-line px-3 py-1.5 text-sm font-medium hover:bg-surface-2 disabled:opacity-50";

/** 고른 사진을 가운데 기준 정사각형 256px JPEG로 줄인다. 서버 한도(200KB) 안에 들어온다. */
async function toSquareJpeg(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const side = Math.min(bitmap.width, bitmap.height);
  const canvas = document.createElement("canvas");
  canvas.width = SIZE;
  canvas.height = SIZE;
  canvas.getContext("2d")?.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, SIZE, SIZE);
  bitmap.close();
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
  if (!blob) throw new Error("encode");
  return blob;
}

export function ProfileImageEditor({ user }: { user: Me }) {
  const { refresh } = useAuth();
  const input = useRef<HTMLInputElement>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (work: () => Promise<unknown>) => {
    setPending(true);
    setError(null);
    try {
      await work();
      await refresh();
    } catch (e) {
      setError(e instanceof Error && e.message !== "encode" ? e.message : "사진을 처리하지 못했습니다. 다른 사진을 골라 주세요.");
    } finally {
      setPending(false);
    }
  };

  const upload = (file: File) =>
    run(async () => {
      const body = await toSquareJpeg(file);
      await api("/api/users/me/avatar", { method: "PUT", body, headers: { "Content-Type": "image/jpeg" } });
    });

  return (
    <div className="flex flex-wrap items-center gap-4">
      <Avatar user={user} size="lg" />
      <div className="flex flex-col gap-2">
        <div className="flex flex-col gap-0.5">
          <h1 className="text-2xl font-bold">{user.nickname}</h1>
          <p className="break-all text-sm text-muted">{user.email}</p>
        </div>
        <div className="flex gap-2">
          <button type="button" className={button} disabled={pending} onClick={() => input.current?.click()}>
            {pending ? "처리하는 중…" : user.avatarUpdatedAt ? "사진 바꾸기" : "프로필 사진 올리기"}
          </button>
          {user.avatarUpdatedAt && (
            <button type="button" className={`${button} text-muted`} disabled={pending} onClick={() => run(() => api("/api/users/me/avatar", { method: "DELETE" }))}>
              사진 지우기
            </button>
          )}
        </div>
        <input
          ref={input}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (file) void upload(file);
          }}
        />
        <ErrorMessage message={error} />
      </div>
    </div>
  );
}
