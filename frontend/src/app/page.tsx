import Link from "next/link";
import { BroadcastList } from "@/components/broadcast/BroadcastList";

export default function HomePage() {
  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-3 pt-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold sm:text-3xl">지금 켜고, 어디서든 보기</h1>
          <p className="max-w-xl text-muted">PC 화면 공유나 휴대폰 카메라로 바로 방송을 시작하세요. 시청은 로그인 없이도 됩니다.</p>
        </div>
        <Link href="/my" className="inline-flex h-11 shrink-0 items-center self-start rounded-lg bg-live px-4 font-semibold text-white hover:bg-live-strong sm:self-auto">
          방송하러 가기
        </Link>
      </section>
      <BroadcastList />
    </div>
  );
}
