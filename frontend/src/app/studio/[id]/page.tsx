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

export default function StudioPage({ params }: PageProps<"/studio/[id]">) {
  return (
    <div className="mx-auto w-full max-w-4xl">
      <Suspense fallback={<div className="aspect-video animate-pulse rounded-2xl bg-surface-2" />}>
        <RequireAuth>
          <StudioWithParams params={params} />
        </RequireAuth>
      </Suspense>
    </div>
  );
}
