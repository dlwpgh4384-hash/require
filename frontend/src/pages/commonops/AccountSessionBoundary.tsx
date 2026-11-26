import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  AccountLoginPage,
  AccountMyInfo,
  AccountPasswordPage,
  AccountLogout,
  AccountIdentity,
} from "./AccountAccessPages";
import {
  createAccountAccessApi,
  firstAccountScreen,
  type AccountInfo,
  type AccountMeta,
  type AccountResponse,
} from "./accountAccessApi";
import { MfaPage } from "../mfa/MfaPage";
import { createMfaApi } from "../mfa/mfaApi";

interface SessionValue {
  info?: AccountInfo;
  meta: AccountMeta;
  refresh: () => Promise<void>;
}
const AccountSessionContext = createContext<SessionValue | undefined>(
  undefined,
);
export function useOptionalAccountSession() {
  return useContext(AccountSessionContext);
}
export function useAccountSession() {
  const value = useContext(AccountSessionContext);
  if (!value) throw new Error("AccountSessionBoundary가 필요합니다.");
  return value;
}
/** Wrap the existing router's authenticated shell, not a second router. */
export function AccountSessionBoundary({
  children,
  dirty = false,
  onAllowedScreensChanged,
}: {
  children: ReactNode;
  dirty?: boolean;
  onAllowedScreensChanged?: (screenIds: string[]) => void;
}) {
  const api = useMemo(() => createAccountAccessApi(), []);
  const mfa = useMemo(() => createMfaApi((...args) => fetch(...args)), []);
  const navigate = useNavigate();
  const location = useLocation();
  const [info, setInfo] = useState<AccountInfo>();
  const [meta, setMeta] = useState<AccountMeta>({});
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const loginPassword = useRef<string>();
  const sessionGeneration = useRef(0);
  const screenId = location.pathname.split("/").pop();
  const mobile =
    typeof window !== "undefined" &&
    window.matchMedia?.("(max-width: 767px)").matches;
  const first = firstAccountScreen(meta.permissions ?? [], Boolean(mobile));
  function reset(text = "") {
    sessionGeneration.current += 1;
    loginPassword.current = undefined;
    setInfo(undefined);
    setMeta({});
    setLoading(false);
    setNotice(text);
    navigate("/screens/UI-22", { replace: true });
  }
  async function refresh() {
    const generation = sessionGeneration.current;
    const response = await api.myInfo();
    if (generation !== sessionGeneration.current) return;
    setInfo(response.data);
    setMeta(response.meta);
    onAllowedScreensChanged?.(
      (response.meta.permissions ?? [])
        .filter((item) => item.readYn === "Y")
        .map((item) => item.screenId),
    );
  }
  useEffect(() => {
    let active = true;
    const generation = sessionGeneration.current;
    if (["/screens/UI-22", "/screens/UI-39"].includes(location.pathname)) {
      setLoading(false);
      return;
    }
    api
      .myInfo()
      .then((response) => {
        if (active && generation === sessionGeneration.current) {
          setInfo(response.data);
          setMeta(response.meta);
        }
      })
      .catch(() => {
        if (active && generation === sessionGeneration.current)
          reset("다시 로그인해 주세요.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // Initial session recovery only. Forced-password metadata lives in this owner until logout.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api]);
  useEffect(() => {
    const expired = () => reset("다시 로그인해 주세요.");
    const changed = () => {
      void refresh().catch(() => reset("다시 로그인해 주세요."));
    };
    window.addEventListener("account-session-expired", expired);
    window.addEventListener("account-permissions-changed", changed);
    return () => {
      window.removeEventListener("account-session-expired", expired);
      window.removeEventListener("account-permissions-changed", changed);
    };
  });
  async function completed(
    response: AccountResponse<{ nextScreenId: string }>,
  ) {
    const generation = sessionGeneration.current;
    setMeta(response.meta);
    setNotice("");
    if (["UI-39", "UI-40"].includes(response.data.nextScreenId)) {
      navigate(`/screens/${response.data.nextScreenId}`, { replace: true });
      return;
    }
    const identity = await api.myInfo();
    if (generation !== sessionGeneration.current) return;
    loginPassword.current = undefined;
    setInfo(identity.data);
    setMeta(identity.meta);
    const target = firstAccountScreen(
      identity.meta.permissions ?? [],
      Boolean(mobile),
    );
    setNotice(
      `${identity.data.name}(${identity.data.roles[0] ?? "역할 없음"}${identity.data.roles.length > 1 ? ` 외 ${identity.data.roles.length - 1}` : ""})으로 로그인했습니다.`,
    );
    navigate(`/screens/${target.screenId}`, { replace: true });
  }
  const restricted = meta.passwordChangeRequired;
  const special = ["UI-34", "UI-39", "UI-40", "UI-41", "UI-42"].includes(
    screenId ?? "",
  );
  const forbidden = Boolean(
    info &&
      !special &&
      !(meta.permissions ?? []).some(
        (item) =>
          item.screenId === screenId &&
          item.readYn === "Y" &&
          (!mobile || item.pcOnlyYn !== "Y"),
      ),
  );
  useEffect(() => {
    if (forbidden) {
      setNotice("볼 권한이 없습니다.");
      navigate(`/screens/${first.screenId}`, { replace: true });
    } else if (restricted && screenId !== "UI-40")
      navigate("/screens/UI-40", { replace: true });
  }, [forbidden, restricted, screenId, first.screenId, navigate]);
  if (screenId === "UI-22")
    return (
      <>
        {notice && <p role="status">{notice}</p>}
        <AccountLoginPage
          api={api}
          onCompleted={(response, currentPassword) => {
            loginPassword.current = currentPassword;
            void completed(response).catch(() =>
              reset("다시 로그인해 주세요."),
            );
          }}
        />
      </>
    );
  if (screenId === "UI-39")
    return (
      <MfaPage
        api={mfa}
        initial={meta.mfaDelivery}
        deliveryError={meta.deliveryError}
        onCompleted={(response) =>
          void completed(response).catch(() => reset("다시 로그인해 주세요."))
        }
        onRestart={() => reset()}
      />
    );
  if (loading) return <p role="status">불러오는 중…</p>;
  if (screenId === "UI-40" || restricted)
    return (
      <AccountPasswordPage
        api={api}
        meta={meta}
        currentPassword={restricted ? loginPassword.current : undefined}
        onCompleted={(updated) =>
          void completed({
            success: true,
            data: { nextScreenId: "UI-34" },
            meta: updated,
          })
        }
        onBack={async () => {
          if (restricted) {
            await api.logout();
            reset();
          } else navigate(`/screens/${first.screenId}`);
        }}
      />
    );
  if (!info || forbidden) return null;
  if (first.message)
    return (
      <div data-testid="account-empty-shell" data-screen-id="UI-34">
        <p role="status">{first.message}</p>
        <AccountLogout api={api} dirty={dirty} onLoggedOut={() => reset()} />
      </div>
    );
  return (
    <AccountSessionContext.Provider value={{ info, meta, refresh }}>
      <div data-testid="account-authenticated-shell" data-screen-id="UI-34">
        {notice && <p role="status">{notice}</p>}
        <div data-testid="account-tools">
          <AccountIdentity info={info} />
          <button
            data-testid="account-open-info"
            onClick={() => navigate("/screens/UI-41")}
          >
            내 정보
          </button>
          <button
            data-testid="account-open-password"
            onClick={() => navigate("/screens/UI-40")}
          >
            비밀번호 변경
          </button>
          <AccountLogout api={api} dirty={dirty} onLoggedOut={() => reset()} />
        </div>
        {first.message ? (
          <p role="status">{first.message}</p>
        ) : screenId === "UI-41" ? (
          <AccountMyInfo
            api={api}
            onClose={() => navigate(`/screens/${first.screenId}`)}
          />
        ) : (
          children
        )}
      </div>
    </AccountSessionContext.Provider>
  );
}
