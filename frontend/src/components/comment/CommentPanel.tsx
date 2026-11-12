"use client";

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Broadcast } from "@/lib/broadcast";
import { sortedComments } from "@/lib/comments";
import { useAuth } from "../auth/AuthProvider";
import { CommentComposer } from "./CommentComposer";
import { CommentItem } from "./CommentItem";
import { useComments } from "./useComments";

/** 맨 아래에서 이 픽셀 안이면 "아래를 보고 있다"고 본다. */
const NEAR_BOTTOM_PX = 80;

/** onEnded는 방송 상세를 다시 불러와 상태를 갱신하는 콜백. */
export function CommentPanel({ broadcast, onEnded }: { broadcast: Broadcast; onEnded: () => void }) {
  const { user } = useAuth();
  const listRef = useRef<HTMLDivElement>(null);
  const stickRef = useRef(true);
  const olderAnchorRef = useRef<number | null>(null);
  const [hasNew, setHasNew] = useState(false);

  const onIncoming = useCallback(() => {
    if (!stickRef.current) setHasNew(true);
  }, []);
  const { state, dispatch, loaded, error, loadingOlder, loadOlder, retry } = useComments({
    broadcastId: broadcast.id,
    live: broadcast.status === "LIVE",
    onEnded,
    onIncoming,
  });
  const comments = useMemo(() => sortedComments(state), [state]);
  const newestId = comments.at(-1)?.id;
  const oldestId = comments[0]?.id;

  const scrollToBottom = useCallback(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, []);

  useEffect(() => {
    if (stickRef.current) scrollToBottom();
  }, [newestId, scrollToBottom]);

  // 위에 이전 댓글이 붙어도 보던 위치가 밀리지 않게 한다.
  useLayoutEffect(() => {
    const el = listRef.current;
    if (el && olderAnchorRef.current !== null) {
      el.scrollTop = el.scrollHeight - olderAnchorRef.current;
      olderAnchorRef.current = null;
    }
  }, [oldestId]);

  function onScroll() {
    const el = listRef.current;
    if (!el) return;
    stickRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
    if (stickRef.current) setHasNew(false);
  }

  function showOlder() {
    const el = listRef.current;
    if (el) olderAnchorRef.current = el.scrollHeight - el.scrollTop;
    void loadOlder();
  }

  const anchorIso = broadcast.startedAt ?? broadcast.createdAt;

  return (
    <section aria-label="댓글" className="relative h-[32rem] lg:h-auto lg:min-h-[24rem]">
      <div className="absolute inset-0 flex flex-col overflow-hidden rounded-xl border border-line bg-surface">
        <h2 className="shrink-0 border-b border-line px-4 py-3 text-sm font-semibold">댓글</h2>

        <div className="relative min-h-0 flex-1">
          <div ref={listRef} onScroll={onScroll} className="h-full overflow-y-auto px-4 py-2">
            {state.hasOlder && (
              <button
                type="button"
                onClick={showOlder}
                disabled={loadingOlder}
                className="mb-1 w-full rounded-lg bg-surface-2 py-2 text-xs text-muted hover:bg-line disabled:opacity-60"
              >
                {loadingOlder ? "불러오는 중…" : "이전 댓글 더 보기"}
              </button>
            )}
            {!loaded && !error && <p className="py-8 text-center text-sm text-muted">댓글을 불러오는 중…</p>}
            {error && (
              <div className="flex flex-col items-center gap-2 py-6 text-center text-sm text-muted">
                <p role="alert">{error}</p>
                <button type="button" onClick={() => void retry()} className="rounded-lg bg-surface-2 px-3 py-1.5 hover:bg-line">
                  다시 시도
                </button>
              </div>
            )}
            {loaded && comments.length === 0 && <p className="py-8 text-center text-sm text-muted">아직 댓글이 없습니다. 첫 댓글을 남겨 보세요.</p>}
            <ul className="divide-y divide-line/60">
              {comments.map((c) => (
                <CommentItem
                  key={c.id}
                  comment={c}
                  broadcastId={broadcast.id}
                  anchorIso={anchorIso}
                  isAuthor={user?.id === c.author.id}
                  isBroadcaster={c.author.id === broadcast.owner.id}
                  canDelete={user?.id === c.author.id || user?.id === broadcast.owner.id}
                  onChanged={(comment) => dispatch({ type: "upsert", comment })}
                  onRemoved={(id) => dispatch({ type: "remove", id })}
                />
              ))}
            </ul>
          </div>
          {hasNew && (
            <button
              type="button"
              onClick={scrollToBottom}
              className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-live px-3 py-1.5 text-xs font-semibold text-white shadow-lg hover:bg-live-strong"
            >
              새 댓글 ↓
            </button>
          )}
        </div>

        <div className="shrink-0 border-t border-line p-3">
          <CommentComposer
            broadcast={broadcast}
            onSent={(comment) => {
              stickRef.current = true;
              dispatch({ type: "upsert", comment });
            }}
            onEnded={onEnded}
          />
        </div>
      </div>
    </section>
  );
}
