import { createContext, useContext, useState, type ReactNode } from "react";

/** 제목줄(틀)과 사용자 화면 본문이 나눠 쓰는 상태: 등록 창 열기, 「사용 중지 포함」. */
const UserWorkspace = createContext({
  registering: false,
  setRegistering: (_open: boolean) => {},
  includeStopped: false,
  setIncludeStopped: (_value: boolean) => {},
});
export function UserWorkspaceProvider({ children }: { children: ReactNode }) {
  const [registering, setRegistering] = useState(false);
  const [includeStopped, setIncludeStopped] = useState(false);
  return (
    <UserWorkspace.Provider
      value={{ registering, setRegistering, includeStopped, setIncludeStopped }}
    >
      {children}
    </UserWorkspace.Provider>
  );
}
export const useUserWorkspace = () => useContext(UserWorkspace);

/** 시안 UI-23 제목줄 오른쪽: [사용 중지 포함] 체크 + [등록]. */
export function UserRegistrationButton() {
  const { setRegistering, includeStopped, setIncludeStopped } =
    useUserWorkspace();
  return (
    <>
      <label
        className="users-include-stopped"
        title="사용 중지된 계정은 기본으로 숨깁니다(상태 조건에서 중지를 고르면 함께 보임)"
      >
        <input
          type="checkbox"
          data-testid="user-include-stopped"
          checked={includeStopped}
          onChange={(event) => setIncludeStopped(event.target.checked)}
        />{" "}
        사용 중지 포함
      </label>
      <button
        type="button"
        className="primary-button users-register"
        data-testid="user-register-open"
        onClick={() => setRegistering(true)}
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
        등록
      </button>
    </>
  );
}
