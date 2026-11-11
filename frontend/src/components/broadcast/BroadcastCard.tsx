import Link from "next/link";
import { describeTime, type Broadcast } from "@/lib/broadcast";
import { StatusBadge } from "./StatusBadge";

/** 썸네일 대신 방송 상태를 보여 주는 화면 자리. 방송 중이면 붉게 빛난다. */
function Screen({ broadcast }: { broadcast: Broadcast }) {
  const live = broadcast.status === "LIVE";
  return (
    <div
      className={`relative flex aspect-video items-center justify-center overflow-hidden rounded-xl border ${
        live ? "border-live/50 bg-[radial-gradient(ellipse_at_center,rgba(255,59,69,0.25),transparent_70%)]" : "border-line bg-surface-2"
      }`}
    >
      <span className={`px-6 text-center text-sm font-semibold ${live ? "text-fg" : "text-muted"} line-clamp-2`}>
        {broadcast.title}
      </span>
      <StatusBadge status={broadcast.status} className="absolute left-2 top-2" />
    </div>
  );
}

export function BroadcastCard({ broadcast, now }: { broadcast: Broadcast; now: number }) {
  return (
    <Link href={`/broadcasts/${broadcast.id}`} className="group flex flex-col gap-2 rounded-xl focus-visible:outline-offset-4">
      <Screen broadcast={broadcast} />
      <div className="flex min-w-0 flex-col gap-0.5 px-0.5">
        <h3 className="truncate font-semibold group-hover:underline group-hover:underline-offset-4">{broadcast.title}</h3>
        <p className="truncate text-sm text-muted">{broadcast.owner.nickname}</p>
        <p className="text-xs text-muted">{describeTime(broadcast, now)}</p>
      </div>
    </Link>
  );
}
