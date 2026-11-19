"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Avatar } from "../Avatar";
import { formatDateTime } from "@/lib/broadcast";
import { fetchRecording, formatPlayTime, formatSize, type RecordingLoad } from "@/lib/recordings";
import { Notice } from "../ui";

type State = { kind: "loading" } | RecordingLoad;

export function RecordingDetail({ id }: { id: number }) {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let active = true;
    fetchRecording(id).then((result) => active && setState(result));
    return () => {
      active = false;
    };
  }, [id]);

  if (state.kind === "loading") {
    return <div className="aspect-video w-full animate-pulse rounded-xl bg-surface-2" aria-busy />;
  }
  if (state.kind === "not-found") {
    return (
      <Notice title="녹화를 찾을 수 없습니다" description="삭제되었거나 잘못된 주소입니다.">
        <Link href="/recordings" className="rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line">
          다시보기 목록으로
        </Link>
      </Notice>
    );
  }
  if (state.kind === "error") {
    return <Notice title={state.message} description="잠시 후 다시 시도해 주세요." />;
  }

  const r = state.recording;
  const b = r.broadcast;
  return (
    <article className="flex flex-col gap-5">
      {r.playbackUrl ? (
        // 서명된 주소는 몇 시간 뒤 만료되므로 페이지를 열 때마다 새로 받는다
        <video key={r.id} src={r.playbackUrl} controls playsInline preload="metadata" className="aspect-video w-full rounded-xl bg-black" />
      ) : (
        <div className="flex aspect-video w-full flex-col items-center justify-center gap-2 rounded-xl border border-line bg-surface p-6 text-center" role="status">
          <p className="text-lg font-semibold">{r.status === "RECORDING" ? "녹화 영상을 만드는 중입니다" : "녹화에 실패했습니다"}</p>
          <p className="text-sm text-muted">
            {r.status === "RECORDING" ? "방송이 끝나고 잠시 뒤 재생할 수 있습니다." : "이 방송은 다시보기를 제공하지 않습니다."}
          </p>
        </div>
      )}
      <header className="flex flex-col gap-2">
        <div className="flex items-center gap-2 text-sm text-muted">
          <span className="rounded-md bg-surface-2 px-1.5 py-0.5 text-xs font-semibold text-fg">다시보기</span>
          <span className="flex min-w-0 items-center gap-1.5">
            <Avatar user={b.owner} size="sm" />
            <span className="truncate">{b.owner.nickname}</span>
          </span>
        </div>
        <h1 className="text-xl font-bold break-words sm:text-2xl">{b.title}</h1>
        <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted">
          <div className="flex gap-1">
            <dt>방송</dt>
            <dd>{formatDateTime(b.startedAt)}</dd>
          </div>
          {r.durationMs != null && (
            <div className="flex gap-1">
              <dt>길이</dt>
              <dd className="tabular-nums">{formatPlayTime(r.durationMs)}</dd>
            </div>
          )}
          {r.sizeBytes != null && (
            <div className="flex gap-1">
              <dt>크기</dt>
              <dd className="tabular-nums">{formatSize(r.sizeBytes)}</dd>
            </div>
          )}
        </dl>
        <div className="flex flex-wrap gap-2">
          <Link href={`/broadcasts/${b.id}`} className="rounded-lg border border-line px-3 py-1.5 text-sm hover:bg-surface-2">
            방송 정보 · 댓글 보기
          </Link>
          <Link href="/recordings" className="rounded-lg border border-line px-3 py-1.5 text-sm hover:bg-surface-2">
            다른 다시보기
          </Link>
        </div>
      </header>
      {b.description && (
        <p className="whitespace-pre-wrap break-words rounded-xl border border-line bg-surface p-4 text-sm leading-relaxed">{b.description}</p>
      )}
    </article>
  );
}
