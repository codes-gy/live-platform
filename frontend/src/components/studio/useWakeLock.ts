import { useEffect } from "react";

/** 방송 중 휴대폰 화면이 꺼지면 카메라가 멈추므로 화면을 켜 둔다. API가 없으면 아무 일도 하지 않는다. */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || !navigator.wakeLock) return;
    let lock: WakeLockSentinel | null = null;
    let stopped = false;

    const acquire = async () => {
      if (lock && !lock.released) return;
      try {
        const next = await navigator.wakeLock.request("screen");
        if (stopped) void next.release();
        else lock = next;
      } catch {
        // 배터리 절약 모드 등으로 거부될 수 있다. 방송에는 영향이 없다
      }
    };
    // 탭이 숨겨지면 브라우저가 잠금을 풀어 버리므로 다시 보일 때 새로 요청한다
    const onVisible = () => {
      if (document.visibilityState === "visible") void acquire();
    };

    void acquire();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      stopped = true;
      document.removeEventListener("visibilitychange", onVisible);
      void lock?.release();
    };
  }, [active]);
}
