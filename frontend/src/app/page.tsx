import Link from "next/link";
import { BroadcastList } from "@/components/broadcast/BroadcastList";

const FEATURES = ["PC 화면 공유 + 소리", "휴대폰 카메라 송출", "실시간 댓글", "로그인 없이 시청"];

export default function HomePage() {
  return (
    <div className="flex flex-col gap-10">
      <section className="relative overflow-hidden rounded-2xl border border-line bg-surface px-6 py-8 shadow-sm sm:px-10 sm:py-12">
        <div aria-hidden className="pointer-events-none absolute -right-20 -top-24 h-72 w-72 rounded-full bg-live/15 blur-3xl" />
        <div aria-hidden className="pointer-events-none absolute -bottom-24 right-40 h-56 w-56 rounded-full bg-[#ffb3c1]/30 blur-3xl" />
        <div className="relative flex flex-col gap-4">
          <p className="text-sm font-semibold text-live-strong">설치 없이 브라우저에서 바로</p>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">지금 켜고, 어디서든 보기</h1>
          <p className="max-w-xl text-muted">PC 화면 공유나 휴대폰 카메라로 바로 방송을 시작하세요. 시청은 로그인 없이도 됩니다.</p>
          <ul className="flex flex-wrap gap-2">
            {FEATURES.map((f) => (
              <li key={f} className="rounded-full border border-line bg-surface-2 px-3 py-1 text-xs font-medium text-muted">
                {f}
              </li>
            ))}
          </ul>
          <div className="mt-2 flex flex-wrap gap-2">
            <Link href="/broadcasts/new" className="inline-flex h-11 items-center rounded-lg bg-live px-5 font-semibold text-white shadow-sm hover:bg-live-strong">
              방송 시작하기
            </Link>
            <a href="#broadcasts" className="inline-flex h-11 items-center rounded-lg border border-line bg-surface px-5 font-semibold hover:bg-surface-2">
              방송 둘러보기
            </a>
          </div>
        </div>
      </section>
      <div id="broadcasts" className="scroll-mt-20">
        <BroadcastList />
      </div>
    </div>
  );
}
