import Link from "next/link";
import { describeTime, formatAgo, formatDuration, type Broadcast } from "@/lib/broadcast";
import { OwnerAvatar } from "./OwnerAvatar";
import { StatusBadge } from "./StatusBadge";

/** 영상 썸네일 대신 누구의 방송인지와 상태를 보여 준다. 제목은 카드 아래에만 둔다. */
function Screen({ broadcast, now }: { broadcast: Broadcast; now: number }) {
  const live = broadcast.status === "LIVE";
  const elapsed = formatDuration(broadcast.startedAt, null, now);
  const caption = live
    ? elapsed === "1분 미만" ? "방금 시작" : `${elapsed}째 방송 중`
    : broadcast.status === "ENDED"
      ? `${formatDuration(broadcast.startedAt, broadcast.endedAt, now)} 방송`
      : "방송 예정";
  return (
    <div
      className={`relative flex aspect-video flex-col items-center justify-center gap-2 overflow-hidden rounded-xl border transition-colors ${
        live
          ? "border-live/50 bg-[radial-gradient(ellipse_at_center,rgba(255,59,69,0.22),transparent_70%)] group-hover:border-live"
          : "border-line bg-surface-2 group-hover:border-muted/50"
      }`}
    >
      <OwnerAvatar nickname={broadcast.owner.nickname} live={live} />
      <span className={`text-xs ${live ? "text-fg/80" : "text-muted"}`}>{caption}</span>
      <StatusBadge status={broadcast.status} className="absolute left-2 top-2" />
    </div>
  );
}

export function BroadcastCard({ broadcast, now }: { broadcast: Broadcast; now: number }) {
  // 방송 중 경과 시간 · 종료 방송의 방송 시간은 화면 자리에 이미 있다
  const when =
    broadcast.status === "LIVE" ? null : broadcast.status === "ENDED" ? `${formatAgo(broadcast.endedAt, now)} 종료` : describeTime(broadcast, now);
  return (
    <Link href={`/broadcasts/${broadcast.id}`} className="group flex flex-col gap-2 rounded-xl focus-visible:outline-offset-4">
      <Screen broadcast={broadcast} now={now} />
      <div className="flex min-w-0 flex-col gap-0.5 px-0.5">
        <h3 className="line-clamp-2 font-semibold group-hover:underline group-hover:underline-offset-4">{broadcast.title}</h3>
        <p className="truncate text-sm text-muted">
          {broadcast.owner.nickname}
          {when && ` · ${when}`}
        </p>
      </div>
    </Link>
  );
}
