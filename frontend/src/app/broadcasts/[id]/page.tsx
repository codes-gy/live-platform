import type { Metadata } from "next";
import { Suspense } from "react";
import { BroadcastDetail } from "@/components/broadcast/BroadcastDetail";

export const metadata: Metadata = { title: "방송 보기" };

async function DetailWithParams({ params }: { params: PageProps<"/broadcasts/[id]">["params"] }) {
  const { id } = await params;
  const broadcastId = Number(id);
  // 숫자가 아니면 상세가 "찾을 수 없음"을 보여 준다(0은 없는 ID)
  return <BroadcastDetail id={Number.isSafeInteger(broadcastId) && broadcastId > 0 ? broadcastId : 0} />;
}

export default function BroadcastPage({ params }: PageProps<"/broadcasts/[id]">) {
  return (
    <div className="mx-auto w-full max-w-4xl">
      <Suspense fallback={<div className="aspect-video w-full animate-pulse rounded-xl bg-surface-2" />}>
        <DetailWithParams params={params} />
      </Suspense>
    </div>
  );
}
