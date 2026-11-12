import { createContext, useContext, useState, type ReactNode } from "react";

const UserWorkspace = createContext({
  registering: false,
  setRegistering: (_open: boolean) => {},
});
export function UserWorkspaceProvider({ children }: { children: ReactNode }) {
  const [registering, setRegistering] = useState(false);
  return (
    <UserWorkspace.Provider value={{ registering, setRegistering }}>
      {children}
    </UserWorkspace.Provider>
  );
}
export const useUserWorkspace = () => useContext(UserWorkspace);
export function UserRegistrationButton() {
  const { setRegistering } = useUserWorkspace();
  return (
    <button
      type="button"
      className="primary-button"
      data-testid="user-register-open"
      onClick={() => setRegistering(true)}
    >
      + 사용자 등록
    </button>
  );
}
