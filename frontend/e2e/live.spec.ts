import { expect, test, type Page } from "@playwright/test";
import { broadcastStatus, cleanup, createBroadcast, loginAs } from "./helpers";

/** 송출 → 다른 기기(비로그인) 시청 → 댓글 실시간 → 종료 */
test("카메라 송출을 비로그인 시청자가 받고, 종료하면 양쪽이 끝난다", async ({ browser }) => {
  const streamer = await loginAs(browser, "STREAMER");
  let id: number | undefined;
  try {
    id = await createBroadcast(streamer, "E2E 송출 · 시청");

    // 방송자: 카메라 모드 → 카메라 켜기 → 방송 시작
    await streamer.goto(`/studio/${id}`);
    await streamer.getByRole("button", { name: "카메라", exact: true }).click();
    await streamer.getByRole("button", { name: "카메라 켜기" }).first().click();
    await streamer.getByRole("button", { name: "방송 시작" }).click();
    await expect.poll(() => broadcastStatus(streamer.request, id!), { timeout: 30_000 }).toBe("LIVE");

    // 시청자: 로그인 없이 상세에서 영상 · 소리 수신
    const viewer = await (await browser.newContext()).newPage();
    await viewer.goto(`/broadcasts/${id}`);
    await expect.poll(() => playingVideoWidth(viewer), { timeout: 30_000 }).toBeGreaterThan(0);
    await expect.poll(() => viewer.locator("audio").count()).toBeGreaterThan(0);
    await expect(viewer.getByRole("link", { name: /로그인/ }).first()).toBeVisible(); // 비로그인은 댓글 대신 로그인 링크

    // 다른 로그인 사용자의 댓글이 비로그인 시청자 화면에 실시간(SSE)으로 도착
    const commenter = await loginAs(browser, "VIEWER");
    const text = `E2E 댓글 ${Date.now()}`;
    expect((await commenter.request.post(`/api/broadcasts/${id}/comments`, { data: { content: text } })).status()).toBe(201);
    await expect(viewer.getByText(text)).toBeVisible({ timeout: 10_000 });

    // 종료: 서버 ENDED, 시청자 화면도 종료로 바뀐다
    await streamer.getByRole("button", { name: "방송 종료" }).first().click();
    await streamer.getByRole("button", { name: "종료", exact: true }).click();
    await expect.poll(() => broadcastStatus(streamer.request, id!), { timeout: 20_000 }).toBe("ENDED");
    await expect(viewer.getByText("방송이 종료되었습니다").first()).toBeVisible();
  } finally {
    await cleanup(streamer, id);
  }
});

function playingVideoWidth(page: Page) {
  return page.evaluate(() => Math.max(0, ...[...document.querySelectorAll("video")].map((v) => v.videoWidth)));
}
