import type { Metadata } from "next";
import { Suspense } from "react";
import { DonationSuccess } from "@/components/donation/DonationResult";

export const metadata: Metadata = { title: "후원 완료" };

const one = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

async function WithParams({ searchParams }: { searchParams: PageProps<"/donations/success">["searchParams"] }) {
  const p = await searchParams;
  return (
    <DonationSuccess
      params={{
        paymentKey: one(p.paymentKey),
        orderId: one(p.orderId),
        amount: one(p.amount),
        broadcastId: one(p.broadcastId),
      }}
    />
  );
}

export default function DonationSuccessPage({ searchParams }: PageProps<"/donations/success">) {
  return (
    <div className="mx-auto w-full max-w-md py-10">
      <Suspense fallback={null}>
        <WithParams searchParams={searchParams} />
      </Suspense>
    </div>
  );
}
