import { api } from "./api";

export type Comment = {
  id: number;
  broadcastId: number;
  author: { id: number; nickname: string };
  content: string;
  createdAt: string;
  updatedAt: string | null;
};

export type CommentPage = {
  items: Comment[];
  hasNext: boolean;
  nextBefore: number | null;
};

/** 백엔드 CommentRequest 검증과 같은 한도 */
export const COMMENT_MAX = 500;

/** 목록 · 새 댓글 · 수정 · 삭제 모두 id로 합쳐서, SSE로 온 내 댓글이 POST 응답과 겹쳐도 한 번만 보인다. */
export type CommentsState = {
  byId: Map<number, Comment>;
  /** 더 오래된 댓글을 받을 때 넘기는 커서. hasOlder가 false면 null */
  nextBefore: number | null;
  hasOlder: boolean;
};

export const emptyComments: CommentsState = { byId: new Map(), nextBefore: null, hasOlder: false };

export type CommentsAction =
  /** 첫 페이지(처음 · 재연결). 이 페이지보다 새 항목(SSE로 먼저 온 것)만 남기고 나머지는 이 페이지로 교체한다. */
  | { type: "first-page"; page: CommentPage }
  | { type: "older-page"; page: CommentPage }
  | { type: "upsert"; comment: Comment }
  /** 수정 이벤트. 아직 안 받은 오래된 댓글이면 목록 중간에 끼어들지 않게 무시한다. */
  | { type: "edited"; comment: Comment }
  | { type: "remove"; id: number };

export function commentsReducer(state: CommentsState, action: CommentsAction): CommentsState {
  switch (action.type) {
    case "first-page": {
      const byId = new Map<number, Comment>();
      for (const c of action.page.items) byId.set(c.id, c);
      // 서버가 지운 댓글을 놓쳤을 수 있어, 스냅샷 범위 안의 기존 항목은 버린다.
      const newest = action.page.items[0]?.id ?? 0;
      for (const [id, c] of state.byId) if (id > newest) byId.set(id, c);
      return { byId, nextBefore: action.page.nextBefore, hasOlder: action.page.hasNext };
    }
    case "older-page": {
      const byId = new Map(state.byId);
      for (const c of action.page.items) byId.set(c.id, c);
      return { byId, nextBefore: action.page.nextBefore, hasOlder: action.page.hasNext };
    }
    case "upsert":
      return { ...state, byId: new Map(state.byId).set(action.comment.id, action.comment) };
    case "edited":
      return state.byId.has(action.comment.id)
        ? { ...state, byId: new Map(state.byId).set(action.comment.id, action.comment) }
        : state;
    case "remove": {
      if (!state.byId.has(action.id)) return state;
      const byId = new Map(state.byId);
      byId.delete(action.id);
      return { ...state, byId };
    }
  }
}

/** 오래된 것이 위, 새 것이 아래(채팅 순서) */
export function sortedComments(state: CommentsState): Comment[] {
  return [...state.byId.values()].sort((a, b) => a.id - b.id);
}

export function fetchComments(broadcastId: number, before?: number): Promise<CommentPage> {
  const query = before === undefined ? "" : `?before=${before}`;
  return api<CommentPage>(`/api/broadcasts/${broadcastId}/comments${query}`);
}

const timeOnly = new Intl.DateTimeFormat("ko-KR", { hour: "numeric", minute: "2-digit" });
const dateAndTime = new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });

/** 방송 시작과 같은 날이면 시각만, 다음 날 이후(다시보기)면 날짜도 붙인다. 렌더 중 현재 시각을 쓰지 않으려는 선택. */
export function formatCommentTime(iso: string, anchorIso: string): string {
  const at = new Date(iso);
  const anchor = new Date(anchorIso);
  return at.toDateString() === anchor.toDateString() ? timeOnly.format(at) : dateAndTime.format(at);
}
