import type { Metadata } from "next";
import { Suspense } from "react";
import { BroadcastDetail } from "@/components/broadcast/BroadcastDetail";
import { parseBroadcastId } from "@/lib/broadcast";

export const metadata: Metadata = { title: "방송 보기" };

async function DetailWithParams({ params }: { params: PageProps<"/broadcasts/[id]">["params"] }) {
  const { id } = await params;
  return <BroadcastDetail id={parseBroadcastId(id)} />;
}

export default function BroadcastPage({ params }: PageProps<"/broadcasts/[id]">) {
  return (
    <div className="mx-auto w-full max-w-6xl">
      <Suspense fallback={<div className="aspect-video w-full animate-pulse rounded-xl bg-surface-2" />}>
        <DetailWithParams params={params} />
      </Suspense>
    </div>
  );
}
