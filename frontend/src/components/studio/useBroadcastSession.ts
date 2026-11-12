"use client";

import { DisconnectReason, Room, RoomEvent, TrackEvent, type LocalAudioTrack, type LocalVideoTrack } from "livekit-client";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Broadcast, LiveAccess } from "@/lib/broadcast";
import { captureCamera, captureMic, captureScreen, NO_CAPTURE, stopCapture, stopVideo, videoTracks, type Capture, type StudioMode, type VideoCapture } from "./media";

/**
 * setup  : 화면(또는 카메라) · 마이크를 고르는 단계. 방송이 READY면 "시작", LIVE면(새로고침 · 연결 끊김 뒤) "재개".
 * live   : 룸에 연결해 송출 중.
 */
export type StudioPhase =
  | { kind: "setup"; broadcast: Broadcast }
  | { kind: "connecting"; broadcast: Broadcast }
  | { kind: "live"; broadcast: Broadcast; access: LiveAccess; room: Room }
  | { kind: "ending"; broadcast: Broadcast; access: LiveAccess; room: Room }
  | { kind: "ended"; broadcast: Broadcast };

const MESSAGES: Record<string, string> = {
  LIVEKIT_NOT_CONFIGURED: "방송 서버가 아직 설정되지 않았습니다. 운영자에게 문의해 주세요.",
  LIVEKIT_UNAVAILABLE: "방송 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  NOT_BROADCAST_OWNER: "방송을 만든 사람만 송출할 수 있습니다.",
};

const describe = (e: unknown, fallback: string) => (e instanceof ApiError ? (MESSAGES[e.code] ?? e.message) : fallback);

const ended = (b: Broadcast): StudioPhase => ({ kind: "ended", broadcast: { ...b, status: "ENDED" } });

