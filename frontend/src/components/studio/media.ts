import {
  createLocalAudioTrack,
  createLocalScreenTracks,
  createLocalVideoTrack,
  LocalAudioTrack,
  LocalVideoTrack,
  VideoPreset,
  type TrackPublishDefaults,
  type VideoResolution,
} from "livekit-client";

export type Facing = "user" | "environment";

export type VideoCapture =
  | { kind: "screen"; track: LocalVideoTrack; audio?: LocalAudioTrack }
  | { kind: "camera"; track: LocalVideoTrack; facing: Facing };

export type Capture = {
  video?: VideoCapture;
  mic?: LocalAudioTrack;
  micMuted: boolean;
};

export type StudioMode = VideoCapture["kind"];

export const NO_CAPTURE: Capture = { micMuted: false };

/** 가로세로를 같게 둬서 세로로 든 휴대폰 카메라를 16:9로 잘라내지 않게 한다 */
export const CAMERA_RESOLUTION: VideoResolution = { width: 1280, height: 1280, frameRate: 30 };

/**
 * LiveKit 기본값은 화면 공유 15fps, 카메라 낮은 층 20fps라 끊겨 보인다.
 * 카메라는 휴대폰에서 60fps가 불안정해 30fps로 두고, 화면 공유만 60fps까지 올린다.
 */
export const PUBLISH_DEFAULTS: TrackPublishDefaults = {
  videoEncoding: { maxBitrate: 2_500_000, maxFramerate: 30 },
  videoSimulcastLayers: [new VideoPreset(320, 180, 200_000, 30), new VideoPreset(640, 360, 600_000, 30)],
  screenShareEncoding: { maxBitrate: 6_000_000, maxFramerate: 60 },
  screenShareSimulcastLayers: [new VideoPreset(1280, 720, 1_500_000, 30)],
};

/** 화면 캡처 API가 없는 브라우저(휴대폰 등)는 카메라 송출만 쓸 수 있다. */
export function canCaptureScreen(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.mediaDevices?.getDisplayMedia === "function";
}

/** HTTPS가 아닌 주소에서는 브라우저가 mediaDevices 자체를 감춘다. */
export function canCaptureCamera(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.mediaDevices?.getUserMedia === "function";
}

/** 방송에 올릴 트랙들. */
export function videoTracks(v: VideoCapture): (LocalVideoTrack | LocalAudioTrack)[] {
  return v.kind === "screen" && v.audio ? [v.track, v.audio] : [v.track];
}

/**
 * 사용자가 화면 선택 창에서 취소하면 null. 그 밖의 실패는 던진다.
 * 탭/시스템 소리는 사용자가 선택 창에서 체크해야 들어오므로 audio가 없을 수 있다.
 */
export async function captureScreen(): Promise<VideoCapture | null> {
  try {
    const tracks = await createLocalScreenTracks({
      audio: true,
      systemAudio: "include",
      // 이 스튜디오 탭을 고르면 미리보기가 무한 반복되므로 선택지에서 뺀다
      selfBrowserSurface: "exclude",
      surfaceSwitching: "include",
      resolution: { width: 1920, height: 1080, frameRate: 60 },
    });
    const video = tracks.find((t) => t instanceof LocalVideoTrack);
    if (!video) {
      tracks.forEach((t) => t.stop());
      throw new Error("no video track");
    }
    return { kind: "screen", track: video, audio: tracks.find((t) => t instanceof LocalAudioTrack) };
  } catch (e) {
    if (e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "AbortError")) return null;
    throw e;
  }
}

/** 권한 거부 · 장치 없음은 null. 그 밖의 실패는 던진다. */
export async function captureCamera(facing: Facing): Promise<VideoCapture | null> {
  try {
    const track = await createLocalVideoTrack({ facingMode: facing, resolution: CAMERA_RESOLUTION });
    return { kind: "camera", track, facing };
  } catch (e) {
    if (e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "NotFoundError" || e.name === "OverconstrainedError")) return null;
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

export function stopVideo(v: VideoCapture) {
  videoTracks(v).forEach((t) => t.stop());
}

export function stopCapture(c: Capture) {
  if (c.video) stopVideo(c.video);
  c.mic?.stop();
}
