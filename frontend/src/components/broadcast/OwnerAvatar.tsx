/** 방송자 닉네임 첫 글자. 썸네일이 없는 화면 자리에서 누구의 방송인지 먼저 보이게 한다. */
export function OwnerAvatar({ nickname, live = false }: { nickname: string; live?: boolean }) {
  return (
    <span
      aria-hidden
      className={`flex h-12 w-12 items-center justify-center rounded-full text-lg font-bold ${
        live ? "bg-live/20 text-fg ring-2 ring-live/70" : "bg-surface-2 text-muted ring-1 ring-line"
      }`}
    >
      {[...nickname.trim()][0] ?? "?"}
    </span>
  );
}
