"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchBroadcastRecordings, formatPlayTime, type Recording } from "@/lib/recordings";

/** 종료된 방송 상세: 다시보기가 있으면 바로가기, 만드는 중이면 안내. 녹화가 없으면 아무것도 안 보인다. */
export function BroadcastRecordings({ broadcastId }: { broadcastId: number }) {
  const [recordings, setRecordings] = useState<Recording[]>([]);

  useEffect(() => {
    let active = true;
    fetchBroadcastRecordings(broadcastId).then((list) => active && setRecordings(list));
    return () => {
      active = false;
    };
  }, [broadcastId]);

  const ready = recordings.find((r) => r.status === "READY");
  const pending = !ready && recordings.some((r) => r.status === "RECORDING");
  if (ready) {
    return (
      <Link href={`/recordings/${ready.id}`} className="inline-flex w-fit items-center gap-2 rounded-lg bg-live px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-live-strong">
        ▶ 다시보기{ready.durationMs != null && <span className="font-mono tabular-nums opacity-90">{formatPlayTime(ready.durationMs)}</span>}
      </Link>
    );
  }
  if (pending) {
    return <p className="text-sm text-muted">녹화 영상을 만드는 중입니다. 잠시 뒤 다시보기가 열립니다.</p>;
  }
  return null;
}
