import { configure } from "@testing-library/react";

// 바쁜 PC(품질 검사)에서 1초 기본값 때문에 깜빡 실패하지 않게, findBy·waitFor 기본 기다림을 3초로 둔다.
// 확인하는 내용은 그대로이고 기다리는 시간만 늘린다.
configure({ asyncUtilTimeout: 3000 });
