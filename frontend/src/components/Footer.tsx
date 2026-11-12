import Link from "next/link";

export function Footer() {
  return (
    <footer className="border-t border-line bg-surface">
      <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-6 text-sm text-muted sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <p>
          <span className="font-semibold text-fg">핏</span> · 설치 없이 브라우저에서 바로 켜는 라이브 방송
        </p>
        <nav className="flex gap-4">
          <Link href="/" className="hover:text-fg">
            방송 목록
          </Link>
          <Link href="/broadcasts/new" className="hover:text-fg">
            방송 만들기
          </Link>
          <a href="https://github.com/codes-gy/live-platform" target="_blank" rel="noreferrer" className="hover:text-fg">
            GitHub
          </a>
        </nav>
      </div>
    </footer>
  );
}
