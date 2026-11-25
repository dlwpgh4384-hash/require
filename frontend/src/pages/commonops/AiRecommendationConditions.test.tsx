import { StrictMode, useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  AiRecommendationConditions,
  AiRecommendationOptionsPanel,
  type AiRecommendationDraft,
} from "./AiRecommendationConditions";
import {
  aiRecommendationOptionsError,
  getCommonAiRecommendationOptions,
  type AiRecommendationOptions,
} from "./aiRecommendationOptions";

function options(): AiRecommendationOptions {
  return {
    targets: [
      {
        targetType: "project",
        targetId: "selected",
        targetName: "진행 프로젝트",
        startMonth: "2026-12",
        endMonth: "2027-06",
        needs: [],
      },
      {
        targetType: "opportunity",
        targetId: "selected",
        targetName: "진행 기회",
        startMonth: "2026-12",
        endMonth: null,
        needs: [
          {
            needSn: 2,
            jobCode: "BE",
            jobName: "백엔드",
            requiredCount: 3,
            startMonth: "2026-12",
          },
          {
            needSn: 9,
            jobCode: "FE",
            jobName: "프론트엔드",
            requiredCount: 1,
            startMonth: "2027-01",
          },
        ],
      },
      {
        targetType: "opportunity",
        targetId: "no-needs",
        targetName: "신규 기회",
        startMonth: "2027-01",
        endMonth: null,
        needs: [],
      },
    ],
    jobs: [
      { code: "BE", name: "백엔드" },
      { code: "FE", name: "프론트엔드" },
      { code: "PM", name: "PM" },
    ],
    grades: [
      { code: "MD", name: "중급" },
      { code: "SR", name: "고급" },
    ],
    exclusionReasons: [{ code: "SCHEDULE", name: "일정이 맞지 않음" }],
  };
}
const initial: AiRecommendationDraft = {
  targetType: "project",
  targetId: "selected",
  jobCode: "BE",
  startMonth: "2026-12",
  gradeCode: "SR",
};
function response(data = options()) {
  return {
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  };
}
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  request = vi.fn(async () => response());
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function Harness({
  remote = false,
  start = null,
  onChange = vi.fn(),
  onRecommend = vi.fn(),
}: {
  remote?: boolean;
  start?: AiRecommendationDraft | null;
  onChange?: ReturnType<typeof vi.fn>;
  onRecommend?: ReturnType<typeof vi.fn>;
}) {
  const [value, setValue] = useState<AiRecommendationDraft | null>(start);
  const Component = remote
    ? AiRecommendationOptionsPanel
    : AiRecommendationConditions;
  return (
    <Component
      options={options()}
      value={value}
      onRecommend={onRecommend}
      onChange={(next, source) => {
        setValue(next);
        onChange(next, source);
      }}
    />
  );
}
function chooseTarget(type: "project" | "opportunity", id = "selected") {
  fireEvent.change(screen.getByTestId("ai-condition-target"), {
    target: { value: JSON.stringify([type, id]) },
  });
}

