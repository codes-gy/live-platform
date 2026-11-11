"use client";

import Link from "next/link";
import type { Broadcast } from "@/lib/broadcast";
import { useAuth } from "../auth/AuthProvider";
import { DeleteBroadcast } from "./DeleteBroadcast";

const linkClass = "rounded-lg border border-line px-3 py-2 text-sm hover:bg-surface-2";

/** 방송 소유자에게만 보이는 관리 버튼. 권한은 서버가 다시 확인한다. */
export function OwnerActions({ broadcast }: { broadcast: Broadcast }) {
  const { user } = useAuth();
  if (user?.id !== broadcast.owner.id) return null;
  return (
    <div className="flex flex-wrap items-start gap-2">
      {broadcast.status !== "ENDED" && (
        <Link href={`/studio/${broadcast.id}`} className="rounded-lg bg-live px-3 py-2 text-sm font-semibold text-white hover:bg-live-strong">
          송출 화면
        </Link>
      )}
      <Link href={`/broadcasts/${broadcast.id}/edit`} className={linkClass}>
        수정
      </Link>
      <DeleteBroadcast broadcast={broadcast} />
    </div>
  );
}
