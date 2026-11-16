import { expect, type APIRequestContext, type Browser, type Page } from "@playwright/test";

export type TestAccount = "STREAMER" | "VIEWER";

/** 로그인 화면의 테스트 계정 버튼으로 로그인한 새 브라우저 컨텍스트 */
export async function loginAs(browser: Browser, account: TestAccount): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("/login");
  await page.getByRole("button", { name: account === "STREAMER" ? /방송자로 시작/ : /시청자로 시작/ }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
  return page;
}

/** 로그인한 페이지의 쿠키로 방송을 만든다 */
export async function createBroadcast(page: Page, title: string): Promise<number> {
  const response = await page.request.post("/api/broadcasts", { data: { title } });
  expect(response.status()).toBe(201);
  return (await response.json()).id as number;
}

export async function broadcastStatus(request: APIRequestContext, id: number): Promise<string> {
  const response = await request.get(`/api/broadcasts/${id}`);
  return (await response.json()).status as string;
}

/** 시험 방송 정리(LIVE면 종료 후 삭제). 실패해도 테스트 결과에는 영향 없음 */
export async function cleanup(page: Page | undefined, id: number | undefined) {
  if (!page || id === undefined) return;
  await page.request.post(`/api/broadcasts/${id}/end`).catch(() => undefined);
  await page.request.delete(`/api/broadcasts/${id}`).catch(() => undefined);
}
