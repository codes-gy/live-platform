import { defineConfig, devices } from "@playwright/test";

/**
 * 실제로 띄운 서비스(프론트 + 백엔드 + LiveKit)를 브라우저로 확인하는 E2E.
 * 대상 주소: E2E_BASE_URL (기본 http://localhost:3000). 실행 방법은 e2e/README.md
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    // 가짜 카메라 · 마이크(초록 화면 + 비프음)로 송출한다. 권한 창은 자동 허용
    permissions: ["camera", "microphone"],
    launchOptions: {
      args: ["--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream", "--autoplay-policy=no-user-gesture-required"],
    },
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
