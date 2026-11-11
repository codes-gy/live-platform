import {
  createLocalAudioTrack,
  createLocalScreenTracks,
  LocalAudioTrack,
  LocalVideoTrack,
  ScreenSharePresets,
} from "livekit-client";

export type Capture = {
  screen?: LocalVideoTrack;
  screenAudio?: LocalAudioTrack;
  mic?: LocalAudioTrack;
  micMuted: boolean;
};

export const NO_CAPTURE: Capture = { micMuted: false };

/** 화면 캡처 API가 없는 브라우저(휴대폰 등)는 PC 송출을 쓸 수 없다. */
export function canCaptureScreen(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.mediaDevices?.getDisplayMedia === "function";
}

/**
 * 사용자가 화면 선택 창에서 취소하면 null. 그 밖의 실패는 던진다.
 * 탭/시스템 소리는 사용자가 선택 창에서 체크해야 들어오므로 audio가 없을 수 있다.
 */
export async function captureScreen(): Promise<{ video: LocalVideoTrack; audio?: LocalAudioTrack } | null> {
  try {
    const tracks = await createLocalScreenTracks({
      audio: true,
      systemAudio: "include",
      // 이 스튜디오 탭을 고르면 미리보기가 무한 반복되므로 선택지에서 뺀다
      selfBrowserSurface: "exclude",
      surfaceSwitching: "include",
      resolution: ScreenSharePresets.h1080fps30.resolution,
    });
    const video = tracks.find((t) => t instanceof LocalVideoTrack);
    if (!video) {
      tracks.forEach((t) => t.stop());
      throw new Error("no video track");
    }
    return { video, audio: tracks.find((t) => t instanceof LocalAudioTrack) };
  } catch (e) {
    if (e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "AbortError")) return null;
    throw e;
  }
}

/** 마이크는 선택 사항이라 권한 거부 · 장치 없음은 모두 null. */
export async function captureMic(): Promise<LocalAudioTrack | null> {
  try {
    return await createLocalAudioTrack({ echoCancellation: true, noiseSuppression: true, autoGainControl: true });
  } catch {
    return null;
  }
}

export function stopCapture(c: Capture) {
  c.screen?.stop();
  c.screenAudio?.stop();
  c.mic?.stop();
}
