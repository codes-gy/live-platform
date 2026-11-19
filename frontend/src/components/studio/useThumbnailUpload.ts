"use client";

import type { LocalVideoTrack } from "livekit-client";
import { useEffect } from "react";

const WIDTH = 480;
const HEIGHT = 270;
const RETRY_MS = 3_000;
const INTERVAL_MS = 30_000;

/**
 * 방송 중에 송출 화면을 30초마다 찍어 목록 카드 썸네일로 올린다.
 * 영상 준비 전 · 네트워크 · 서버 오류면 3초 뒤 다시 찍는다. 요청 자체가 거절되면(종료 · 로그인 만료 등 4xx) 멈춘다.
 */
export function useThumbnailUpload(broadcastId: number, track: LocalVideoTrack | undefined, live: boolean) {
  useEffect(() => {
    if (!live || !track) return;
    const video = document.createElement("video");
    video.muted = true;
    video.playsInline = true;
    const canvas = document.createElement("canvas");
    canvas.width = WIDTH;
    canvas.height = HEIGHT;

    const upload = async (): Promise<"ok" | "retry" | "stop"> => {
      // 카메라 전환 등으로 안쪽 트랙이 바뀌었으면 다시 붙인다
      const current = track.mediaStreamTrack;
      if ((video.srcObject as MediaStream | null)?.getVideoTracks()[0] !== current) {
        video.srcObject = new MediaStream([current]);
        await video.play().catch(() => undefined);
      }
      const ctx = canvas.getContext("2d");
      if (!video.videoWidth || !ctx) return "retry";
      // 시청 플레이어처럼 잘라내지 않고 16:9 안에 맞춘다
      const scale = Math.min(WIDTH / video.videoWidth, HEIGHT / video.videoHeight);
      const w = video.videoWidth * scale;
      const h = video.videoHeight * scale;
      ctx.fillStyle = "#000";
      ctx.fillRect(0, 0, WIDTH, HEIGHT);
      ctx.drawImage(video, (WIDTH - w) / 2, (HEIGHT - h) / 2, w, h);
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.75));
      if (!blob) return "retry";
      const response = await fetch(`/api/broadcasts/${broadcastId}/thumbnail`, {
        method: "PUT",
        headers: { "Content-Type": "image/jpeg" },
        body: blob,
        credentials: "same-origin",
      }).catch(() => null);
      if (response?.ok) return "ok";
      return response && response.status < 500 ? "stop" : "retry";
    };

    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      const result = await upload();
      if (!stopped && result !== "stop") timer = setTimeout(tick, result === "ok" ? INTERVAL_MS : RETRY_MS);
    };
    timer = setTimeout(tick, RETRY_MS);
    return () => {
      stopped = true;
      clearTimeout(timer);
      video.srcObject = null;
    };
  }, [broadcastId, track, live]);
}
