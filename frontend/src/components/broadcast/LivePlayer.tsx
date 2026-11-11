"use client";

import {
  LiveKitRoom,
  RoomAudioRenderer,
  StartAudio,
  useConnectionState,
  useTracks,
  VideoTrack,
} from "@livekit/components-react";
import { ConnectionState, DisconnectReason, Track } from "livekit-client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { LiveAccess } from "@/lib/broadcast";

type Props = {
  broadcastId: number;
  /** 방송이 끝났거나(방 삭제) 시청할 수 없는 상태가 됐을 때 */
  onEnded: () => void;
};

type Access =
  | { kind: "loading" }
  | { kind: "ready"; access: LiveAccess }
  | { kind: "error"; message: string; retry: boolean };

/**
 * 시청자 화면. 서버에서 받기 전용 토큰을 받아 LiveKit 방에 들어가고,
 * 방송자의 화면(없으면 카메라)과 모든 소리(마이크 + 화면 소리)를 재생한다.
 */
export function LivePlayer({ broadcastId, onEnded }: Props) {
  const [access, setAccess] = useState<Access>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    api<LiveAccess>(`/api/broadcasts/${broadcastId}/viewer-token`, { method: "POST" })
      .then((result) => !cancelled && setAccess({ kind: "ready", access: result }))
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && (e.code === "BROADCAST_ENDED" || e.code === "BROADCAST_NOT_LIVE")) {
          onEnded();
          return;
        }
        setAccess({
          kind: "error",
          message: e instanceof ApiError ? e.message : "방송에 연결하지 못했습니다.",
          retry: !(e instanceof ApiError && e.code === "LIVEKIT_NOT_CONFIGURED"),
        });
      });
    return () => {
      cancelled = true;
    };
  }, [broadcastId, attempt, onEnded]);

  const retry = () => {
    setAccess({ kind: "loading" });
    setAttempt((n) => n + 1);
  };

  const onDisconnected = useCallback(
    (reason?: DisconnectReason) => {
      // 방송자가 종료하면 서버가 방을 닫는다 → 모든 시청자가 ROOM_DELETED로 끊긴다
      if (reason === DisconnectReason.ROOM_DELETED) {
        onEnded();
        return;
      }
      if (reason === DisconnectReason.CLIENT_INITIATED) return;
      setAccess({ kind: "error", message: "방송과 연결이 끊겼습니다.", retry: true });
    },
    [onEnded],
  );

  if (access.kind === "loading") {
    return <PlayerFrame><Overlay title="방송에 연결하는 중…" /></PlayerFrame>;
  }
  if (access.kind === "error") {
    return (
      <PlayerFrame>
        <Overlay title={access.message}>
          {access.retry && (
            <button type="button" onClick={retry} className="rounded-lg bg-white/10 px-4 py-2 text-sm font-semibold hover:bg-white/20">
              다시 연결
            </button>
          )}
        </Overlay>
      </PlayerFrame>
    );
  }

  return (
    <LiveKitRoom
      serverUrl={access.access.serverUrl}
      token={access.access.token}
      connect
      audio={false}
      video={false}
      options={{ adaptiveStream: true }}
      onDisconnected={onDisconnected}
      className="contents"
    >
      <Stage />
    </LiveKitRoom>
  );
}

/** 방 안에서 방송자 트랙을 골라 보여 준다. 화면 공유가 있으면 화면, 없으면 카메라. */
function Stage() {
  const frameRef = useRef<HTMLDivElement>(null);
  const connection = useConnectionState();
  const [muted, setMuted] = useState(false);
  const videos = useTracks([Track.Source.ScreenShare, Track.Source.Camera], { onlySubscribed: true });
  const main = videos.find((t) => t.source === Track.Source.ScreenShare) ?? videos[0];

  const toggleFullscreen = () => {
    const el = frameRef.current;
    if (!el) return;
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    } else {
      el.requestFullscreen?.().catch(() => {});
    }
  };

  return (
    <PlayerFrame ref={frameRef}>
      {main && <VideoTrack trackRef={main} className="h-full w-full object-contain" />}
      <RoomAudioRenderer muted={muted} />

      {connection !== ConnectionState.Connected && (
        <Overlay title={connection === ConnectionState.Reconnecting ? "다시 연결하는 중…" : "방송에 연결하는 중…"} />
      )}
      {connection === ConnectionState.Connected && !main && (
        <Overlay title="방송자 화면을 기다리고 있습니다" description="방송자의 연결이 잠시 끊겼을 수 있습니다. 돌아오면 바로 이어집니다." />
      )}

      <div className="absolute inset-x-0 bottom-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/70 to-transparent p-3">
        <span className="inline-flex items-center gap-1.5 rounded bg-live px-1.5 py-0.5 text-xs font-bold text-white">
          <span className="h-1.5 w-1.5 rounded-full bg-white motion-safe:animate-pulse" aria-hidden />
          LIVE
        </span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setMuted((m) => !m)}
            aria-pressed={muted}
            className="rounded-md bg-black/50 px-2.5 py-1 text-xs font-medium text-white hover:bg-black/70"
          >
            {muted ? "소리 켜기" : "음소거"}
          </button>
          <button type="button" onClick={toggleFullscreen} className="rounded-md bg-black/50 px-2.5 py-1 text-xs font-medium text-white hover:bg-black/70">
            전체 화면
          </button>
        </div>
      </div>

      {/* 브라우저가 소리 자동재생을 막으면 이 버튼만 보이고, 누르면 사라진다 */}
      <StartAudio
        label="눌러서 소리 켜기"
        className="absolute left-1/2 top-4 -translate-x-1/2 rounded-full bg-live px-4 py-2 text-sm font-semibold text-white shadow-lg hover:bg-live-strong"
      />
    </PlayerFrame>
  );
}

function PlayerFrame({ children, ref }: { children: React.ReactNode; ref?: React.Ref<HTMLDivElement> }) {
  return (
    <div ref={ref} className="relative aspect-video w-full overflow-hidden rounded-xl bg-black">
      {children}
    </div>
  );
}

function Overlay({ title, description, children }: { title: string; description?: string; children?: React.ReactNode }) {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center text-white" role="status">
      <p className="font-semibold">{title}</p>
      {description && <p className="max-w-sm text-sm text-white/70">{description}</p>}
      {children}
    </div>
  );
}
