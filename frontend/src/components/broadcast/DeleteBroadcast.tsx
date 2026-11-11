"use client";

import { useRouter } from "next/navigation";
import { useLayoutEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Broadcast } from "@/lib/broadcast";
import { useAuth } from "../auth/AuthProvider";

type Step = { kind: "idle" } | { kind: "confirm"; error: string | null } | { kind: "deleting" };

/** 삭제 버튼과 화면 안 확인 단계. 지우면 내 방송으로 돌아간다. */
export function DeleteBroadcast({ broadcast }: { broadcast: Pick<Broadcast, "id" | "title" | "status"> }) {
  const router = useRouter();
  const { refresh } = useAuth();
  const [step, setStep] = useState<Step>({ kind: "idle" });

  // 확인 단계를 열어 둔 채 다른 화면으로 가면, 돌아왔을 때는 닫혀 있어야 한다
  useLayoutEffect(() => () => setStep({ kind: "idle" }), []);

  const remove = async () => {
    setStep({ kind: "deleting" });
    try {
      await api<void>(`/api/broadcasts/${broadcast.id}`, { method: "DELETE" });
      router.push("/my");
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        await refresh();
      }
      setStep({ kind: "confirm", error: e instanceof ApiError ? e.message : "삭제하지 못했습니다." });
    }
  };

  if (step.kind === "idle") {
    return (
      <button
        type="button"
        onClick={() => setStep({ kind: "confirm", error: null })}
        className="rounded-lg border border-line px-3 py-2 text-sm text-danger hover:border-danger/60 hover:bg-danger/10"
      >
        삭제
      </button>
    );
  }

  const deleting = step.kind === "deleting";
  return (
    <div role="alertdialog" aria-labelledby={`delete-${broadcast.id}`} className="flex w-full flex-col gap-3 rounded-xl border border-danger/40 bg-danger/10 p-4">
      <p id={`delete-${broadcast.id}`} className="text-sm">
        <span className="font-semibold break-words">‘{broadcast.title}’</span> 방송을 삭제할까요? 삭제하면 되돌릴 수 없습니다.
        {broadcast.status === "LIVE" && <span className="mt-1 block text-danger">지금 방송 중입니다. 방송을 먼저 종료해야 삭제할 수 있습니다.</span>}
      </p>
      {step.kind === "confirm" && step.error && (
        <p role="alert" className="text-sm font-medium text-danger">
          {step.error}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="button"
          onClick={remove}
          disabled={deleting}
          className="rounded-lg bg-danger px-3 py-2 text-sm font-semibold text-bg hover:opacity-90 disabled:opacity-60"
        >
          {deleting ? "삭제하는 중…" : "삭제하기"}
        </button>
        <button
          type="button"
          onClick={() => setStep({ kind: "idle" })}
          disabled={deleting}
          autoFocus
          className="rounded-lg px-3 py-2 text-sm text-muted hover:bg-surface-2 hover:text-fg disabled:opacity-60"
        >
          취소
        </button>
      </div>
    </div>
  );
}
