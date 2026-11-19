"use client";

import type { Room } from "livekit-client";
import { useEffect, useState } from "react";

const POLL_MS = 3_000;

/**
 * 지금 방송을 보고 있는 사람 수(방송자 제외).
 * 시청자는 hidden으로 들어와 참가자 목록에는 안 보이지만, 서버가 주는 방 정보(numParticipants)에는 포함된다.
 * 이 값은 바뀔 때 이벤트가 따로 없어 짧은 간격으로 읽는다. 방 정보를 아직 못 받았으면 null.
 */
export function useViewerCount(room: Room | null | undefined): number | null {
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    if (!room) return;
    const read = () => setCount(room.numParticipants > 0 ? Math.max(0, room.numParticipants - 1) : null);
    const first = setTimeout(read, 0);
    const timer = setInterval(read, POLL_MS);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [room]);
  return room ? count : null;
}

export function ViewerCount({ count, className = "" }: { count: number | null; className?: string }) {
  if (count === null) return null;
  return (
    <span className={`inline-flex items-center gap-1 tabular-nums ${className}`} aria-label={`시청자 ${count}명`}>
      <svg viewBox="0 0 20 20" fill="currentColor" className="h-3.5 w-3.5" aria-hidden>
        <path d="M10 4C5.5 4 2.3 7.1 1 10c1.3 2.9 4.5 6 9 6s7.7-3.1 9-6c-1.3-2.9-4.5-6-9-6Zm0 9.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7Zm0-2a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" />
      </svg>
      {count}
    </span>
  );
}
