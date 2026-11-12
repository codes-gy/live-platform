"use client";

import Link from "next/link";
import type { LocalAudioTrack, LocalVideoTrack } from "livekit-client";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { loadBroadcast, type Broadcast, type BroadcastLoad } from "@/lib/broadcast";
import { useAuth } from "../auth/AuthProvider";
import { StatusBadge } from "../broadcast/StatusBadge";
import { useViewerCount, ViewerCount } from "../broadcast/useViewerCount";
import { CommentPanel } from "../comment/CommentPanel";
import { Card, ErrorMessage, Notice, PrimaryButton } from "../ui";
import { canCaptureCamera, canCaptureScreen, type Capture, type StudioMode } from "./media";
import { useBroadcastSession } from "./useBroadcastSession";
import { useWakeLock } from "./useWakeLock";

type State = { kind: "loading" } | BroadcastLoad;

const secondaryButton = "h-11 rounded-lg border border-line px-4 text-sm font-medium hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-60";
const linkButton = "rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line";

const noopSubscribe = () => () => {};
// 방송 종료는 스튜디오가 룸 연결로 먼저 알게 되므로 댓글 패널의 종료 알림은 쓰지 않는다
const ignoreEnded = () => {};

/** 안내 화면은 좁게 가운데에 둔다(스튜디오 본문만 댓글과 함께 넓게 쓴다). */
function Narrow({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto w-full max-w-3xl">{children}</div>;
}

export function Studio({ id }: { id: number }) {
  const { user } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  // 서버 렌더에서는 알 수 없으므로 true로 시작하고, 브라우저에서 실제 값으로 바뀐다
  const canScreen = useSyncExternalStore(noopSubscribe, canCaptureScreen, () => true);
  const canCamera = useSyncExternalStore(noopSubscribe, canCaptureCamera, () => true);

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
  const notice = studioNotice(state, user?.id, canScreen, canCamera);
  if (notice) return <Narrow>{notice}</Narrow>;
  if (state.kind !== "ok") return null;
  return <Session key={state.broadcast.id} broadcast={state.broadcast} canScreen={canScreen} canCamera={canCamera} />;
}

/** 송출 화면 대신 보여 줄 안내. 송출할 수 있으면 null. */
function studioNotice(state: BroadcastLoad, userId: number | undefined, canScreen: boolean, canCamera: boolean) {
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
  if (state.broadcast.owner.id !== userId) {
    return (
      <Notice title="송출할 권한이 없습니다" description="방송을 만든 사람만 송출할 수 있습니다.">
        <Link href={`/broadcasts/${state.broadcast.id}`} className={linkButton}>
          방송 보러 가기
        </Link>
      </Notice>
    );
  }
  if (!canScreen && !canCamera && state.broadcast.status !== "ENDED") {
    return (
      <Notice
        title="이 브라우저에서는 방송을 할 수 없습니다"
        description="카메라와 화면 송출은 보안 연결(HTTPS) 주소에서만 동작합니다. 주소가 https://로 시작하는지 확인하고, 휴대폰은 iOS Safari 또는 Android Chrome, PC는 Chrome 또는 Edge에서 접속해 주세요.">
        <Link href={`/broadcasts/${state.broadcast.id}`} className={linkButton}>
          방송 상세로
        </Link>
      </Notice>
    );
  }
  return null;
}

function Session({ broadcast, canScreen, canCamera }: { broadcast: Broadcast; canScreen: boolean; canCamera: boolean }) {
  const { phase, capture, message, mode, selectMode, pickVideo, flipCamera, toggleMic, toggleMicMute, goLive, end } = useBroadcastSession(
    broadcast,
    canScreen ? "screen" : "camera",
  );

  // 방송 중 탭을 닫거나 새로고침하면 송출이 끊긴다
  const guarded = phase.kind === "connecting" || phase.kind === "live" || phase.kind === "ending";
  useEffect(() => {
    if (!guarded) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [guarded]);
  useWakeLock(guarded);

  const viewers = useViewerCount(phase.kind === "live" || phase.kind === "ending" ? phase.room : null);

  if (phase.kind === "ended") {
    return (
      <Narrow>
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
      </Narrow>
    );
  }

  const live = phase.kind === "live" || phase.kind === "ending";
  const busy = phase.kind === "connecting" || phase.kind === "ending";
  const video = capture.video;
  const camera = mode === "camera";

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h1 className="min-w-0 truncate text-2xl font-bold">{phase.broadcast.title}</h1>
          <StatusBadge status={phase.broadcast.status} />
          {live && <Elapsed since={phase.broadcast.startedAt} />}
          {live && <ViewerCount count={viewers} className="text-sm text-muted" />}
        </div>

        {phase.kind === "setup" && canScreen && canCamera && <ModeToggle mode={mode} onSelect={selectMode} />}

        <div className={`relative w-full overflow-hidden rounded-xl bg-black ${camera ? "aspect-[3/4] sm:aspect-video" : "aspect-video"}`}>
          {video ? (
            <Preview track={video.track} mirror={video.kind === "camera" && video.facing === "user"} />
          ) : (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center text-white">
              <p className="font-semibold">
                {camera ? (live ? "켜져 있는 카메라가 없습니다" : "카메라를 켜 주세요") : live ? "공유 중인 화면이 없습니다" : "방송할 화면을 선택하세요"}
              </p>
              <button type="button" onClick={pickVideo} className="rounded-lg bg-white/10 px-4 py-2 text-sm font-semibold hover:bg-white/20">
                {camera ? "카메라 켜기" : "화면 선택"}
              </button>
            </div>
          )}
        </div>

        <ErrorMessage message={message} />
        {video?.kind === "screen" && !video.audio && <NoAudioHint />}

        <Card className="!p-5">
          <div className="flex flex-wrap items-center gap-2">
            {camera ? (
              video ? (
                // 카메라가 여러 개인 건 휴대폰이라 PC 웹캠에는 보이지 않는다
                !canScreen && (
                  <button type="button" onClick={flipCamera} disabled={busy} className={secondaryButton}>
                    전면/후면 전환
                  </button>
                )
              ) : (
                <button type="button" onClick={pickVideo} disabled={busy} className={secondaryButton}>
                  카메라 켜기
                </button>
              )
            ) : (
              <button type="button" onClick={pickVideo} disabled={busy} className={secondaryButton}>
                {video ? "화면 바꾸기" : "화면 선택"}
              </button>
            )}
            <MicControl capture={capture} live={live} onToggleCapture={toggleMic} onToggleMute={toggleMicMute} />
            <div className="ml-auto">
              {live ? (
                <EndButton pending={phase.kind === "ending"} onEnd={end} />
              ) : (
                <PrimaryButton onClick={goLive} disabled={!video || phase.kind === "connecting"}>
                  {phase.kind === "connecting" ? "연결하는 중…" : phase.broadcast.status === "LIVE" ? "방송 재개" : "방송 시작"}
                </PrimaryButton>
              )}
            </div>
          </div>
          <p className="mt-3 text-xs text-muted">
            {!live && !video
              ? `${camera ? "카메라를 켜면" : "방송할 화면을 선택하면"} 방송을 ${phase.broadcast.status === "LIVE" ? "재개" : "시작"}할 수 있습니다.`
              : phase.broadcast.status === "LIVE" && !live
              ? `방송은 아직 진행 중입니다. ${camera ? "카메라를 켜고" : "화면을 선택하고"} 재개하면 시청자에게 이어서 송출됩니다.`
              : "방송 중 이 탭을 닫거나 다른 화면으로 이동하면 송출이 멈추고, 30초 안에 돌아오지 않으면 방송이 자동으로 종료됩니다."}
          </p>
        </Card>
      </div>
      <CommentPanel broadcast={phase.broadcast} onEnded={ignoreEnded} />
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
  return (
    <div className="flex items-center gap-2">
      {live ? (
        <button type="button" onClick={onToggleMute} aria-pressed={capture.micMuted} className={secondaryButton}>
          {capture.micMuted ? "마이크 음소거 해제" : "마이크 음소거"}
        </button>
      ) : (
        <button type="button" onClick={onToggleCapture} className={secondaryButton}>
          마이크 끄기
        </button>
      )}
      <MicLevel track={capture.mic} muted={capture.micMuted} />
    </div>
  );
}

/**
 * 마이크 음량 막대. 말할 때 움직이면 소리가 들어가고 있다는 뜻이다.
 * 매 프레임 setState를 하지 않도록 막대 요소의 transform만 직접 바꾼다.
 */
function MicLevel({ track, muted }: { track: LocalAudioTrack; muted: boolean }) {
  const barRef = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const ctx = new AudioContext();
    void ctx.resume();
    const source = ctx.createMediaStreamSource(new MediaStream([track.mediaStreamTrack]));
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 512;
    source.connect(analyser);
    const samples = new Uint8Array(analyser.fftSize);
    let frame = 0;
    const draw = () => {
      analyser.getByteTimeDomainData(samples);
      let peak = 0;
      for (const v of samples) peak = Math.max(peak, Math.abs(v - 128));
      if (barRef.current) barRef.current.style.transform = `scaleX(${Math.min(1, peak / 48)})`;
      frame = requestAnimationFrame(draw);
    };
    draw();
    return () => {
      cancelAnimationFrame(frame);
      source.disconnect();
      void ctx.close();
    };
  }, [track]);
  return (
    <span className="flex items-center gap-1.5 text-xs text-muted" role="img" aria-label={muted ? "마이크 음소거됨" : "마이크 음량"}>
      <span className="relative h-1.5 w-16 overflow-hidden rounded-full bg-line">
        <span
          ref={barRef}
          className={`absolute inset-0 origin-left rounded-full transition-transform duration-75 ${muted ? "bg-muted/40" : "bg-ok"}`}
          style={{ transform: "scaleX(0)" }}
        />
      </span>
      {muted && "음소거"}
    </span>
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
    <div className="flex flex-wrap items-center justify-end gap-2">
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

function ModeToggle({ mode, onSelect }: { mode: StudioMode; onSelect: (mode: StudioMode) => void }) {
  const options: { value: StudioMode; label: string }[] = [
    { value: "screen", label: "화면 공유" },
    { value: "camera", label: "카메라" },
  ];
  return (
    <div role="group" aria-label="송출 방식" className="inline-flex self-start rounded-lg border border-line p-0.5">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => onSelect(o.value)}
          aria-pressed={mode === o.value}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${mode === o.value ? "bg-surface-2" : "text-muted hover:bg-surface-2"}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

function Preview({ track, mirror }: { track: LocalVideoTrack; mirror: boolean }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    track.attach(el);
    return () => {
      track.detach(el);
    };
  }, [track]);
  // 거울 반전은 미리보기에만 건다. 송출 트랙은 그대로라 시청자에게는 글자가 바로 보인다
  return <video ref={ref} muted playsInline autoPlay style={mirror ? { transform: "scaleX(-1)" } : undefined} className="h-full w-full object-contain" />;
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
