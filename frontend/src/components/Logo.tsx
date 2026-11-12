import Link from "next/link";

/**
 * 토끼 실루엣. 테두리 · 채움 · 잘라내기 영역이 모두 같은 모양을 쓴다.
 * clipPath 안에는 <g>를 둘 수 없어 도형만 돌려준다(색은 감싸는 쪽에서 정한다).
 */
function RabbitShape() {
  return (
    <>
      <rect x="6.5" y="1" width="4" height="11" rx="2" transform="rotate(-10 8.5 12)" />
      <rect x="13.5" y="1" width="4" height="11" rx="2" transform="rotate(10 15.5 12)" />
      <ellipse cx="12" cy="15.5" rx="7.5" ry="6.5" />
    </>
  );
}

/**
 * 방송 표시등 대신 토끼: 아래에서부터 빨갛게 차올랐다가 비워지기를 반복한다.
 * 테두리를 굵게 그린 뒤 같은 모양을 바탕색으로 덮어 귀와 머리가 겹치는 안쪽 선을 지운다.
 */
export function Logo() {
  return (
    <Link href="/" className="flex items-center gap-2 font-bold tracking-tight" aria-label="핏 홈">
      <svg viewBox="0 0 24 24" className="h-6 w-6" aria-hidden>
        <defs>
          <clipPath id="logo-rabbit">
            <RabbitShape />
          </clipPath>
        </defs>
        <g fill="none" stroke="var(--color-live)" strokeWidth="2.4">
          <RabbitShape />
        </g>
        <g fill="var(--color-bg)">
          <RabbitShape />
        </g>
        <g clipPath="url(#logo-rabbit)">
          <rect x="0" y="0" width="24" height="24" fill="var(--color-live)" className="logo-rabbit-fill" />
        </g>
        <circle cx="9.3" cy="15" r="1" fill="var(--color-bg)" />
        <circle cx="14.7" cy="15" r="1" fill="var(--color-bg)" />
      </svg>
      <span className="text-lg">핏</span>
    </Link>
  );
}
