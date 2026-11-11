"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, json } from "@/lib/api";
import { loadBroadcast, type Broadcast, type BroadcastInput, type BroadcastLoad } from "@/lib/broadcast";
import { useAuth } from "../auth/AuthProvider";
import { Card, Notice } from "../ui";
import { BroadcastForm } from "./BroadcastForm";
import { DeleteBroadcast } from "./DeleteBroadcast";
import { StatusBadge } from "./StatusBadge";

type State = { kind: "loading" } | BroadcastLoad;

const backLink = "rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line";

export function EditBroadcast({ id }: { id: number }) {
  const { user } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let active = true;
    loadBroadcast(id).then((result) => active && setState(result));
    return () => {
      active = false;
    };
  }, [id]);

  if (state.kind === "loading") {
    return <div className="h-80 animate-pulse rounded-2xl bg-surface-2" aria-busy />;
  }
  if (state.kind === "not-found") {
    return (
      <Notice title="방송을 찾을 수 없습니다" description="삭제되었거나 잘못된 주소입니다.">
        <Link href="/my" className={backLink}>
          내 방송으로
        </Link>
      </Notice>
    );
  }
  if (state.kind === "error") {
    return <Notice title={state.message} description="잠시 후 다시 시도해 주세요." />;
  }
  if (state.broadcast.owner.id !== user?.id) {
    return (
      <Notice title="수정할 권한이 없습니다" description="방송을 만든 사람만 수정 · 삭제할 수 있습니다.">
        <Link href={`/broadcasts/${state.broadcast.id}`} className={backLink}>
          방송 보러 가기
        </Link>
      </Notice>
    );
  }
  // 다시 불러온 내용이 바뀌었으면 폼을 새 값으로 다시 만든다
  return <EditForm key={state.broadcast.updatedAt} broadcast={state.broadcast} />;
}

function EditForm({ broadcast }: { broadcast: Broadcast }) {
  const router = useRouter();

  const save = async (input: BroadcastInput) => {
    await api<Broadcast>(`/api/broadcasts/${broadcast.id}`, { method: "PATCH", body: json(input) });
    router.push(`/broadcasts/${broadcast.id}`);
  };

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold">방송 수정</h1>
          <StatusBadge status={broadcast.status} />
        </div>
        <div className="mt-6">
          <BroadcastForm
            initial={{ title: broadcast.title, description: broadcast.description ?? "" }}
            submitLabel="저장"
            pendingLabel="저장하는 중…"
            cancelHref={`/broadcasts/${broadcast.id}`}
            onSubmit={save}
          />
        </div>
      </Card>
      <Card className="!p-5">
        <h2 className="text-sm font-semibold">방송 삭제</h2>
        <p className="mt-1 mb-3 text-xs text-muted">방송 중에는 삭제할 수 없습니다. 송출 화면에서 방송을 먼저 종료하세요.</p>
        <DeleteBroadcast broadcast={broadcast} />
      </Card>
    </div>
  );
}
