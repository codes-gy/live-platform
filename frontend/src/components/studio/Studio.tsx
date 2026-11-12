"use client";

import Link from "next/link";
import type { LocalVideoTrack } from "livekit-client";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { loadBroadcast, type Broadcast, type BroadcastLoad } from "@/lib/broadcast";
import { useAuth } from "../auth/AuthProvider";
import { StatusBadge } from "../broadcast/StatusBadge";
import { Card, ErrorMessage, Notice, PrimaryButton } from "../ui";
import { canCaptureScreen, type Capture } from "./media";
import { useBroadcastSession } from "./useBroadcastSession";

type State = { kind: "loading" } | BroadcastLoad;

const secondaryButton = "h-11 rounded-lg border border-line px-4 text-sm font-medium hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-60";
const linkButton = "rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line";

const noopSubscribe = () => () => {};

export function Studio({ id }: { id: number }) {
  const { user } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  // 서버 렌더에서는 알 수 없으므로 true로 시작하고, 브라우저에서 실제 값으로 바뀐다
  const supported = useSyncExternalStore(noopSubscribe, canCaptureScreen, () => true);

  useEffect(() => {
    let active = true;
    loadBroadcast(id).then((result) => active && setState(result));
    return () => {
      active = false;
    };
  }, [id]);

  if (state.kind === "loading") {
    return <div className="aspect-video animate-pulse rounded-2xl bg-surface-2" aria-busy />;
  }
  if (state.kind === "not-found") {
    return (
      <Notice title="방송을 찾을 수 없습니다" description="삭제되었거나 잘못된 주소입니다.">
        <Link href="/my" className={linkButton}>
          내 방송으로
        </Link>
      </Notice>
    );
  }
  if (state.kind === "error") {
    return <Notice title={state.message} description="잠시 후 다시 시도해 주세요." />;
  }
  if (state.broadcast.owner.id !== user?.id) {
    return (
      <Notice title="송출할 권한이 없습니다" description="방송을 만든 사람만 송출할 수 있습니다.">
        <Link href={`/broadcasts/${state.broadcast.id}`} className={linkButton}>
          방송 보러 가기
        </Link>
      </Notice>
    );
  }
  if (!supported && state.broadcast.status !== "ENDED") {
    return (
      <Notice title="이 브라우저에서는 화면 송출을 할 수 없습니다" description="PC의 Chrome 또는 Edge에서 접속해 주세요. 휴대폰 카메라 송출은 곧 지원됩니다.">
        <Link href={`/broadcasts/${state.broadcast.id}`} className={linkButton}>
          방송 상세로
        </Link>
      </Notice>
    );
  }
  return <Session key={state.broadcast.id} broadcast={state.broadcast} />;
}

function Session({ broadcast }: { broadcast: Broadcast }) {
  const { phase, capture, message, pickScreen, toggleMic, toggleMicMute, goLive, end } = useBroadcastSession(broadcast);

  // 방송 중 탭을 닫거나 새로고침하면 송출이 끊긴다
  const guarded = phase.kind === "connecting" || phase.kind === "live" || phase.kind === "ending";
  useEffect(() => {
    if (!guarded) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [guarded]);

  if (phase.kind === "ended") {
    return (
      <Notice title="방송이 종료되었습니다" description="수고하셨습니다. 시청자는 방송 상세에서 종료된 방송으로 보게 됩니다.">
        <div className="flex gap-2">
          <Link href={`/broadcasts/${phase.broadcast.id}`} className={linkButton}>
            방송 상세
          </Link>
          <Link href="/my" className={linkButton}>
            내 방송
          </Link>
        </div>
      </Notice>
    );
  }

  const live = phase.kind === "live" || phase.kind === "ending";

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="min-w-0 truncate text-2xl font-bold">{phase.broadcast.title}</h1>
        <StatusBadge status={phase.broadcast.status} />
        {live && <Elapsed since={phase.broadcast.startedAt} />}
      </div>

      <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-black">
        {capture.screen ? (
          <Preview track={capture.screen} />
        ) : (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center text-white">
            <p className="font-semibold">{live ? "공유 중인 화면이 없습니다" : "방송할 화면을 선택하세요"}</p>
            <button type="button" onClick={pickScreen} className="rounded-lg bg-white/10 px-4 py-2 text-sm font-semibold hover:bg-white/20">
              화면 선택
            </button>
          </div>
        )}
      </div>

      <ErrorMessage message={message} />
      {capture.screen && !capture.screenAudio && <NoAudioHint />}

      <Card className="!p-5">
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={pickScreen} disabled={phase.kind === "connecting" || phase.kind === "ending"} className={secondaryButton}>
            {capture.screen ? "화면 바꾸기" : "화면 선택"}
          </button>
          <MicControl capture={capture} live={live} onToggleCapture={toggleMic} onToggleMute={toggleMicMute} />
          <div className="ml-auto">
            {live ? (
              <EndButton pending={phase.kind === "ending"} onEnd={end} />
            ) : (
              <PrimaryButton onClick={goLive} disabled={!capture.screen || phase.kind === "connecting"}>
                {phase.kind === "connecting" ? "연결하는 중…" : phase.broadcast.status === "LIVE" ? "방송 재개" : "방송 시작"}
              </PrimaryButton>
            )}
          </div>
        </div>
        <p className="mt-3 text-xs text-muted">
          {phase.broadcast.status === "LIVE" && !live
            ? "방송은 아직 진행 중입니다. 화면을 선택하고 재개하면 시청자에게 이어서 송출됩니다."
            : "방송 중 이 탭을 닫거나 다른 화면으로 이동하면 송출이 멈추고, 30초 안에 돌아오지 않으면 방송이 자동으로 종료됩니다."}
        </p>
      </Card>
    </div>
  );
}

