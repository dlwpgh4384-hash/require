import { loadEnv } from "vite";
import { configDefaults, defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// 화면 개발 서버·미리보기 서버 주소(로그인(UI-22) ⑬-3).
// 기본은 이 PC에서만(127.0.0.1). 같은 내부망에서 열려면 환경 변수 VITE_DEV_HOST에 주소를 준다
// (예: VITE_DEV_HOST=0.0.0.0 npm run dev). .env.local에 적어도 된다. 비우면 지금과 같다.
export default defineConfig(({ mode }) => {
  const host = loadEnv(mode, ".", "VITE_DEV_HOST").VITE_DEV_HOST?.trim() || "127.0.0.1";
  return {
    plugins: [react()],
    server: { host },
    preview: {
      host,
      allowedHosts: [...new Set(["localhost", "127.0.0.1", host])],
    },
    // 화면 시험 공통 설정(기다리는 시간 등). 실제 화면 동작에는 쓰이지 않는다.
    test: {
      css: { include: [/\?render-test$/] },
      setupFiles: ["./src/test/setup.ts"],
      exclude: [...configDefaults.exclude, "e2e/**"],
    },
  };
});
