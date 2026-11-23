import { afterEach, expect, it, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider, useParams } from "react-router-dom";
import type { ReactNode } from "react";
import { createAppRouter } from "./router";

// Isolate the router boundary; screen and menu registration have their own tests.
vi.mock("../pages/common/CommonNavigationPage", () => ({
  CommonNavigationPage: () => {
    const { scrnId } = useParams();
    return <main data-testid="navigation-route">{scrnId ?? "공통 화면"}</main>;
  },
}));
vi.mock("../pages/userpreferences", () => ({
  UserPreferencesProvider: ({ children }: { children: ReactNode }) => (
    <div data-testid="preferences-route">{children}</div>
  ),
}));

let router: ReturnType<typeof createAppRouter> | undefined;
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  vi.restoreAllMocks();
});

function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}

it("passes UI-33 to the sole shared screen route without a candidate-specific route", async () => {
  mount("/screens/UI-33");
  expect(await screen.findByTestId("navigation-route")).toHaveTextContent(
    "UI-33",
  );
  expect(router?.state.matches.at(-1)?.route.path).toBe("/screens/:scrnId");
  expect(router?.state.matches.at(-1)?.params.scrnId).toBe("UI-33");
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getAllByTestId("preferences-route")).toHaveLength(1);
});

it("keeps the preference boundary mounted when navigating to and from UI-33", async () => {
  mount("/screens/UI-02");
  await screen.findByText("UI-02");
  const preferences = screen.getByTestId("preferences-route");
  for (const id of ["UI-33", "UI-17", "UI-02"]) {
    await act(async () => {
      await router!.navigate(`/screens/${id}`);
    });
    expect(screen.getByTestId("navigation-route")).toHaveTextContent(id);
    expect(screen.getByTestId("preferences-route")).toBe(preferences);
    expect(router?.state.matches.at(-1)?.route.path).toBe("/screens/:scrnId");
  }
});

it.each(["/", "/unknown", "/candidates"])(
  "%s preserves the existing root or fallback instead of adding another feature route",
  async (path) => {
    mount(path);
    expect(await screen.findByTestId("navigation-route")).toHaveTextContent(
      "공통 화면",
    );
    expect(router?.state.matches.at(-1)?.route.path).toBe(
      path === "/" ? "/" : "*",
    );
    expect(screen.getAllByRole("main")).toHaveLength(1);
  },
);
