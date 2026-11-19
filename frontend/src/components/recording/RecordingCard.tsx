"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { Avatar } from "../Avatar";
import { formatDateTime } from "@/lib/broadcast";
import { formatPlayTime, type Recording } from "@/lib/recordings";

/** 다시보기 카드: 방송 마지막 썸네일 + 재생 시간. */
export function RecordingCard({ recording }: { recording: Recording }) {
  const b = recording.broadcast;
  const src = b.thumbnailUpdatedAt && `/api/broadcasts/${b.id}/thumbnail?v=${encodeURIComponent(b.thumbnailUpdatedAt)}`;
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const shown = src && src !== failedSrc ? src : null;
  return (
    <Link href={`/recordings/${recording.id}`} className="group flex flex-col gap-2 rounded-xl focus-visible:outline-offset-4">
      <div className="relative flex aspect-video items-center justify-center overflow-hidden rounded-xl border border-line bg-surface-2 shadow-sm transition group-hover:-translate-y-0.5 group-hover:shadow-md">
        {shown ? (
          <Image src={shown} alt="" fill unoptimized sizes="(min-width: 1024px) 25vw, 50vw" className="bg-black object-contain" onError={() => setFailedSrc(shown)} />
        ) : (
          <Avatar user={b.owner} />
        )}
        <span className="absolute left-2 top-2 rounded-md bg-black/70 px-1.5 py-0.5 text-[11px] font-semibold text-white">다시보기</span>
        {recording.durationMs != null && (
          <span className="absolute bottom-2 right-2 rounded-md bg-black/75 px-1.5 py-0.5 font-mono text-xs tabular-nums text-white">
            {formatPlayTime(recording.durationMs)}
          </span>
        )}
      </div>
      <div className="flex min-w-0 flex-col gap-0.5 px-0.5">
        <h3 className="line-clamp-2 font-semibold group-hover:underline group-hover:underline-offset-4">{b.title}</h3>
        <p className="truncate text-sm text-muted">
          {b.owner.nickname} · {formatDateTime(b.startedAt ?? recording.createdAt)}
        </p>
      </div>
    </Link>
  );
}
