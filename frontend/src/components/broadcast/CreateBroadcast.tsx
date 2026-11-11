"use client";

import { useRouter } from "next/navigation";
import { api, json } from "@/lib/api";
import type { Broadcast, BroadcastInput } from "@/lib/broadcast";
import { Card } from "../ui";
import { BroadcastForm } from "./BroadcastForm";

export function CreateBroadcast() {
  const router = useRouter();

  const create = async (input: BroadcastInput) => {
    const created = await api<Broadcast>("/api/broadcasts", { method: "POST", body: json(input) });
    router.push(`/broadcasts/${created.id}`);
  };

  return (
    <Card>
      <h1 className="text-2xl font-bold">방송 만들기</h1>
      <p className="mt-1 text-sm text-muted">방송을 먼저 등록한 뒤 송출 화면에서 방송을 시작합니다.</p>
      <div className="mt-6">
        <BroadcastForm submitLabel="방송 등록" pendingLabel="등록하는 중…" cancelHref="/my" onSubmit={create} />
      </div>
    </Card>
  );
}
