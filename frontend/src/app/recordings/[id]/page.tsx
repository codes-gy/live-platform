import type { Metadata } from "next";
import { Suspense } from "react";
import { RecordingDetail } from "@/components/recording/RecordingDetail";
import { parseBroadcastId } from "@/lib/broadcast";

export const metadata: Metadata = { title: "다시보기" };

async function DetailWithParams({ params }: { params: PageProps<"/recordings/[id]">["params"] }) {
  const { id } = await params;
  // 숫자가 아니면 0(없는 ID) → "찾을 수 없음"
  return <RecordingDetail id={parseBroadcastId(id)} />;
}

export default function RecordingPage({ params }: PageProps<"/recordings/[id]">) {
  return (
    <div className="mx-auto w-full max-w-4xl">
      <Suspense fallback={<div className="aspect-video w-full animate-pulse rounded-xl bg-surface-2" />}>
        <DetailWithParams params={params} />
      </Suspense>
    </div>
  );
}
