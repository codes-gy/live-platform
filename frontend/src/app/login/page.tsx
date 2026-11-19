import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";
import { safeNext } from "@/lib/navigation";

export const metadata: Metadata = { title: "로그인" };

async function LoginWithNext({ searchParams }: { searchParams: PageProps<"/login">["searchParams"] }) {
  const { next } = await searchParams;
  return <LoginForm next={safeNext(next)} />;
}

/** 돌아갈 주소(next)만 요청마다 다르므로, 그 부분만 Suspense 안에서 읽는다. */
export default function LoginPage({ searchParams }: PageProps<"/login">) {
  return (
    <Suspense fallback={<LoginForm next="/" />}>
      <LoginWithNext searchParams={searchParams} />
    </Suspense>
  );
}
