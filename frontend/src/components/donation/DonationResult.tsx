"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { confirmDonation, formatWon, type Donation } from "@/lib/donations";
import { Notice } from "../ui";

type Params = { paymentKey?: string; orderId?: string; amount?: string; broadcastId?: string };

type State = { kind: "confirming" } | { kind: "done"; donation: Donation } | { kind: "error"; message: string };

const backHref = (broadcastId?: string) => (broadcastId && /^\d+$/.test(broadcastId) ? `/broadcasts/${broadcastId}` : "/");

/**
 * 결제창 성공 뒤 돌아오는 화면. 받은 값을 서버로 넘겨 승인 · 적립한다.
 * 새로고침 등으로 같은 요청이 다시 가도 서버가 한 번만 적립한다.
 */
export function DonationSuccess({ params }: { params: Params }) {
  const [state, setState] = useState<State>({ kind: "confirming" });
  const sent = useRef(false);

  useEffect(() => {
    if (sent.current) return;
    sent.current = true;
    const amount = Number(params.amount);
    if (!params.paymentKey || !params.orderId || !Number.isInteger(amount)) {
      Promise.resolve().then(() => setState({ kind: "error", message: "결제 정보가 올바르지 않습니다." }));
      return;
    }
    confirmDonation(params.paymentKey, params.orderId, amount)
      .then((donation) => setState({ kind: "done", donation }))
      .catch((e) =>
        setState({ kind: "error", message: e instanceof ApiError ? e.message : "후원을 확정하지 못했습니다." }),
      );
  }, [params.paymentKey, params.orderId, params.amount]);

  const back = (
    <Link href={backHref(params.broadcastId)} className="rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line">
      방송으로 돌아가기
    </Link>
  );
  if (state.kind === "confirming") {
    return <Notice title="결제를 확인하는 중입니다" description="잠시만 기다려 주세요." />;
  }
  if (state.kind === "error") {
    return (
      <Notice title="후원이 완료되지 않았습니다" description={state.message}>
        {back}
      </Notice>
    );
  }
  return (
    <Notice
      title={`${formatWon(state.donation.amount)} 후원 완료`}
      description={state.donation.message ? `“${state.donation.message}”` : "방송자에게 후원이 전달되었습니다."}
    >
      {back}
    </Notice>
  );
}

export function DonationFail({ params }: { params: { code?: string; message?: string; broadcastId?: string } }) {
  const canceled = params.code === "PAY_PROCESS_CANCELED" || params.code === "USER_CANCEL";
  return (
    <Notice
      title={canceled ? "결제를 취소했습니다" : "결제에 실패했습니다"}
      description={canceled ? "후원이 진행되지 않았습니다." : (params.message ?? "다시 시도해 주세요.")}
    >
      <Link href={backHref(params.broadcastId)} className="rounded-lg bg-surface-2 px-4 py-2 text-sm hover:bg-line">
        방송으로 돌아가기
      </Link>
    </Notice>
  );
}
