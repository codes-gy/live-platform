"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Broadcast, BroadcastStatus, Page } from "@/lib/broadcast";
import { BroadcastCard } from "./BroadcastCard";

type Tab = { key: string; label: string; status?: BroadcastStatus };

const TABS: Tab[] = [
  { key: "live", label: "방송 중", status: "LIVE" },
  { key: "all", label: "전체" },
  { key: "ended", label: "종료된 방송", status: "ENDED" },
];

const PAGE_SIZE = 12;
/** 방송 중 탭은 새 방송 · 종료를 반영하려고 주기적으로 첫 페이지를 다시 읽는다. */
const LIVE_REFRESH_MS = 20_000;

type State =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; items: Broadcast[]; hasNext: boolean; page: number; total: number };

function query(tab: Tab, page: number) {
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (tab.status) params.set("status", tab.status);
  return `/api/broadcasts?${params}`;
}

async function fetchFirst(tab: Tab): Promise<State> {
  try {
    const page = await api<Page<Broadcast>>(query(tab, 0));
    return { kind: "ok", items: page.items, hasNext: page.hasNext, page: 0, total: page.totalElements };
  } catch (e) {
    return { kind: "error", message: e instanceof ApiError ? e.message : "목록을 불러오지 못했습니다." };
  }
}

export function BroadcastList() {
  const [tab, setTab] = useState<Tab>(TABS[0]);
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
    const refresh = () => fetchFirst(tab).then((result) => active && apply(result));
    refresh();
    const timer = tab.status === "LIVE" ? setInterval(refresh, LIVE_REFRESH_MS) : undefined;
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [tab, apply]);

  const selectTab = (next: Tab) => {
    if (next.key === tab.key) return;
    setState({ kind: "loading" });
    setTab(next);
  };

  const loadMore = async () => {
    if (state.kind !== "ok") return;
    setLoadingMore(true);
    try {
      const page = await api<Page<Broadcast>>(query(tab, state.page + 1));
      setState({
        kind: "ok",
        // 그사이 새 방송이 생겨 밀린 항목이 겹칠 수 있어 ID로 거른다
        items: [...state.items, ...page.items.filter((b) => !state.items.some((x) => x.id === b.id))],
        hasNext: page.hasNext,
        page: state.page + 1,
        total: page.totalElements,
      });
    } catch (e) {
      setState({ kind: "error", message: e instanceof ApiError ? e.message : "목록을 불러오지 못했습니다." });
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <section className="flex flex-col gap-5" aria-labelledby="broadcast-list-title">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 id="broadcast-list-title" className="text-xl font-bold">
          {tab.label}
          {state.kind === "ok" && <span className="ml-2 text-base font-medium text-muted">{state.total}</span>}
        </h2>
        <div role="tablist" aria-label="방송 상태" className="flex rounded-lg border border-line bg-surface p-1 text-sm">
          {TABS.map((t) => (
            <button
              key={t.key}
              role="tab"
              type="button"
              aria-selected={t.key === tab.key}
              onClick={() => selectTab(t)}
              className={`rounded-md px-3 py-1.5 ${t.key === tab.key ? "bg-surface-2 font-semibold text-fg" : "text-muted hover:text-fg"}`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {state.kind === "loading" && (
        <div className="grid gap-x-4 gap-y-6 sm:grid-cols-2 lg:grid-cols-3" aria-busy>
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="flex flex-col gap-2">
              <div className="aspect-video animate-pulse rounded-xl bg-surface-2" />
              <div className="h-4 w-2/3 animate-pulse rounded bg-surface-2" />
              <div className="h-3 w-1/3 animate-pulse rounded bg-surface-2" />
            </div>
          ))}
        </div>
      )}

      {state.kind === "error" && (
        <div className="flex flex-col items-start gap-3 rounded-xl border border-line bg-surface p-6">
          <p className="text-sm">{state.message}</p>
          <button type="button" onClick={() => { setState({ kind: "loading" }); fetchFirst(tab).then(apply); }} className="rounded-lg bg-surface-2 px-3 py-1.5 text-sm hover:bg-line">
            다시 시도
          </button>
        </div>
      )}

      {state.kind === "ok" && state.items.length === 0 && (
        <div className="rounded-xl border border-dashed border-line px-6 py-14 text-center">
          <p className="font-semibold">{tab.status === "LIVE" ? "지금 방송 중인 채널이 없습니다" : "아직 방송이 없습니다"}</p>
          <p className="mt-1 text-sm text-muted">로그인하고 직접 방송을 시작해 보세요.</p>
        </div>
      )}

      {state.kind === "ok" && state.items.length > 0 && (
        <>
          <ul className="grid gap-x-4 gap-y-6 sm:grid-cols-2 lg:grid-cols-3">
            {state.items.map((b) => (
              <li key={b.id}>
                <BroadcastCard broadcast={b} now={now} />
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
