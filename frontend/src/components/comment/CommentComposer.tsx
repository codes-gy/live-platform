"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { api, ApiError, json } from "@/lib/api";
import type { Broadcast } from "@/lib/broadcast";
import { COMMENT_MAX, type Comment } from "@/lib/comments";
import { loginPath } from "@/lib/navigation";
import { useAuth } from "../auth/AuthProvider";
import { ErrorMessage } from "../ui";

type Props = {
  broadcast: Broadcast;
  onSent: (comment: Comment) => void;
  /** 서버가 방송이 끝났다고 답했을 때 상세 화면을 다시 불러오게 한다. */
  onEnded: () => void;
};

const noteClass = "rounded-lg bg-surface-2 px-3 py-2.5 text-center text-sm text-muted";

export function CommentComposer({ broadcast, onSent, onEnded }: Props) {
  const { user, status } = useAuth();
  const pathname = usePathname();
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (broadcast.status === "ENDED") return <p className={noteClass}>종료된 방송의 댓글입니다.</p>;
  if (broadcast.status === "READY") return <p className={noteClass}>방송이 시작되면 댓글을 쓸 수 있습니다.</p>;
  if (status === "loading") return <div className="h-11 animate-pulse rounded-lg bg-surface-2" />;
  if (!user) {
    return (
      <Link href={loginPath(pathname)} className={`${noteClass} hover:bg-line hover:text-fg`}>
        로그인하고 댓글을 남겨 보세요
      </Link>
    );
  }

  const content = text.trim();
  const canSend = content.length > 0 && text.length <= COMMENT_MAX && !sending;

  async function send() {
    if (!canSend) return;
    setSending(true);
    setError(null);
    try {
      const created = await api<Comment>(`/api/broadcasts/${broadcast.id}/comments`, { method: "POST", body: json({ content }) });
      setText("");
      onSent(created);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "댓글을 등록하지 못했습니다.");
      if (e instanceof ApiError && (e.code === "BROADCAST_ENDED" || e.code === "BROADCAST_NOT_LIVE")) onEnded();
    } finally {
      setSending(false);
    }
  }

  return (
    <form
      className="flex flex-col gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        void send();
      }}
    >
      <ErrorMessage message={error} />
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          // 한글 조합 중 Enter는 글자 확정이라 전송하지 않는다.
          if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            void send();
          }
        }}
        disabled={sending}
        rows={2}
        maxLength={COMMENT_MAX}
        placeholder={`${user.nickname}(으)로 댓글 남기기`}
        aria-label="댓글 입력"
        className="resize-none rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm outline-none placeholder:text-muted/70 focus:border-live disabled:opacity-60"
      />
      <div className="flex items-center justify-between">
        <span className="text-xs text-muted">
          {text.length}/{COMMENT_MAX}
        </span>
        <button
          type="submit"
          disabled={!canSend}
          className="h-9 rounded-lg bg-live px-4 text-sm font-semibold text-white transition-colors hover:bg-live-strong disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-muted"
        >
          {sending ? "등록 중…" : "등록"}
        </button>
      </div>
    </form>
  );
}
