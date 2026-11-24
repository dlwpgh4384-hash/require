import { configure } from "@testing-library/react";

// 바쁜 PC(품질 검사)에서 1초 기본값 때문에 깜빡 실패하지 않게, findBy·waitFor 기본 기다림을 3초로 둔다.
// 확인하는 내용은 그대로이고 기다리는 시간만 늘린다.
configure({ asyncUtilTimeout: 3000 });

// jsdom에는 URL.revokeObjectURL이 없다. 엑셀 내려받기는 1초 뒤에 정리하는데, 그때는 시험이 바꿔 둔 URL이
// 이미 원래대로 돌아와 「not a function」 오류가 시험 밖에서 난다. 시험 환경에만 빈 함수를 둔다.
if (typeof URL.revokeObjectURL !== "function") URL.revokeObjectURL = () => {};
