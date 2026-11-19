"use client";

import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";

const buttonClass = "rounded-lg border border-line px-3 py-1.5 text-sm hover:bg-surface-2";

/**
 * 방송 주소 공유: 링크 복사와 "휴대폰으로 보기" QR.
 * 다른 기기에서 시청하는 흐름(필수 요구사항)을 시연 · 확인하기 쉽게 한다.
 * 주소는 렌더 중이 아니라 버튼을 누를 때 읽는다(서버 프리렌더에는 window가 없다).
 */
export function ShareBroadcast() {
  const [copied, setCopied] = useState(false);
  const [qr, setQr] = useState<{ url: string; svg: string } | null>(null);
  const [failed, setFailed] = useState(false);
  const resetTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(resetTimer.current), []);

  const copy = () => {
    setFailed(false);
    navigator.clipboard
      .writeText(window.location.href)
      .then(() => {
        setCopied(true);
        clearTimeout(resetTimer.current);
        resetTimer.current = setTimeout(() => setCopied(false), 2000);
      })
      .catch(() => setFailed(true));
  };

  const toggleQr = () => {
    if (qr) {
      setQr(null);
      return;
    }
    const url = window.location.href;
    QRCode.toString(url, { type: "svg", margin: 1, width: 176 })
      .then((svg) => setQr({ url, svg }))
      .catch(() => setFailed(true));
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={copy} className={buttonClass} aria-live="polite">
          {copied ? "복사했습니다" : "링크 복사"}
        </button>
        <button type="button" onClick={toggleQr} className={buttonClass} aria-expanded={qr !== null}>
          {qr ? "QR 닫기" : "휴대폰으로 보기"}
        </button>
      </div>
      {failed && <p className="text-sm text-live">주소를 복사하거나 QR을 만들지 못했습니다. 주소창의 주소를 직접 공유해 주세요.</p>}
      {qr && (
        <figure className="flex w-fit flex-col items-center gap-2 rounded-xl border border-line bg-white p-3">
          {/* qrcode가 만든 SVG(외부 입력 없음, 현재 페이지 주소만 담김) */}
          <div className="size-44" role="img" aria-label="이 방송 주소 QR 코드" dangerouslySetInnerHTML={{ __html: qr.svg }} />
          <figcaption className="max-w-44 text-center text-xs break-all text-neutral-600">휴대폰 카메라로 찍으면 이 방송이 열립니다</figcaption>
        </figure>
      )}
    </div>
  );
}
