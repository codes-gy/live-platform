import type { Metadata } from "next";
import { Suspense } from "react";
import { SignupForm } from "@/components/auth/SignupForm";
import { safeNext } from "@/lib/navigation";

export const metadata: Metadata = { title: "회원가입" };

async function SignupWithNext({ searchParams }: { searchParams: PageProps<"/signup">["searchParams"] }) {
  const { next } = await searchParams;
  return <SignupForm next={safeNext(next)} />;
}

export default function SignupPage({ searchParams }: PageProps<"/signup">) {
  return (
    <Suspense fallback={<SignupForm next="/" />}>
      <SignupWithNext searchParams={searchParams} />
    </Suspense>
  );
}
