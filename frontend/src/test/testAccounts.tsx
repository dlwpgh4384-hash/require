import type { ReactNode } from "react";
import { AccountSessionValueProvider } from "../pages/commonops/AccountSessionBoundary";
import type { TestAccount } from "./testAccountData";
export * from "./testAccountData";

/** 로그인 틀 없이 그린 화면 단위 시험에 계정 권한을 넣는다. */
export function WithTestAccount({
  account,
  children,
}: {
  account: TestAccount;
  children: ReactNode;
}) {
  return (
    <AccountSessionValueProvider
      value={{
        info: account.info,
        meta: account.meta,
        refresh: async () => {},
      }}
    >
      {children}
    </AccountSessionValueProvider>
  );
}
