import Link from "next/link";

/** 방송 목록은 #5에서 채운다. */
export default function HomePage() {
  return (
    <section className="flex flex-col gap-3 py-10">
      <p className="text-sm font-semibold tracking-wide text-live">LIVE</p>
      <h1 className="text-3xl font-bold sm:text-4xl">지금 켜고, 어디서든 보기</h1>
      <p className="max-w-xl text-muted">
        PC 화면 공유나 휴대폰 카메라로 바로 방송을 시작하세요. 시청은 로그인 없이도 됩니다.
      </p>
      <div className="mt-2">
        <Link href="/my" className="inline-flex h-11 items-center rounded-lg bg-live px-4 font-semibold text-white hover:bg-live-strong">
          방송하러 가기
        </Link>
      </div>
    </section>
  );
}
