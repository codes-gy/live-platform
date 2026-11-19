import type { Metadata } from "next";
import { Suspense } from "react";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { Studio } from "@/components/studio/Studio";
import { parseBroadcastId } from "@/lib/broadcast";

export const metadata: Metadata = { title: "송출 화면" };

async function StudioWithParams({ params }: { params: PageProps<"/studio/[id]">["params"] }) {
  const { id } = await params;
  return <Studio id={parseBroadcastId(id)} />;
}

/** 스튜디오 모양 그대로의 뼈대. 로그인 확인 · 방송 정보를 불러오는 동안 보인다. */
function StudioSkeleton() {
  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]" aria-busy>
      <div className="flex flex-col gap-4">
        <div className="h-8 w-2/3 animate-pulse rounded bg-surface-2" />
        <div className="aspect-video animate-pulse rounded-xl bg-surface-2" />
        <div className="h-24 animate-pulse rounded-2xl bg-surface-2" />
      </div>
      <div className="hidden animate-pulse rounded-xl bg-surface-2 lg:block" />
    </div>
  );
}

export default function StudioPage({ params }: PageProps<"/studio/[id]">) {
  return (
    <Suspense fallback={<StudioSkeleton />}>
      <RequireAuth fallback={<StudioSkeleton />}>
        <StudioWithParams params={params} />
      </RequireAuth>
    </Suspense>
  );
}
