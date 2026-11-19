import { expect, type APIRequestContext, type Browser, type Page } from "@playwright/test";

export type Role = "STREAMER" | "VIEWER";

/**
 * 회원가입으로 새 사용자를 만들고 로그인된 브라우저 컨텍스트를 돌려준다.
 * 실행할 때마다 새 계정을 써서 운영 데이터 · 공개 계정에 기대지 않는다.
 */
export async function loginAs(browser: Browser, role: Role): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const response = await page.request.post("/api/auth/signup", {
    data: {
      email: `e2e-${role.toLowerCase()}-${suffix}@example.com`,
      password: `e2e-${suffix}`,
      nickname: role === "STREAMER" ? "E2E방송자" : "E2E시청자",
    },
  });
  expect(response.status()).toBe(201);
  await page.goto("/");
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
