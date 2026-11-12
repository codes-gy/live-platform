"use client";

import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { commentsReducer, emptyComments, fetchComments, type Comment } from "@/lib/comments";

type Options = {
  broadcastId: number;
  live: boolean;
  /** SSE가 방송 종료를 알려 줬을 때 */
  onEnded: () => void;
  /** SSE로 남의 새 댓글이 들어왔을 때(스크롤 판단용) */
  onIncoming: () => void;
};

function parse<T>(event: Event): T | null {
  try {
    return JSON.parse((event as MessageEvent<string>).data) as T;
  } catch {
    return null;
  }
}

const loadFailed = (e: unknown) => (e instanceof ApiError ? e.message : "댓글을 불러오지 못했습니다.");

/** 댓글 목록 상태 + 첫 페이지 · 이전 페이지 · 방송 중 실시간 수신. */
export function useComments({ broadcastId, live, onEnded, onIncoming }: Options) {
  const [state, dispatch] = useReducer(commentsReducer, emptyComments);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);

  const loadFirst = useCallback(() => {
    return fetchComments(broadcastId).then(
      (page) => {
        dispatch({ type: "first-page", page });
        setLoaded(true);
        setError(null);
      },
      (e) => setError(loadFailed(e)),
    );
  }, [broadcastId]);

  useEffect(() => {
    void loadFirst();
  }, [loadFirst]);

  const loadOlder = useCallback(() => {
    if (state.nextBefore === null || loadingOlder) return Promise.resolve();
    setLoadingOlder(true);
    return fetchComments(broadcastId, state.nextBefore)
      .then(
        (page) => dispatch({ type: "older-page", page }),
        (e) => setError(loadFailed(e)),
      )
      .finally(() => setLoadingOlder(false));
  }, [broadcastId, state.nextBefore, loadingOlder]);

  // 이벤트 핸들러는 최신 콜백을 쓰되 연결은 다시 맺지 않는다.
  const handlers = useRef({ onEnded, onIncoming });
  useEffect(() => {
    handlers.current = { onEnded, onIncoming };
  });

  useEffect(() => {
    if (!live) return;
    const source = new EventSource(`/api/broadcasts/${broadcastId}/comments/stream`);
    let dropped = false;
    source.addEventListener("error", () => {
      dropped = true;
    });
    source.addEventListener("open", () => {
      // 끊겼다 다시 붙으면 그 사이 놓친 이벤트를 첫 페이지로 메운다.
      if (dropped) {
        dropped = false;
        void loadFirst();
      }
    });
    source.addEventListener("comment", (e) => {
      const comment = parse<Comment>(e);
      if (!comment) return;
      dispatch({ type: "upsert", comment });
      handlers.current.onIncoming();
    });
    source.addEventListener("comment-updated", (e) => {
      const comment = parse<Comment>(e);
      if (comment) dispatch({ type: "edited", comment });
    });
    source.addEventListener("comment-deleted", (e) => {
      const deleted = parse<{ id: number }>(e);
      if (deleted) dispatch({ type: "remove", id: deleted.id });
    });
    source.addEventListener("ended", () => {
      source.close();
      handlers.current.onEnded();
    });
    return () => source.close();
  }, [broadcastId, live, loadFirst, handlers]);

  return { state, dispatch, loaded, error, loadingOlder, loadOlder, retry: loadFirst };
}
