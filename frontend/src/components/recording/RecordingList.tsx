"use client";

import { useEffect, useState } from "react";
import { fetchRecordings, type Recording, type RecordingPageLoad } from "@/lib/recordings";
import { Notice } from "../ui";
import { RecordingCard } from "./RecordingCard";

type State = { kind: "loading" } | RecordingPageLoad;

export function RecordingList() {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let active = true;
    fetchRecordings(0).then((result) => active && setState(result));
    return () => {
      active = false;
    };
  }, []);

  const more = () => {
    if (state.kind !== "ok") return;
    setLoadingMore(true);
    fetchRecordings(state.page + 1).then((next) => {
      setLoadingMore(false);
      if (next.kind !== "ok") return;
      setState((prev) => {
        if (prev.kind !== "ok") return next;
        const seen = new Set(prev.items.map((r) => r.id));
        const items: Recording[] = [...prev.items, ...next.items.filter((r) => !seen.has(r.id))];
        return { ...next, items };
      });
    });
  };

  if (state.kind === "loading") {
    return (
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4" aria-busy>
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="aspect-video animate-pulse rounded-xl bg-surface-2" />
        ))}
      </div>
    );
  }
  if (state.kind === "error") {
    return <Notice title={state.message} description="잠시 후 다시 시도해 주세요." />;
  }
  if (state.items.length === 0) {
    return <Notice title="아직 다시보기가 없습니다" description="방송이 끝나면 녹화 영상이 여기에 올라옵니다." />;
  }
  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {state.items.map((r) => (
          <RecordingCard key={r.id} recording={r} />
        ))}
      </div>
      {state.hasNext && (
        <button type="button" onClick={more} disabled={loadingMore} className="self-center rounded-lg border border-line px-5 py-2 text-sm hover:bg-surface-2 disabled:opacity-60">
          {loadingMore ? "불러오는 중…" : "더 보기"}
        </button>
      )}
    </div>
  );
}