describe("AI 추천 조건", () => {
  it("첫 진행 대상과 DB 고급 옵션으로 시작하고 프로젝트에는 자리를 보이지 않는다", async () => {
    const changed = vi.fn();
    render(
      <StrictMode>
        <Harness onChange={changed} />
      </StrictMode>,
    );
    expect(screen.getByLabelText(/프로젝트·영업 기회/)).toHaveValue(
      JSON.stringify(["project", "selected"]),
    );
    expect(screen.getByLabelText(/등급/)).toHaveValue("SR");
    expect(screen.queryByLabelText("가배정 자리")).not.toBeInTheDocument();
    expect(screen.getByTestId("ai-condition-recommend")).toBeEnabled();
    await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
    expect(changed).toHaveBeenCalledWith(initial, "initial");
    expect(request).not.toHaveBeenCalled();
  });

  it("대상 변경을 부모에게 별도 사건으로 알리고 같은 ID의 프로젝트와 기회를 구별한다", () => {
    const changed = vi.fn();
    render(<Harness start={initial} onChange={changed} />);
    chooseTarget("opportunity");
    expect(changed).toHaveBeenLastCalledWith(
      { ...initial, targetType: "opportunity", needSn: 2 },
      "target",
    );
    expect(screen.getByLabelText("가배정 자리")).toHaveValue("2");
    expect(
      within(screen.getByLabelText("가배정 자리")).getByRole("option", {
        name: "2번 · 백엔드 · 3명 · 2026-12",
      }),
    ).toBeInTheDocument();
  });

  it("직무 변경은 같은 직무 줄 또는 첫 줄을 고르며 초안일 뿐 추천하지 않는다", () => {
    const changed = vi.fn(),
      recommend = vi.fn();
    render(
      <Harness
        start={{ ...initial, targetType: "opportunity", needSn: 2 }}
        onChange={changed}
        onRecommend={recommend}
      />,
    );
    fireEvent.change(screen.getByLabelText(/직무/), {
      target: { value: "FE" },
    });
    expect(screen.getByLabelText("가배정 자리")).toHaveValue("9");
    expect(changed).toHaveBeenLastCalledWith(
      { ...initial, targetType: "opportunity", needSn: 9, jobCode: "FE" },
      "draft",
    );
    fireEvent.change(screen.getByLabelText(/직무/), {
      target: { value: "PM" },
    });
    expect(screen.getByLabelText("가배정 자리")).toHaveValue("2");
    expect(recommend).not.toHaveBeenCalled();
    expect(request).not.toHaveBeenCalled();
  });

  it("사용자 자리 선택은 현재 초안으로 전달하며 추천 버튼을 누를 때만 실행 콜백을 부른다", () => {
    const recommend = vi.fn();
    render(
      <Harness
        start={{ ...initial, targetType: "opportunity", needSn: 2 }}
        onRecommend={recommend}
      />,
    );
    fireEvent.change(screen.getByLabelText("가배정 자리"), {
      target: { value: "9" },
    });
    fireEvent.change(screen.getByLabelText(/등급/), {
      target: { value: "MD" },
    });
    expect(recommend).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "추천 받기" }));
    expect(recommend).toHaveBeenCalledTimes(1);
    expect(recommend).toHaveBeenCalledWith({
      ...initial,
      targetType: "opportunity",
      needSn: 9,
      gradeCode: "MD",
    });
  });

  it("프로젝트 시작 전 월은 보정하고 초안 변경으로 자동 추천하지 않는다", () => {
    const changed = vi.fn(),
      recommend = vi.fn();
    render(
      <Harness start={initial} onChange={changed} onRecommend={recommend} />,
    );
    fireEvent.change(screen.getByLabelText(/시작월/), {
      target: { value: "2026-10" },
    });
    expect(changed).toHaveBeenLastCalledWith(initial, "draft");
    expect(screen.getByLabelText(/시작월/)).toHaveValue("2026-12");
    expect(recommend).not.toHaveBeenCalled();
  });

  it("프로젝트 종료월 뒤에는 지정 오류와 비활성 버튼을 보인다", () => {
    render(<Harness start={initial} />);
    fireEvent.change(screen.getByLabelText(/시작월/), {
      target: { value: "2027-07" },
    });
    expect(
      screen.getByText("시작월이 프로젝트 종료월 뒤입니다."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/시작월/)).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByRole("button", { name: "추천 받기" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/시작월/), {
      target: { value: "2027-06" },
    });
    expect(screen.getByRole("button", { name: "추천 받기" })).toBeEnabled();
  });

  it("필요 인력 없는 기회는 가짜 자리를 만들지 않고 추천을 허용한다", () => {
    const recommend = vi.fn(),
      changed = vi.fn();
    render(
      <Harness start={initial} onRecommend={recommend} onChange={changed} />,
    );
    chooseTarget("opportunity", "no-needs");
    expect(
      screen.getByText("필요 인력을 먼저 등록해 주세요"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("가배정 자리")).toBeDisabled();
    expect(screen.getByRole("button", { name: "추천 받기" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "추천 받기" }));
    expect(recommend.mock.calls[0][0]).not.toHaveProperty("needSn");
    fireEvent.click(screen.getByRole("button", { name: "가배정 자리 설명" }));
    expect(
      screen.getByText(/필요 인력이 없어도 추천할 수 있고 가배정만/),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "가배정 자리 설명" }));
    expect(
      screen.queryByText(/필요 인력이 없어도 추천할 수 있고 가배정만/),
    ).not.toBeInTheDocument();
  });

  it("빈 대상은 지정 빈문구만 보이고 합성 대상이나 추천 버튼을 만들지 않는다", () => {
    render(
      <AiRecommendationConditions
        options={{ ...options(), targets: [] }}
        value={null}
        onChange={vi.fn()}
        onRecommend={vi.fn()}
      />,
    );
    expect(screen.getByText("진행 중인 대상이 없습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "추천 받기" }),
    ).not.toBeInTheDocument();
  });

  it("요청 중 잠금과 서버의 필드 오류를 그대로 표시한다", () => {
    render(
      <AiRecommendationConditions
        options={options()}
        value={{ ...initial, targetType: "opportunity", needSn: 2 }}
        onChange={vi.fn()}
        onRecommend={vi.fn()}
        disabled
        errors={{ needSn: "필요 인력 줄을 다시 골라 주세요." }}
      />,
    );
    expect(screen.getByLabelText("가배정 자리")).toBeDisabled();
    expect(screen.getByLabelText("가배정 자리")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(
      screen.getByText("필요 인력 줄을 다시 골라 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByTestId("ai-condition-target")).toBeDisabled();
    expect(screen.getByTestId("ai-condition-recommend")).toBeDisabled();
  });

  it("옵션이 없는 직무·등급을 만들어 내지 않고 실행을 막는다", () => {
    render(
      <AiRecommendationConditions
        options={{ ...options(), jobs: [], grades: [] }}
        value={null}
        onChange={vi.fn()}
        onRecommend={vi.fn()}
      />,
    );
    expect(screen.getByText("선택할 직무가 없습니다.")).toBeInTheDocument();
    expect(screen.getByText("선택할 등급이 없습니다.")).toBeInTheDocument();
    expect(screen.getByTestId("ai-condition-recommend")).toBeDisabled();
  });

  it("열린 컴포넌트는 부모의 선택을 유지하고 닫았다 다시 열면 첫 대상으로 초기화한다", () => {
    const view = render(<Harness />);
    chooseTarget("opportunity");
    view.rerender(<Harness />);
    expect(screen.getByLabelText("가배정 자리")).toHaveValue("2");
    view.unmount();
    render(<Harness />);
    expect(screen.queryByLabelText("가배정 자리")).not.toBeInTheDocument();
    expect(screen.getByTestId("ai-condition-target")).toHaveValue(
      JSON.stringify(["project", "selected"]),
    );
  });
});

describe("AI 추천 옵션 조회", () => {
  it("실제 상대 GET 결과를 로딩 뒤 표시한다", async () => {
    render(<Harness remote />);
    expect(
      screen.getByTestId("ai-recommendation-options-loading"),
    ).toHaveTextContent("추천 조건을 불러오는 중입니다.");
    expect(
      await screen.findByTestId("ai-condition-target"),
    ).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(
      "/api/common/ai-recommendation-options",
      { signal: expect.any(AbortSignal) },
    );
  });

  it("API 필터만 URL 인코딩하고 브라우저 주소는 바꾸지 않는다", async () => {
    const location = window.location.href;
    await getCommonAiRecommendationOptions({
      targetType: "opportunity",
      targetId: "selected&other=value",
    });
    expect(request).toHaveBeenCalledWith(
      "/api/common/ai-recommendation-options?targetType=opportunity&targetId=selected%26other%3Dvalue",
      { signal: undefined },
    );
    expect(window.location.href).toBe(location);
  });

  it("실패는 안전한 오류를 보이고 재시도 뒤 정상 옵션을 복구한다", async () => {
    request.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({ message: "private SQL", success: false }),
    });
    render(<Harness remote />);
    expect(
      await screen.findByTestId("ai-recommendation-options-error"),
    ).toHaveTextContent(aiRecommendationOptionsError);
    expect(screen.queryByText(/private SQL/)).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
    expect(
      await screen.findByTestId("ai-condition-target"),
    ).toBeInTheDocument();
    expect(request).toHaveBeenCalledTimes(2);
  });

  it.each([
    { success: false, data: options() },
    { success: true, data: { ...options(), targets: null } },
    {
      success: true,
      data: {
        ...options(),
        targets: [{ ...options().targets[0], endMonth: "2026-99" }],
      },
    },
    { success: true, data: { ...options(), jobs: [{ code: "BE" }] } },
    {
      success: true,
      data: {
        ...options(),
        targets: [{ ...options().targets[1], needs: [{ needSn: "2" }] }],
      },
    },
  ])(
    "계약을 어긴 성공 응답에 가짜 옵션으로 대체하지 않는다 %#",
    async (body) => {
      request.mockResolvedValueOnce({ ok: true, json: async () => body });
      render(<Harness remote />);
      expect(
        await screen.findByTestId("ai-recommendation-options-error"),
      ).toHaveTextContent(aiRecommendationOptionsError);
      expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    },
  );

  it("HTML과 네트워크 오류는 안전한 오류로 바꾼다", async () => {
    request.mockResolvedValueOnce({
      ok: true,
      json: async () => {
        throw new Error("private HTML");
      },
    });
    await expect(getCommonAiRecommendationOptions()).rejects.toThrow(
      aiRecommendationOptionsError,
    );
    request.mockRejectedValueOnce(new Error("private connection"));
    await expect(getCommonAiRecommendationOptions()).rejects.toThrow(
      aiRecommendationOptionsError,
    );
  });

  it("닫힌 화면의 요청을 취소하고 뒤늦은 응답을 적용하지 않는다", async () => {
    let resolve!: (value: ReturnType<typeof response>) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = render(<Harness remote />);
    await waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    const signal = request.mock.calls[0][1].signal as AbortSignal;
    view.unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => {
      resolve(response());
    });
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });
});
