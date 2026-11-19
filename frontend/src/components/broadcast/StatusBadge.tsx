import { STATUS_LABEL, type BroadcastStatus } from "@/lib/broadcast";

const STYLE: Record<BroadcastStatus, string> = {
  LIVE: "bg-live text-white",
  READY: "bg-surface text-fg border border-line",
  ENDED: "bg-fg/70 text-white",
};

export function StatusBadge({ status, className = "" }: { status: BroadcastStatus; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-xs font-bold ${STYLE[status]} ${className}`}>
      {status === "LIVE" && <span className="h-1.5 w-1.5 rounded-full bg-white motion-safe:animate-pulse" aria-hidden />}
      {status === "LIVE" ? "LIVE" : STATUS_LABEL[status]}
    </span>
  );
}
