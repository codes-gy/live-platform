import type { Metadata } from "next";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { CreateBroadcast } from "@/components/broadcast/CreateBroadcast";

export const metadata: Metadata = { title: "방송 만들기" };

export default function NewBroadcastPage() {
  return (
    <div className="mx-auto w-full max-w-2xl">
      <RequireAuth>
        <CreateBroadcast />
      </RequireAuth>
    </div>
  );
}
