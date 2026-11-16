import { expect, test } from "@playwright/test";
import { cleanup, createBroadcast, loginAs } from "./helpers";

test("비로그인 사용자는 목록을 볼 수 있다", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("link", { name: /로그인/ }).first()).toBeVisible();
  const response = await page.request.get("/api/broadcasts");
  expect(response.status()).toBe(200);
});

test("비로그인 사용자가 방송 만들기 · 송출 화면에 가면 로그인으로 보낸다", async ({ page }) => {
  await page.goto("/broadcasts/new");
  await expect(page).toHaveURL(/\/login\?next=%2Fbroadcasts%2Fnew/);
  await page.goto("/studio/1");
  await expect(page).toHaveURL(/\/login\?next=%2Fstudio%2F1/);
  // 화면 우회와 별개로 서버도 거절한다
  expect((await page.request.post("/api/broadcasts", { data: { title: "x" } })).status()).toBe(401);
});

test("다른 사용자는 남의 방송을 수정 · 삭제 · 송출할 수 없다", async ({ browser }) => {
  const owner = await loginAs(browser, "STREAMER");
  const id = await createBroadcast(owner, "E2E 권한 확인");
  try {
    const other = await loginAs(browser, "VIEWER");
    await other.goto(`/studio/${id}`);
    await expect(other.getByText("송출할 권한이 없습니다")).toBeVisible();
    expect((await other.request.patch(`/api/broadcasts/${id}`, { data: { title: "탈취" } })).status()).toBe(403);
    expect((await other.request.delete(`/api/broadcasts/${id}`)).status()).toBe(403);
    expect((await other.request.post(`/api/broadcasts/${id}/start`)).status()).toBe(403);
  } finally {
    await cleanup(owner, id);
  }
});
