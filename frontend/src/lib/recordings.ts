import { api, ApiError } from "./api";
import type { Broadcast, Page } from "./broadcast";

export type RecordingStatus = "RECORDING" | "READY" | "FAILED";

export type Recording = {
  id: number;
  status: RecordingStatus;
  broadcast: Broadcast;
  /** 녹화 중이거나 실패면 null */
  durationMs: number | null;
  sizeBytes: number | null;
  createdAt: string;
  endedAt: string | null;
  /** 상세에서 재생할 수 있을 때만 들어오는 서명된 주소(몇 시간 뒤 만료) */
  playbackUrl: string | null;
};

export const RECORDING_PAGE_SIZE = 12;

export type RecordingPageLoad =
  | { kind: "ok"; items: Recording[]; hasNext: boolean; page: number; total: number }
  | { kind: "error"; message: string };

export async function fetchRecordings(page: number): Promise<RecordingPageLoad> {
  try {
    const result = await api<Page<Recording>>(`/api/recordings?page=${page}&size=${RECORDING_PAGE_SIZE}`);
    return { kind: "ok", items: result.items, hasNext: result.hasNext, page, total: result.totalElements };
  } catch (e) {
    return { kind: "error", message: e instanceof ApiError ? e.message : "다시보기 목록을 불러오지 못했습니다." };
  }
}

export type RecordingLoad = { kind: "ok"; recording: Recording } | { kind: "not-found" } | { kind: "error"; message: string };

export async function fetchRecording(id: number): Promise<RecordingLoad> {
  try {
    return { kind: "ok", recording: await api<Recording>(`/api/recordings/${id}`) };
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.code === "INVALID_INPUT")) return { kind: "not-found" };
    return { kind: "error", message: e instanceof ApiError ? e.message : "녹화를 불러오지 못했습니다." };
  }
}

export async function fetchBroadcastRecordings(broadcastId: number): Promise<Recording[]> {
  try {
    return await api<Recording[]>(`/api/broadcasts/${broadcastId}/recordings`);
  } catch {
    return [];
  }
}

/** 65000 → "1:05", 3725000 → "1:02:05" */
export function formatPlayTime(ms: number | null): string {
  if (ms == null) return "";
  const total = Math.round(ms / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = String(total % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${s}` : `${m}:${s}`;
}

export function formatSize(bytes: number | null): string {
  if (bytes == null) return "";
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}
