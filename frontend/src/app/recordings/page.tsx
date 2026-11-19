import type { Metadata } from "next";
import { RecordingList } from "@/components/recording/RecordingList";

export const metadata: Metadata = { title: "다시보기" };

export default function RecordingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">다시보기</h1>
        <p className="text-sm text-muted">끝난 라이브 방송의 녹화 영상입니다. 로그인 없이 볼 수 있습니다.</p>
      </header>
      <RecordingList />
    </div>
  );
}
