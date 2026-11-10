import type { MenuItem, ScreenState } from "../services/menus";
import { StateNotice } from "../components/StateNotice";
import { InterfaceIcon } from "../components/InterfaceIcon";

interface OutletProps {
  status: "loading" | "error" | "success";
  selected: string | null | undefined;
  state?: ScreenState;
  mobile: boolean;
  first?: MenuItem;
  onFirst: () => void;
}
export function ScreenOutlet({
  status,
  selected,
  state,
  mobile,
  first,
  onFirst,
}: OutletProps) {
  if (status === "loading")
    return (
      <StateNotice
        icon="clock"
        tone="loading"
        title="메뉴를 불러오는 중입니다"
        description="메뉴와 화면 정보를 확인하고 있습니다."
        testId="screen-loading"
      />
    );
  if (status === "error")
    return (
      <StateNotice
        icon="alert"
        tone="error"
        title="메뉴를 확인한 뒤 화면을 표시합니다"
        description="업무 메뉴의 ‘다시 시도’를 눌러 메뉴를 다시 불러와 주세요."
        testId="screen-menu-error"
      />
    );
  if (selected === undefined)
    return (
      <StateNotice
        icon="empty"
        title="표시할 화면이 없습니다"
        description="현재 표시할 수 있는 화면이 메뉴에 등록되어 있지 않습니다."
        testId="screen-empty"
      />
    );
  if (!state)
    return (
      <StateNotice
        icon="search"
        title="없는 화면입니다"
        testId="screen-not-found"
        description="주소를 확인하거나 첫 화면으로 이동해 주세요."
      >
        <button
          className="primary"
          disabled={!first}
          onClick={onFirst}
          data-testid="first-screen"
        >
          첫 화면으로
          <InterfaceIcon name="arrow" />
        </button>
      </StateNotice>
    );
  if (!state.available)
    return (
      <StateNotice
        icon="unavailable"
        title="사용할 수 없는 화면입니다"
        description="다른 화면은 업무 메뉴에서 선택할 수 있습니다."
        testId="screen-unavailable"
      />
    );
  if (mobile && state.pcOnlyYn === "Y")
    return (
      <StateNotice
        icon="monitor"
        title="PC 전용 화면입니다"
        description="넓은 PC 화면에서 확인해 주세요. 화면 폭을 늘리면 선택한 화면이 다시 표시됩니다."
        testId="screen-pc-only"
      />
    );
  return (
    <section
      className="screen-content"
      aria-label={selected ?? undefined}
      data-testid="screen-content"
    >
      <div className="titlebar">
        <div>
          <span className="eyebrow">선택한 업무 화면</span>
          <h1>{state.menuNm}</h1>
          <p className="page-description">
            업무 메뉴에서 선택한 화면의 안내를 확인하세요.
          </p>
        </div>
        <span className="screen-code">{selected}</span>
      </div>
      <StateNotice
        icon="clock"
        title="준비 중"
        description="이 화면의 업무 기능은 아직 제공되지 않습니다. 다른 화면은 업무 메뉴에서 선택해 주세요."
      />
    </section>
  );
}
