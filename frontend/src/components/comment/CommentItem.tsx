"use client";

import { useState } from "react";
import { api, ApiError, json } from "@/lib/api";
import { COMMENT_MAX, formatCommentTime, type Comment } from "@/lib/comments";
import { ErrorMessage } from "../ui";

type Props = {
  comment: Comment;
  broadcastId: number;
  /** 시간 표기 기준(방송 시작 시각) */
  anchorIso: string;
  isAuthor: boolean;
  isBroadcaster: boolean;
  canDelete: boolean;
  onChanged: (comment: Comment) => void;
  onRemoved: (id: number) => void;
};

type Mode = { kind: "view" } | { kind: "edit"; text: string } | { kind: "confirm-delete" };

const smallButton = "rounded px-1.5 py-0.5 text-xs text-muted hover:bg-surface-2 hover:text-fg disabled:opacity-60";

export function CommentItem({ comment, broadcastId, anchorIso, isAuthor, isBroadcaster, canDelete, onChanged, onRemoved }: Props) {
  const [mode, setMode] = useState<Mode>({ kind: "view" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const url = `/api/broadcasts/${broadcastId}/comments/${comment.id}`;

  async function run(action: () => Promise<void>, fallback: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      if (e instanceof ApiError && e.code === "COMMENT_NOT_FOUND") {
        onRemoved(comment.id);
        return;
      }
      setError(e instanceof ApiError ? e.message : fallback);
    } finally {
      setBusy(false);
    }
  }

  const save = (text: string) =>
    run(async () => {
      onChanged(await api<Comment>(url, { method: "PATCH", body: json({ content: text.trim() }) }));
      setMode({ kind: "view" });
    }, "댓글을 수정하지 못했습니다.");

  const remove = () =>
    run(async () => {
      await api<void>(url, { method: "DELETE" });
      onRemoved(comment.id);
    }, "댓글을 삭제하지 못했습니다.");

  return (
    <li className="flex flex-col gap-1 py-2">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs">
        <span className="font-semibold">{comment.author.nickname}</span>
        {isBroadcaster && <span className="rounded bg-live/15 px-1.5 py-0.5 text-[10px] font-semibold text-live">방송자</span>}
        <span className="text-muted">{formatCommentTime(comment.createdAt, anchorIso)}</span>
        {comment.updatedAt && <span className="text-muted">(수정됨)</span>}
      </div>

      {mode.kind === "edit" ? (
        <div className="flex flex-col gap-1.5">
          <textarea
            value={mode.text}
            onChange={(e) => setMode({ kind: "edit", text: e.target.value })}
            disabled={busy}
            rows={3}
            maxLength={COMMENT_MAX}
            aria-label="댓글 수정"
            autoFocus
            className="resize-none rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm outline-none focus:border-live disabled:opacity-60"
          />
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted">
              {mode.text.length}/{COMMENT_MAX}
            </span>
            <div className="flex gap-1">
              <button type="button" className={smallButton} disabled={busy} onClick={() => setMode({ kind: "view" })}>
                취소
              </button>
              <button
                type="button"
                className="rounded bg-live px-2 py-0.5 text-xs font-semibold text-white hover:bg-live-strong disabled:opacity-60"
                disabled={busy || mode.text.trim().length === 0}
                onClick={() => save(mode.text)}
              >
                저장
              </button>
            </div>
          </div>
        </div>
      ) : (
        <p className="text-sm whitespace-pre-wrap break-words">{comment.content}</p>
      )}

      <ErrorMessage message={error} />

      {mode.kind === "confirm-delete" ? (
        <div className="flex items-center gap-2 text-xs">
          <span className="text-muted">이 댓글을 삭제할까요?</span>
          <button type="button" className="rounded bg-danger/15 px-2 py-0.5 font-semibold text-danger hover:bg-danger/25 disabled:opacity-60" disabled={busy} onClick={remove}>
            {busy ? "삭제 중…" : "삭제"}
          </button>
          <button type="button" className={smallButton} disabled={busy} onClick={() => setMode({ kind: "view" })}>
            취소
          </button>
        </div>
      ) : (
        mode.kind === "view" &&
        (isAuthor || canDelete) && (
          <div className="flex gap-1">
            {isAuthor && (
              <button type="button" className={smallButton} onClick={() => setMode({ kind: "edit", text: comment.content })}>
                수정
              </button>
            )}
            {canDelete && (
              <button type="button" className={smallButton} onClick={() => setMode({ kind: "confirm-delete" })}>
                삭제
              </button>
            )}
          </div>
        )
      )}
    </li>
  );
}
