import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    proxy: process.env.API_PROXY_TARGET
      ? {
          "/api": { target: process.env.API_PROXY_TARGET, changeOrigin: false },
        }
      : {},
  },
  preview: { host: "127.0.0.1", allowedHosts: ["localhost", "127.0.0.1"] },
  test: {
    environment: "jsdom",
    setupFiles: ["./tests/setup.ts"],
    include: [
      "tests/**/*.test.tsx",
      "tests/e2e/**/*.spec.ts",
      "tests/browser/**/*.spec.ts",
    ],
    restoreMocks: true,
  },
});
