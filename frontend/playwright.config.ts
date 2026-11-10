import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/playwright",
  testMatch: "**/*.pw.ts",
  fullyParallel: true,
  use: {
    baseURL: process.env.UI_BASE_URL ?? "http://127.0.0.1:3000",
    trace: "retain-on-failure",
  },
  // The QA runner starts Compose. No test starts a server or writes shared seed rows.
});
