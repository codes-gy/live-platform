"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { formatDateTime, formatDuration, type Broadcast } from "@/lib/broadcast";
import { LivePlayer } from "./LivePlayer";
import { StatusBadge } from "./StatusBadge";

/** 방송 전이면 시작했는지 이 간격으로 확인해, 시작되면 바로 시청 화면으로 바꾼다. */
const READY_POLL_MS = 10_000;

type State =
  | { kind: "loading" }
  | { kind: "not-found" }
  | { kind: "error"; message: string }
  | { kind: "ok"; broadcast: Broadcast };

async function fetchBroadcast(id: number): Promise<State> {
  try {
    return { kind: "ok", broadcast: await api<Broadcast>(`/api/broadcasts/${id}`) };
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.code === "INVALID_INPUT")) {
      return { kind: "not-found" };
    }
    return { kind: "error", message: e instanceof ApiError ? e.message : "방송 정보를 불러오지 못했습니다." };
  }
}

export function BroadcastDetail({ id }: { id: number }) {
  const [state, setState] = useState<State>({ kind: "loading" });

  const load = useCallback(() => {
    fetchBroadcast(id).then(setState);
  }, [id]);

  useEffect(() => {
    let active = true;
    fetchBroadcast(id).then((result) => active && setState(result));
    return () => {
      active = false;
    };
  }, [id]);

  const status = state.kind === "ok" ? state.broadcast.status : null;
  useEffect(() => {
    if (status !== "READY") return;
    const timer = setInterval(load, READY_POLL_MS);
    return () => clearInterval(timer);
  }, [status, load]);

  if (state.kind === "loading") {
    return (
      <div className="flex flex-col gap-4" aria-busy>
        <div className="aspect-video w-full animate-pulse rounded-xl bg-surface-2" />
        <div className="h-6 w-1/2 animate-pulse rounded bg-surface-2" />
      </div>
    );
  }
  if (state.kind === "not-found") {
    return (
      <Message title="방송을 찾을 수 없습니다" description="삭제되었거나 잘못된 주소입니다.">
        <Link href="/" className="rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line">
          방송 목록으로
        </Link>
      </Message>
    );
  }
  if (state.kind === "error") {
    return (
      <Message title={state.message}>
        <button type="button" onClick={load} className="rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line">
          다시 시도
        </button>
      </Message>
    );
  }

  const b = state.broadcast;
  return (
    <article className="flex flex-col gap-5">
      {b.status === "LIVE" ? (
        // key로 방송이 바뀌면 플레이어를 새로 만든다
        <LivePlayer key={b.id} broadcastId={b.id} onEnded={load} />
      ) : (
        <OfflineScreen broadcast={b} />
      )}

      <header className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <StatusBadge status={b.status} />
          <span className="text-sm text-muted">{b.owner.nickname}</span>
        </div>
        <h1 className="text-xl font-bold break-words sm:text-2xl">{b.title}</h1>
        <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted">
          {b.startedAt && (
            <div className="flex gap-1">
              <dt>시작</dt>
              <dd>{formatDateTime(b.startedAt)}</dd>
            </div>
          )}
          {b.endedAt && (
            <div className="flex gap-1">
              <dt>종료</dt>
              <dd>
                {formatDateTime(b.endedAt)} ({formatDuration(b.startedAt, b.endedAt)})
              </dd>
            </div>
          )}
          {!b.startedAt && (
            <div className="flex gap-1">
              <dt>등록</dt>
              <dd>{formatDateTime(b.createdAt)}</dd>
            </div>
          )}
        </dl>
      </header>

      {b.description && (
        <p className="whitespace-pre-wrap break-words rounded-xl border border-line bg-surface p-4 text-sm leading-relaxed">{b.description}</p>
      )}
    </article>
  );
}

function OfflineScreen({ broadcast }: { broadcast: Broadcast }) {
  const ended = broadcast.status === "ENDED";
  return (
    <div className="flex aspect-video w-full flex-col items-center justify-center gap-2 rounded-xl border border-line bg-surface p-6 text-center" role="status">
      <p className="text-lg font-semibold">{ended ? "방송이 종료되었습니다" : "아직 방송 전입니다"}</p>
      <p className="text-sm text-muted">
        {ended ? `${formatDuration(broadcast.startedAt, broadcast.endedAt)} 동안 방송했습니다.` : "방송이 시작되면 이 화면에서 바로 이어서 볼 수 있습니다."}
      </p>
    </div>
  );
}

function Message({ title, description, children }: { title: string; description?: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-line bg-surface px-6 py-16 text-center">
      <p className="text-lg font-semibold">{title}</p>
      {description && <p className="text-sm text-muted">{description}</p>}
      {children}
    </div>
  );
}
