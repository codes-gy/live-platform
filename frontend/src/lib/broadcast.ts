export type BroadcastStatus = "READY" | "LIVE" | "ENDED";

export type Broadcast = {
  id: number;
  title: string;
  description: string | null;
  status: BroadcastStatus;
  owner: { id: number; nickname: string };
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type LiveAccess = {
  serverUrl: string;
  roomName: string;
  identity: string;
  token: string;
  broadcast: Broadcast;
};

export const STATUS_LABEL: Record<BroadcastStatus, string> = {
  LIVE: "방송 중",
  READY: "방송 전",
  ENDED: "종료",
};

const dateTime = new Intl.DateTimeFormat("ko-KR", {
  month: "long",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});

export function formatDateTime(iso: string | null): string {
  return iso ? dateTime.format(new Date(iso)) : "";
}

/** "3분 전", "2시간 전" 같은 지난 시간. 하루가 넘으면 날짜로. */
export function formatAgo(iso: string | null, now = Date.now()): string {
  if (!iso) return "";
  const minutes = Math.floor((now - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  return formatDateTime(iso);
}

/** 방송 시간(시작~종료 또는 지금까지). "1시간 5분" */
export function formatDuration(fromIso: string | null, toIso: string | null, now = Date.now()): string {
  if (!fromIso) return "";
  const end = toIso ? new Date(toIso).getTime() : now;
  const minutes = Math.max(0, Math.floor((end - new Date(fromIso).getTime()) / 60_000));
  const hours = Math.floor(minutes / 60);
  return hours > 0 ? `${hours}시간 ${minutes % 60}분` : `${minutes}분`;
}

/** 목록 카드 위 한 줄: 방송 중이면 시작 시각, 종료면 끝난 시각, 방송 전이면 등록 시각 */
export function describeTime(b: Broadcast, now = Date.now()): string {
  switch (b.status) {
    case "LIVE":
      return `${formatAgo(b.startedAt, now)} 시작`;
    case "ENDED":
      return `${formatAgo(b.endedAt, now)} 종료 · ${formatDuration(b.startedAt, b.endedAt, now)} 방송`;
    case "READY":
      return `${formatAgo(b.createdAt, now)} 등록`;
  }
}
