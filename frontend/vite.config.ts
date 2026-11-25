import { configDefaults, defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: { host: "127.0.0.1" },
  preview: { host: "127.0.0.1", allowedHosts: ["localhost", "127.0.0.1"] },
  // 화면 시험 공통 설정(기다리는 시간 등). 실제 화면 동작에는 쓰이지 않는다.
  test: {
    css: { include: [/\?render-test$/] },
    setupFiles: ["./src/test/setup.ts"],
    exclude: [...configDefaults.exclude, "e2e/**"],
  },
});
