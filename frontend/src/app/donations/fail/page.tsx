import type { Metadata } from "next";
import { Suspense } from "react";
import { DonationFail } from "@/components/donation/DonationResult";

export const metadata: Metadata = { title: "후원 실패" };

const one = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

async function WithParams({ searchParams }: { searchParams: PageProps<"/donations/fail">["searchParams"] }) {
  const p = await searchParams;
  return <DonationFail params={{ code: one(p.code), message: one(p.message), broadcastId: one(p.broadcastId) }} />;
}

export default function DonationFailPage({ searchParams }: PageProps<"/donations/fail">) {
  return (
    <div className="mx-auto w-full max-w-md py-10">
      <Suspense fallback={null}>
        <WithParams searchParams={searchParams} />
      </Suspense>
    </div>
  );
}
