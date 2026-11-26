import { cleanup, render, screen, waitFor, act } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, expect, it, vi } from "vitest";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { useSavedYear } from "../userpreferences/useSavedYear";
import {
  getCommonUserPreference,
  type UserPreferenceResponse,
} from "../userpreferences/userPreferences";
import {
  currentScreenPeriod,
  screenPeriodChannel,
} from "../../shared/layout/screenPeriodChannel";
vi.mock("../userpreferences/userPreferences", async (original) => ({
  ...(await original<typeof import("../userpreferences/userPreferences")>()),
  getCommonUserPreference: vi.fn(),
}));
const puts: { url: string; body: string }[] = [];
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  puts.length = 0;
});
/** 저장(PUT)은 실제 저장 함수를 지나 fetch로 나간다. 보낸 값만 기록한다. */
function stubSave() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      if (init?.method === "PUT") puts.push({ url, body: String(init.body) });
      return {
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          meta: {},
          data: {
            prefTypeCd: "PERIOD",
            prefKey: url.split("/").at(-1),
            found: true,
            prefVal: null,
            modDttm: "2026-11-26T00:00:00Z",
          },
        }),
      };
    }),
  );
}
const options = { kind: "yearMonth", firstDataYear: 2026 } as const;
const response: UserPreferenceResponse = {
  success: true,
  meta: {
    periodBasis: { latestYm: "202611", cashBaseYm: "202601", closedMonths: [] },
  },
  data: {
    prefTypeCd: "PERIOD",
    prefKey: "UI-08",
    found: true,
    prefVal: '{"year":2026,"month":9}',
    modDttm: "2026-11-25T00:00:00Z",
  },
};
function Source() {
  const period = useScreenPeriod("UI-08", options);
  return (
    <p>
      {period.state.status === "ready"
        ? `${period.state.value.year}-${period.state.value.month}`
        : period.state.status}
    </p>
  );
}
it("publishes the actual active source filter, and a requested period updates an open tab and is saved", async () => {
  stubSave();
  vi.mocked(getCommonUserPreference).mockResolvedValue(response);
  render(<Source />);
  await screen.findByText("2026-9");
  expect(currentScreenPeriod("UI-08")).toEqual({
    startMonth: "2026-09",
    endMonth: "2026-09",
  });
  act(() =>
    screenPeriodChannel("UI-08").request({
      startMonth: "2026-06",
      endMonth: "2026-06",
    }),
  );
  await screen.findByText("2026-6");
  expect(currentScreenPeriod("UI-08")).toEqual({
    startMonth: "2026-06",
    endMonth: "2026-06",
  });
  await waitFor(() =>
    expect(puts.map((put) => JSON.parse(put.body).prefVal)).toEqual([
      '{"year":2026,"month":6}',
    ]),
  );
  expect(puts[0].url).toContain("UI-08");
});
function SavedYear({ screenId }: { screenId: string }) {
  const [year] = useSavedYear(screenId, 2026, 1, 2027);
  return <p>{`${screenId}:${year}`}</p>;
}
it.each(["UI-05", "UI-13"])(
  "%s takes the requested year from an answer action, saves it and clears the request",
  async (screenId) => {
    stubSave();
    vi.mocked(getCommonUserPreference).mockResolvedValue({
      ...response,
      data: { ...response.data, prefKey: screenId, prefVal: null },
    });
    // 아직 안 열린 화면: 요청을 남겨 두었다가 열릴 때 받는다.
    screenPeriodChannel(screenId).request({
      startMonth: "2025-01",
      endMonth: "2025-12",
    });
    const first = render(<SavedYear screenId={screenId} />);
    expect(await screen.findByText(`${screenId}:2025`)).toBeInTheDocument();
    await waitFor(() =>
      expect(puts.map((put) => JSON.parse(put.body).prefVal)).toEqual([
        '{"year":2025}',
      ]),
    );
    first.unmount();
    // 한 번 쓴 요청은 비워져 다시 열어도 또 받지 않는다.
    render(<SavedYear screenId={screenId} />);
    expect(await screen.findByText(`${screenId}:2026`)).toBeInTheDocument();
    // 이미 열린 탭도 바로 바뀐다.
    act(() =>
      screenPeriodChannel(screenId).request({
        startMonth: "2027-03",
        endMonth: "2027-03",
      }),
    );
    expect(await screen.findByText(`${screenId}:2027`)).toBeInTheDocument();
  },
);
it("year-only screens publish their year as the period and every screen clears it when it closes", async () => {
  stubSave();
  vi.mocked(getCommonUserPreference).mockResolvedValue(response);
  const year = render(<SavedYear screenId="UI-05" />);
  await screen.findByText("UI-05:2026");
  expect(currentScreenPeriod("UI-05")).toEqual({
    startMonth: "2026-01",
    endMonth: "2026-12",
  });
  year.unmount();
  expect(currentScreenPeriod("UI-05")).toBeUndefined();
  const month = render(<Source />);
  await screen.findByText("2026-9");
  expect(currentScreenPeriod("UI-08")).toBeDefined();
  month.unmount();
  expect(currentScreenPeriod("UI-08")).toBeUndefined();
});
function Silent() {
  const period = useScreenPeriod("UI-28", { kind: "year" }, undefined, {
    publish: false,
  });
  return <p>{period.state.status}</p>;
}
it("a screen that publishes its own period is the only writer", async () => {
  stubSave();
  vi.mocked(getCommonUserPreference).mockResolvedValue({
    ...response,
    data: { ...response.data, prefKey: "UI-28", prefVal: '{"year":2026}' },
  });
  render(<Silent />);
  await screen.findByText("ready");
  expect(currentScreenPeriod("UI-28")).toBeUndefined();
});
it("does not lose an answer action that arrives before preferences finish loading", async () => {
  stubSave();
  let finish!: (value: UserPreferenceResponse) => void;
  vi.mocked(getCommonUserPreference).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  screenPeriodChannel("UI-08").request({
    startMonth: "2026-07",
    endMonth: "2026-07",
  });
  render(<Source />);
  await act(async () => {
    finish(response);
  });
  await screen.findByText("2026-7");
  await waitFor(() =>
    expect(currentScreenPeriod("UI-08")).toEqual({
      startMonth: "2026-07",
      endMonth: "2026-07",
    }),
  );
});
