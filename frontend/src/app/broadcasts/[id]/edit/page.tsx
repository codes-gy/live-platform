import type { Metadata } from "next";
import { Suspense } from "react";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { EditBroadcast } from "@/components/broadcast/EditBroadcast";
import { parseBroadcastId } from "@/lib/broadcast";

export const metadata: Metadata = { title: "방송 수정" };

async function EditWithParams({ params }: { params: PageProps<"/broadcasts/[id]/edit">["params"] }) {
  const { id } = await params;
  return <EditBroadcast id={parseBroadcastId(id)} />;
}

export default function EditBroadcastPage({ params }: PageProps<"/broadcasts/[id]/edit">) {
  return (
    <div className="mx-auto w-full max-w-2xl">
      {/* 동적 경로라 RequireAuth의 usePathname()도 Suspense 안에 있어야 프리렌더된다 */}
      <Suspense fallback={<div className="h-80 animate-pulse rounded-2xl bg-surface-2" />}>
        <RequireAuth>
          <EditWithParams params={params} />
        </RequireAuth>
      </Suspense>
    </div>
  );
}
