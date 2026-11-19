"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loadTossPayments } from "@tosspayments/tosspayments-sdk";
import { ApiError } from "@/lib/api";
import type { Broadcast } from "@/lib/broadcast";
import {
  DONATION_MAX,
  DONATION_MESSAGE_MAX,
  DONATION_MIN,
  DONATION_PRESETS,
  fetchDonations,
  formatWon,
  orderDonation,
  type DonationSummary,
} from "@/lib/donations";
import { loginPath } from "@/lib/navigation";
import { useAuth } from "../auth/AuthProvider";
import { ErrorMessage, PrimaryButton } from "../ui";

/** 방송 중이면 이 간격으로 합계 · 내역을 다시 읽는다. */
const LIVE_POLL_MS = 15_000;

/**
 * 방송별 후원 합계 · 최근 내역과, 방송 중인 남의 방송이면 후원하기(토스페이먼츠 테스트 결제창).
 * 금액 · 대상은 서버가 주문으로 저장하고, 결제 뒤 /donations/success에서 서버가 승인 · 적립한다.
 */
export function DonationPanel({ broadcast }: { broadcast: Broadcast }) {
  const { user, status } = useAuth();
  const [summary, setSummary] = useState<DonationSummary | null>(null);
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState(DONATION_PRESETS[1]);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const live = broadcast.status === "LIVE";
  useEffect(() => {
    let active = true;
    const load = () =>
      fetchDonations(broadcast.id)
        .then((s) => active && setSummary(s))
        .catch(() => undefined);
    load();
    const timer = live ? setInterval(load, LIVE_POLL_MS) : undefined;
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [broadcast.id, live]);

  const isOwner = user?.id === broadcast.owner.id;
  const validAmount = Number.isInteger(amount) && amount >= DONATION_MIN && amount <= DONATION_MAX;

  const donate = async () => {
    setBusy(true);
    setError(null);
    try {
      const order = await orderDonation(broadcast.id, amount, message);
      const toss = await loadTossPayments(order.clientKey);
      const payment = toss.payment({ customerKey: order.customerKey });
      const back = `${window.location.origin}/donations`;
      await payment.requestPayment({
        method: "CARD",
        amount: { currency: "KRW", value: order.amount },
        orderId: order.orderId,
        orderName: order.orderName,
        successUrl: `${back}/success?broadcastId=${broadcast.id}`,
        failUrl: `${back}/fail?broadcastId=${broadcast.id}`,
      });
    } catch (e) {
      // 결제창을 닫으면 SDK가 USER_CANCEL 오류를 던진다
      const code = (e as { code?: string }).code;
      if (code !== "USER_CANCEL") {
        setError(e instanceof ApiError || e instanceof Error ? e.message : "결제창을 열지 못했습니다.");
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4" aria-label="후원">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-semibold">후원</h2>
        <p className="text-sm text-muted">
          {summary ? (
            <>
              <strong className="text-base text-fg">{formatWon(summary.totalAmount)}</strong> · {summary.count}건
            </>
          ) : (
            "불러오는 중"
          )}
        </p>
      </div>

      {live && !isOwner && status !== "loading" && (
        user ? (
          open ? (
            <div className="flex flex-col gap-2">
              <div className="flex flex-wrap gap-2">
                {DONATION_PRESETS.map((p) => (
                  <button
                    key={p}
                    type="button"
                    onClick={() => setAmount(p)}
                    aria-pressed={amount === p}
                    className={`rounded-lg border px-3 py-1.5 text-sm ${amount === p ? "border-live bg-live/10 font-semibold text-live" : "border-line hover:bg-surface-2"}`}
                  >
                    {formatWon(p)}
                  </button>
                ))}
              </div>
              <label className="flex items-center gap-2 text-sm">
                <span className="shrink-0 text-muted">직접 입력</span>
                <input
                  type="number"
                  inputMode="numeric"
                  min={DONATION_MIN}
                  max={DONATION_MAX}
                  step={1000}
                  value={Number.isNaN(amount) ? "" : amount}
                  onChange={(e) => setAmount(e.target.valueAsNumber)}
                  className="h-10 w-full min-w-0 rounded-lg border border-line bg-surface-2 px-3 outline-none focus:border-live"
                />
              </label>
              <input
                type="text"
                placeholder="응원 메시지(선택)"
                maxLength={DONATION_MESSAGE_MAX}
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                className="h-10 rounded-lg border border-line bg-surface-2 px-3 text-sm outline-none focus:border-live"
              />
              <ErrorMessage message={error} />
              <div className="flex gap-2">
                <PrimaryButton type="button" onClick={donate} disabled={busy || !validAmount} className="flex-1">
                  {busy ? "결제창 여는 중…" : validAmount ? `${formatWon(amount)} 후원하기` : "1,000원 ~ 1,000,000원"}
                </PrimaryButton>
                <button type="button" onClick={() => setOpen(false)} className="rounded-lg border border-line px-3 text-sm hover:bg-surface-2">
                  닫기
                </button>
              </div>
              <p className="text-xs text-muted">테스트 결제입니다. 실제로 돈이 나가지 않습니다.</p>
            </div>
          ) : (
            <PrimaryButton type="button" onClick={() => setOpen(true)}>
              {broadcast.owner.nickname}님 후원하기
            </PrimaryButton>
          )
        ) : (
          <Link
            href={loginPath(`/broadcasts/${broadcast.id}`)}
            className="rounded-lg border border-line px-3 py-2 text-center text-sm hover:bg-surface-2"
          >
            로그인하고 후원하기
          </Link>
        )
      )}

      {summary && summary.items.length > 0 && (
        <ul className="flex max-h-48 flex-col gap-1.5 overflow-y-auto text-sm">
          {summary.items.map((d) => (
            <li key={d.id} className="flex flex-col rounded-lg bg-surface-2 px-3 py-2">
              <span>
                <strong>{d.donor.nickname}</strong>님 <span className="font-semibold text-live">{formatWon(d.amount)}</span>
              </span>
              {d.message && <span className="break-words text-muted">{d.message}</span>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
