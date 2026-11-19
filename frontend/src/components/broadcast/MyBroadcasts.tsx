"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { describeTime, type Broadcast, type Page } from "@/lib/broadcast";
import { StatusBadge } from "./StatusBadge";

const PAGE_SIZE = 20;

type State =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; items: Broadcast[]; hasNext: boolean; page: number; total: number };

const query = (ownerId: number, page: number) => `/api/broadcasts?ownerId=${ownerId}&page=${page}&size=${PAGE_SIZE}`;

async function fetchFirst(ownerId: number): Promise<State> {
  try {
    const page = await api<Page<Broadcast>>(query(ownerId, 0));
    return { kind: "ok", items: page.items, hasNext: page.hasNext, page: 0, total: page.totalElements };
  } catch (e) {
    return { kind: "error", message: e instanceof ApiError ? e.message : "내 방송을 불러오지 못했습니다." };
  }
}

/** 내가 만든 방송(최신순). 화면에 다시 들어올 때마다 이펙트가 다시 돌아 첫 페이지를 새로 읽는다. */
export function MyBroadcasts({ ownerId }: { ownerId: number }) {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [loadingMore, setLoadingMore] = useState(false);
  // 시각은 데이터를 받을 때 정한다(미리 렌더링할 때 Date.now()를 쓰지 않는다)
  const [now, setNow] = useState(0);

  const apply = useCallback((result: State) => {
    setNow(Date.now());
    setState(result);
  }, []);

  useEffect(() => {
    let active = true;
    fetchFirst(ownerId).then((result) => active && apply(result));
    return () => {
      active = false;
    };
  }, [ownerId, apply]);

  const loadMore = async () => {
    if (state.kind !== "ok") return;
    setLoadingMore(true);
    try {
      const page = await api<Page<Broadcast>>(query(ownerId, state.page + 1));
      setState({
        kind: "ok",
        items: [...state.items, ...page.items.filter((b) => !state.items.some((x) => x.id === b.id))],
        hasNext: page.hasNext,
        page: state.page + 1,
        total: page.totalElements,
      });
    } catch (e) {
      setState({ kind: "error", message: e instanceof ApiError ? e.message : "내 방송을 불러오지 못했습니다." });
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <section className="flex flex-col gap-4" aria-labelledby="my-broadcasts-title">
      <div className="flex items-center justify-between gap-3">
        <h2 id="my-broadcasts-title" className="text-lg font-bold">
          내 방송
          {state.kind === "ok" && <span className="ml-2 text-base font-medium text-muted">{state.total}</span>}
        </h2>
        <Link href="/broadcasts/new" className="rounded-lg bg-live px-4 py-2 text-sm font-semibold text-white hover:bg-live-strong">
          방송 만들기
        </Link>
      </div>

      {state.kind === "loading" && (
        <ul className="flex flex-col gap-2" aria-busy>
          {Array.from({ length: 3 }, (_, i) => (
            <li key={i} className="h-[4.5rem] animate-pulse rounded-xl bg-surface-2" />
          ))}
        </ul>
      )}

      {state.kind === "error" && (
        <div className="flex flex-col items-start gap-3 rounded-xl border border-line bg-surface p-6">
          <p className="text-sm">{state.message}</p>
          <button
            type="button"
            onClick={() => {
              setState({ kind: "loading" });
              fetchFirst(ownerId).then(apply);
            }}
            className="rounded-lg bg-surface-2 px-3 py-1.5 text-sm hover:bg-line"
          >
            다시 시도
          </button>
        </div>
      )}

      {state.kind === "ok" && state.items.length === 0 && (
        <div className="rounded-xl border border-dashed border-line px-6 py-14 text-center">
          <p className="font-semibold">아직 만든 방송이 없습니다</p>
          <p className="mt-1 text-sm text-muted">방송을 만들고 송출 화면에서 바로 시작해 보세요.</p>
        </div>
      )}

      {state.kind === "ok" && state.items.length > 0 && (
        <>
          <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-xl border border-line bg-surface">
            {state.items.map((b) => (
              <li key={b.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <Link href={`/broadcasts/${b.id}`} className="group flex min-w-0 flex-col gap-1">
                  <span className="flex min-w-0 items-center gap-2">
                    <StatusBadge status={b.status} className="shrink-0" />
                    <span className="truncate font-semibold group-hover:underline group-hover:underline-offset-4">{b.title}</span>
                  </span>
                  <span className="text-xs text-muted">{describeTime(b, now)}</span>
                </Link>
                <div className="flex shrink-0 gap-2 text-sm">
                  {b.status !== "ENDED" && (
                    <Link href={`/studio/${b.id}`} className="rounded-lg border border-live/50 px-3 py-1.5 text-fg hover:bg-live/10">
                      송출 화면
                    </Link>
                  )}
                  <Link href={`/broadcasts/${b.id}/edit`} className="rounded-lg border border-line px-3 py-1.5 hover:bg-surface-2">
                    수정
                  </Link>
                </div>
              </li>
            ))}
          </ul>
          {state.hasNext && (
            <button
              type="button"
              onClick={loadMore}
              disabled={loadingMore}
              className="self-center rounded-lg border border-line px-4 py-2 text-sm hover:bg-surface-2 disabled:opacity-60"
            >
              {loadingMore ? "불러오는 중…" : "더 보기"}
            </button>
          )}
        </>
      )}
    </section>
  );
}