export function useBroadcastSession(initial: Broadcast, initialMode: StudioMode) {
  const [phase, setPhase] = useState<StudioPhase>(
    initial.status === "ENDED" ? { kind: "ended", broadcast: initial } : { kind: "setup", broadcast: initial },
  );
  const [capture, setCaptureState] = useState<Capture>(NO_CAPTURE);
  const [message, setMessage] = useState<string | null>(null);
  const [mode, setModeState] = useState<StudioMode>(initialMode);

  const captureRef = useRef<Capture>(NO_CAPTURE);
  const roomRef = useRef<Room | null>(null);
  // release()마다 올라간다. 이미 떠난 화면에서 끝난 비동기 작업이 상태를 건드리지 않게 하는 표식.
  const epochRef = useRef(0);
  const micAskedRef = useRef(false);
  const pickingRef = useRef(false);
  const flippingRef = useRef(false);
  const modeRef = useRef(initialMode);

  const setCapture = (next: Capture) => {
    captureRef.current = next;
    setCaptureState(next);
  };

  /** 룸 연결을 끊고 잡아 둔 화면/카메라 · 마이크를 모두 멈춘다. */
  const release = () => {
    epochRef.current++;
    const room = roomRef.current;
    roomRef.current = null;
    room?.disconnect();
    stopCapture(captureRef.current);
    setCapture(NO_CAPTURE);
  };

  // 화면이 숨겨지거나(Activity) 사라질 때 마지막 정리. 방송 종료 API는 부르지 않는다:
  // 서버가 방송자 연결이 끊긴 뒤 30초 유예 후 스스로 종료한다. 돌아오면 setup(재개)에서 이어 간다.
  useEffect(
    () => () => {
      if (!roomRef.current && !captureRef.current.video && !captureRef.current.mic) return;
      release();
      setPhase((p) => (p.kind === "live" || p.kind === "connecting" ? { kind: "setup", broadcast: p.broadcast } : p));
      setMessage("다른 화면으로 이동해 송출이 멈췄습니다. 화면 또는 카메라를 다시 켜고 방송을 재개해 주세요.");
    },
    // release는 ref와 setState만 쓰므로 한 번만 등록해도 최신 값을 본다
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const publish = (track: LocalVideoTrack | LocalAudioTrack) => roomRef.current?.localParticipant.publishTrack(track);

  /**
   * 방송자가 브라우저 공유 중지 막대를 누르거나 OS가 카메라를 가져가면(앱이 백그라운드로 가는 등):
   * 룸은 유지하고 영상만 비운 뒤 다시 켜게 한다.
   */
  const watchVideoEnd = (video: VideoCapture) => {
    video.track.on(TrackEvent.Ended, () => {
      const c = captureRef.current;
      if (c.video?.track !== video.track) return;
      // 방송 중 카메라가 끊기면 LiveKit이 같은 트랙으로 카메라를 다시 잡는다(실패하면 음소거). 직접 내리면 그 복구를 막는다
      if (video.kind === "camera" && roomRef.current) return;
      if (video.kind === "screen" && video.audio) {
        void roomRef.current?.localParticipant.unpublishTrack(video.audio, true);
        video.audio.stop();
      }
      setCapture({ ...c, video: undefined });
      setMessage(video.kind === "screen" ? "화면 공유가 중단되었습니다. 화면을 다시 선택해 주세요." : "카메라가 중단되었습니다. 카메라를 다시 켜 주세요.");
    });
  };

  /** setup에서만 호출한다. 고른 방식이 바뀌면 지금 잡은 영상은 쓸모없으므로 놓는다. */
  const selectMode = (next: StudioMode) => {
    if (next === modeRef.current) return;
    modeRef.current = next;
    setModeState(next);
    setMessage(null);
    const { video } = captureRef.current;
    if (!video) return;
    stopVideo(video);
    setCapture({ ...captureRef.current, video: undefined });
  };

  /** 화면 선택(screen) 또는 카메라 켜기(camera). 방송 중이면 바로 송출에 반영한다. */
  const pickVideo = async () => {
    if (pickingRef.current) return;
    pickingRef.current = true;
    const epoch = epochRef.current;
    const pickMode = modeRef.current;
    setMessage(null);
    try {
      const next = pickMode === "screen" ? await captureScreen() : await captureCamera("user");
      if (!next) {
        setMessage(pickMode === "screen" ? "화면 선택이 취소되었습니다." : "카메라를 사용할 수 없습니다. 브라우저의 카메라 권한을 확인해 주세요.");
        return;
      }
      let mic = captureRef.current.mic;
      if (!mic && !micAskedRef.current) {
        micAskedRef.current = true;
        mic = (await captureMic()) ?? undefined;
        if (!mic) setMessage(pickMode === "screen" ? "마이크를 사용할 수 없습니다. 화면 소리만으로 방송할 수 있습니다." : "마이크를 사용할 수 없습니다. 영상만으로 방송할 수 있습니다.");
      }
      if (epoch !== epochRef.current || pickMode !== modeRef.current) {
        stopVideo(next);
        mic?.stop();
        return;
      }
      const prev = captureRef.current;
      if (prev.video) {
        for (const old of videoTracks(prev.video)) void roomRef.current?.localParticipant.unpublishTrack(old, true);
        stopVideo(prev.video);
      }
      watchVideoEnd(next);
      setCapture({ ...prev, video: next, mic });
      if (roomRef.current) {
        for (const track of videoTracks(next)) await publish(track);
        if (mic && mic !== prev.mic) await publish(mic);
      }
    } catch (e) {
      setMessage(describe(e, pickMode === "screen" ? "화면을 가져오지 못했습니다. 브라우저의 화면 공유 권한을 확인해 주세요." : "카메라를 가져오지 못했습니다. 다른 앱이 카메라를 쓰고 있는지 확인해 주세요."));
    } finally {
      pickingRef.current = false;
    }
  };

  /** 전면/후면 전환. 같은 트랙을 다시 열어 시청자 쪽 게시가 끊기지 않는다. */
  const flipCamera = async () => {
    const video = captureRef.current.video;
    if (video?.kind !== "camera" || flippingRef.current) return;
    flippingRef.current = true;
    const facing = video.facing === "user" ? "environment" : "user";
    try {
      await video.track.restartTrack({ facingMode: facing });
      const c = captureRef.current;
      if (c.video?.track === video.track) setCapture({ ...c, video: { ...video, facing } });
    } catch {
      setMessage("카메라를 전환하지 못했습니다. 이 기기에 다른 카메라가 없을 수 있습니다.");
    } finally {
      flippingRef.current = false;
    }
  };

  /** 마이크 켜기/끄기. 끄면 장치까지 놓는다. 방송 중 음소거는 toggleMicMute. */
  const toggleMic = async () => {
    micAskedRef.current = true;
    const { mic } = captureRef.current;
    if (mic) {
      void roomRef.current?.localParticipant.unpublishTrack(mic, true);
      mic.stop();
      setCapture({ ...captureRef.current, mic: undefined, micMuted: false });
      return;
    }
    const next = await captureMic();
    if (!next) {
      setMessage("마이크를 사용할 수 없습니다. 브라우저의 마이크 권한을 확인해 주세요.");
      return;
    }
    setMessage(null);
    setCapture({ ...captureRef.current, mic: next, micMuted: false });
    try {
      await publish(next);
    } catch {
      setMessage("마이크를 송출하지 못했습니다.");
    }
  };

  const toggleMicMute = async () => {
    const { mic, micMuted } = captureRef.current;
    if (!mic) return;
    await (micMuted ? mic.unmute() : mic.mute());
    setCapture({ ...captureRef.current, micMuted: !micMuted });
  };

  const goLive = async () => {
    if (phase.kind !== "setup") return;
    const { broadcast } = phase;
    const epoch = epochRef.current;
    setMessage(null);
    setPhase({ kind: "connecting", broadcast });

    let access: LiveAccess;
    try {
      const endpoint = broadcast.status === "READY" ? "start" : "publisher-token";
      access = await api<LiveAccess>(`/api/broadcasts/${broadcast.id}/${endpoint}`, { method: "POST" });
    } catch (e) {
      if (epoch !== epochRef.current) return;
      if (e instanceof ApiError && e.code === "BROADCAST_ENDED") {
        release();
        setPhase(ended(broadcast));
        return;
      }
      // 다른 탭에서 이미 시작했거나 아직 시작 전인 경우: 실제 상태에 맞춰 버튼(시작/재개)을 바꾼다
      const status = e instanceof ApiError && e.code === "BROADCAST_ALREADY_LIVE" ? "LIVE" : e instanceof ApiError && e.code === "BROADCAST_NOT_LIVE" ? "READY" : broadcast.status;
      setPhase({ kind: "setup", broadcast: { ...broadcast, status } });
      setMessage(
        e instanceof ApiError && e.code === "BROADCAST_ALREADY_LIVE"
          ? "이미 방송 중입니다. 다른 탭에서 송출하고 있다면 그 탭을 닫고 '방송 재개'를 눌러 주세요."
          : describe(e, "방송을 시작하지 못했습니다."),
      );
      return;
    }

    const room = new Room({ dynacast: true });
    roomRef.current = room;
    room.on(RoomEvent.Disconnected, (reason) => {
      // release()가 먼저 roomRef를 비우므로, 여기 오는 건 우리가 끊지 않은 연결 종료뿐이다
      if (roomRef.current !== room) return;
      release();
      if (reason === DisconnectReason.ROOM_DELETED) {
        setPhase(ended(access.broadcast));
        return;
      }
      setPhase({ kind: "setup", broadcast: access.broadcast });
      setMessage("방송 서버와 연결이 끊겼습니다. 화면 또는 카메라를 다시 켜고 방송을 재개해 주세요.");
    });

    try {
      await room.connect(access.serverUrl, access.token);
      const { video, mic } = captureRef.current;
      for (const track of [...(video ? videoTracks(video) : []), ...(mic ? [mic] : [])]) {
        await room.localParticipant.publishTrack(track);
      }
    } catch (e) {
      if (epoch !== epochRef.current) return;
      roomRef.current = null;
      room.disconnect(false);
      // 서버는 이미 LIVE일 수 있으니 재개 버튼으로 다시 시도하게 한다
      setPhase({ kind: "setup", broadcast: access.broadcast });
      setMessage(describe(e, "방송 서버에 연결하지 못했습니다. 다시 시도해 주세요."));
      return;
    }
    if (epoch !== epochRef.current) return;
    setPhase({ kind: "live", broadcast: access.broadcast, access, room });
  };

  const end = async () => {
    if (phase.kind !== "live") return;
    const { broadcast, access, room } = phase;
    setPhase({ kind: "ending", broadcast, access, room });
    try {
      await api(`/api/broadcasts/${broadcast.id}/end`, { method: "POST" });
    } catch (e) {
      // 서버가 방을 먼저 닫아 Disconnected 처리가 끝난 경우엔 그 결과를 따른다
      if (!roomRef.current) return;
      setPhase({ kind: "live", broadcast, access, room });
      setMessage(describe(e, "방송을 종료하지 못했습니다. 다시 시도해 주세요."));
      return;
    }
    release();
    setPhase(ended(broadcast));
  };

  return { phase, capture, message, mode, selectMode, pickVideo, flipCamera, toggleMic, toggleMicMute, goLive, end };
}