function NoAudioHint() {
  return (
    <p role="status" className="rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-muted">
      화면 소리가 공유되지 않았습니다. 시청자는 마이크 소리만 듣게 됩니다. 소리를 함께 보내려면 &lsquo;화면 바꾸기&rsquo;를 눌러 선택 창 아래의
      &lsquo;탭 오디오 공유&rsquo;(탭) 또는 &lsquo;시스템 오디오 공유&rsquo;(전체 화면, Windows)를 체크하세요. Chrome · Edge에서만 지원됩니다.
    </p>
  );
}

function MicControl({
  capture,
  live,
  onToggleCapture,
  onToggleMute,
}: {
  capture: Capture;
  live: boolean;
  onToggleCapture: () => void;
  onToggleMute: () => void;
}) {
  if (!capture.mic) {
    return (
      <button type="button" onClick={onToggleCapture} className={secondaryButton}>
        마이크 켜기
      </button>
    );
  }
  // 방송 전에는 마이크를 아예 놓을 수 있고, 방송 중에는 음소거만 한다
  if (live) {
    return (
      <button type="button" onClick={onToggleMute} aria-pressed={capture.micMuted} className={secondaryButton}>
        {capture.micMuted ? "마이크 음소거 해제" : "마이크 음소거"}
      </button>
    );
  }
  return (
    <button type="button" onClick={onToggleCapture} className={secondaryButton}>
      마이크 끄기
    </button>
  );
}

function EndButton({ pending, onEnd }: { pending: boolean; onEnd: () => void }) {
  const [confirming, setConfirming] = useState(false);
  if (!confirming) {
    return (
      <button type="button" onClick={() => setConfirming(true)} className="h-11 rounded-lg border border-danger/50 px-4 text-sm font-semibold text-danger hover:bg-danger/10">
        방송 종료
      </button>
    );
  }
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm">정말 종료할까요?</span>
      <button type="button" onClick={() => setConfirming(false)} disabled={pending} className={secondaryButton}>
        취소
      </button>
      <PrimaryButton onClick={onEnd} disabled={pending}>
        {pending ? "종료하는 중…" : "종료"}
      </PrimaryButton>
    </div>
  );
}

function Preview({ track }: { track: LocalVideoTrack }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    track.attach(el);
    return () => {
      track.detach(el);
    };
  }, [track]);
  return <video ref={ref} muted playsInline autoPlay className="h-full w-full object-contain" />;
}

const pad = (n: number) => String(n).padStart(2, "0");

/** 렌더 중 Date.now()를 못 쓰므로 1초 타이머가 현재 시각을 state로 넣어 준다. */
function Elapsed({ since }: { since: string | null }) {
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const tick = () => setNow(Date.now());
    const first = setTimeout(tick, 0);
    const timer = setInterval(tick, 1000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, []);
  const seconds = since && now ? Math.max(0, Math.floor((now - new Date(since).getTime()) / 1000)) : 0;
  return (
    <span className="font-mono text-sm tabular-nums text-muted" aria-label="방송 시간">
      {pad(Math.floor(seconds / 3600))}:{pad(Math.floor(seconds / 60) % 60)}:{pad(seconds % 60)}
    </span>
  );
}
