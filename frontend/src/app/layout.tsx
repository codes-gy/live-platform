import type { Metadata, Viewport } from "next";
import { AuthProvider } from "@/components/auth/AuthProvider";
import { Header } from "@/components/Header";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "핏", template: "%s · 핏" },
  description: "PC 화면 공유나 휴대폰 카메라로 바로 라이브 방송을 켜고, 어느 기기에서나 실시간으로 시청하세요.",
};

export const viewport: Viewport = {
  themeColor: "#0f1013",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full">
      {/* Pretendard(OFL): 쓰인 글자 구간만 내려받는 동적 서브셋 */}
      <link
        rel="stylesheet"
        href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css"
        precedence="default"
        crossOrigin="anonymous"
      />
      <body className="flex min-h-full flex-col">
        <AuthProvider>
          <Header />
          <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 py-6 sm:px-6">{children}</main>
        </AuthProvider>
      </body>
    </html>
  );
}
