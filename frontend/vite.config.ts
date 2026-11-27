import { configDefaults, defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// 화면 개발 서버·미리보기 서버 주소(로그인(UI-22) ⑬-3).
// 기본은 이 PC에서만(127.0.0.1). 같은 내부망에서 열려면 환경 변수 VITE_DEV_HOST에 주소를 준다
// (예: VITE_DEV_HOST=0.0.0.0 npm run dev). 비우면 지금과 같다.
// 설정은 함수가 아닌 객체로 둔다: 개인 설정 파일이 mergeConfig(base, …)로 합칠 수 있게(2026-11-27).
// 화면 코드 타입에는 Node 타입이 없어 process만 여기서 알려 준다(설정 파일은 Node에서 돈다).
declare const process: { env: Record<string, string | undefined> };
const host = process.env.VITE_DEV_HOST?.trim() || "127.0.0.1";

export default defineConfig({
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
});
