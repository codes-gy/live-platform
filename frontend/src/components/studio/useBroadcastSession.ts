"use client";

import { DisconnectReason, Room, RoomEvent, TrackEvent, type LocalAudioTrack, type LocalVideoTrack } from "livekit-client";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Broadcast, LiveAccess } from "@/lib/broadcast";
import { captureMic, captureScreen, NO_CAPTURE, stopCapture, type Capture } from "./media";

/**
 * setup  : 화면 · 마이크를 고르는 단계. 방송이 READY면 "시작", LIVE면(새로고침 · 연결 끊김 뒤) "재개".
 * live   : 룸에 연결해 송출 중.
 */
export type StudioPhase =
  | { kind: "setup"; broadcast: Broadcast }
  | { kind: "connecting"; broadcast: Broadcast }
  | { kind: "live"; broadcast: Broadcast; access: LiveAccess }
  | { kind: "ending"; broadcast: Broadcast; access: LiveAccess }
  | { kind: "ended"; broadcast: Broadcast };

const MESSAGES: Record<string, string> = {
  LIVEKIT_NOT_CONFIGURED: "방송 서버가 아직 설정되지 않았습니다. 운영자에게 문의해 주세요.",
  LIVEKIT_UNAVAILABLE: "방송 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  NOT_BROADCAST_OWNER: "방송을 만든 사람만 송출할 수 있습니다.",
};

const describe = (e: unknown, fallback: string) => (e instanceof ApiError ? (MESSAGES[e.code] ?? e.message) : fallback);

const ended = (b: Broadcast): StudioPhase => ({ kind: "ended", broadcast: { ...b, status: "ENDED" } });

export function useBroadcastSession(initial: Broadcast) {
  const [phase, setPhase] = useState<StudioPhase>(
    initial.status === "ENDED" ? { kind: "ended", broadcast: initial } : { kind: "setup", broadcast: initial },
  );
  const [capture, setCaptureState] = useState<Capture>(NO_CAPTURE);
  const [message, setMessage] = useState<string | null>(null);

  const captureRef = useRef<Capture>(NO_CAPTURE);
  const roomRef = useRef<Room | null>(null);
  // release()마다 올라간다. 이미 떠난 화면에서 끝난 비동기 작업이 상태를 건드리지 않게 하는 표식.
  const epochRef = useRef(0);
  const micAskedRef = useRef(false);
  const pickingRef = useRef(false);

  const setCapture = (next: Capture) => {
    captureRef.current = next;
    setCaptureState(next);
  };

  /** 룸 연결을 끊고 잡아 둔 화면 · 마이크를 모두 멈춘다. */
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
      if (!roomRef.current && !captureRef.current.screen && !captureRef.current.mic) return;
      release();
      setPhase((p) => (p.kind === "live" || p.kind === "connecting" ? { kind: "setup", broadcast: p.broadcast } : p));
      setMessage("다른 화면으로 이동해 송출이 멈췄습니다. 화면을 다시 선택해 방송을 재개해 주세요.");
    },
    // release는 ref와 setState만 쓰므로 한 번만 등록해도 최신 값을 본다
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const publish = (track: LocalVideoTrack | LocalAudioTrack) => roomRef.current?.localParticipant.publishTrack(track);

  /** 방송자가 브라우저 공유 중지 막대를 누르면: 룸은 유지하고 화면만 비운 뒤 다시 고르게 한다. */
  const watchScreenEnd = (video: LocalVideoTrack) => {
    video.on(TrackEvent.Ended, () => {
      const c = captureRef.current;
      if (c.screen !== video) return;
      if (c.screenAudio) {
        void roomRef.current?.localParticipant.unpublishTrack(c.screenAudio, true);
        c.screenAudio.stop();
      }
      setCapture({ ...c, screen: undefined, screenAudio: undefined });
      setMessage("화면 공유가 중단되었습니다. 화면을 다시 선택해 주세요.");
    });
  };

  const pickScreen = async () => {
    if (pickingRef.current) return;
    pickingRef.current = true;
    const epoch = epochRef.current;
    setMessage(null);
    try {
      const shot = await captureScreen();
      if (!shot) {
        setMessage("화면 선택이 취소되었습니다.");
        return;
      }
      let mic = captureRef.current.mic;
      if (!mic && !micAskedRef.current) {
        micAskedRef.current = true;
        mic = (await captureMic()) ?? undefined;
        if (!mic) setMessage("마이크를 사용할 수 없습니다. 화면 소리만으로 방송할 수 있습니다.");
      }
      if (epoch !== epochRef.current) {
        shot.video.stop();
        shot.audio?.stop();
        mic?.stop();
        return;
      }
      const prev = captureRef.current;
      for (const old of [prev.screen, prev.screenAudio]) {
        if (!old) continue;
        void roomRef.current?.localParticipant.unpublishTrack(old, true);
        old.stop();
      }
      watchScreenEnd(shot.video);
      setCapture({ ...prev, screen: shot.video, screenAudio: shot.audio, mic });
      if (roomRef.current) {
        await publish(shot.video);
        if (shot.audio) await publish(shot.audio);
        if (mic && mic !== prev.mic) await publish(mic);
      }
    } catch (e) {
      setMessage(describe(e, "화면을 가져오지 못했습니다. 브라우저의 화면 공유 권한을 확인해 주세요."));
    } finally {
      pickingRef.current = false;
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
      setMessage("방송 서버와 연결이 끊겼습니다. 화면을 다시 선택하고 방송을 재개해 주세요.");
    });

    try {
      await room.connect(access.serverUrl, access.token);
      for (const track of [captureRef.current.screen, captureRef.current.screenAudio, captureRef.current.mic]) {
        if (track) await room.localParticipant.publishTrack(track);
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
    setPhase({ kind: "live", broadcast: access.broadcast, access });
  };

  const end = async () => {
    if (phase.kind !== "live") return;
    const { broadcast, access } = phase;
    setPhase({ kind: "ending", broadcast, access });
    try {
      await api(`/api/broadcasts/${broadcast.id}/end`, { method: "POST" });
    } catch (e) {
      // 서버가 방을 먼저 닫아 Disconnected 처리가 끝난 경우엔 그 결과를 따른다
      if (!roomRef.current) return;
      setPhase({ kind: "live", broadcast, access });
      setMessage(describe(e, "방송을 종료하지 못했습니다. 다시 시도해 주세요."));
      return;
    }
    release();
    setPhase(ended(broadcast));
  };

  return { phase, capture, message, pickScreen, toggleMic, toggleMicMute, goLive, end };
}
