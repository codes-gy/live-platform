import type { Metadata } from "next";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { MyAccount } from "./MyAccount";

export const metadata: Metadata = { title: "내 방송" };

export default function MyPage() {
  return (
    <RequireAuth>
      <MyAccount />
    </RequireAuth>
  );
}
