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
  AccountPasswordDialog,
  AccountPasswordPage,
  AccountLogout,
} from "./AccountAccessPages";
import {
  createAccountAccessApi,
  firstAccountScreen,
  forbiddenScreenMessage,
  loginNotice,
  NO_ACCOUNT_MENUS,
  NO_ACCOUNT_MENUS_TITLE,
  PASSWORD_CHANGED_MESSAGE,
  SESSION_EXPIRED_MESSAGE,
  type AccountAccessApi,
  type AccountInfo,
  type AccountMeta,
  type AccountResponse,
  type LoginResult,
} from "./accountAccessApi";
import { MfaPage } from "../mfa/MfaPage";
import { createMfaApi } from "../mfa/mfaApi";
import { Toast } from "../../shared/feedback/Dialog";

interface SessionValue {
  info?: AccountInfo;
  meta: AccountMeta;
  refresh: () => Promise<void>;
  /** 헤더 사용자 메뉴(내 정보·비밀번호 변경·로그아웃)가 쓰는 계정 API. */
  api?: AccountAccessApi;
  /** 로그아웃이 끝난 뒤 로그인 화면으로 돌린다. */
  signOut?: () => void;
}
const AccountSessionContext = createContext<SessionValue | undefined>(
  undefined,
);
/** 로그인 틀 없이 그린 화면에 계정 값을 넣는 자리(화면 단위 시험용). */
export const AccountSessionValueProvider = AccountSessionContext.Provider;
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
  // notice: 토스트(로그인 완료·세션 만료), loginError: 로그인 카드 비밀번호 칸 아래 문구(시안).
  const [notice, setNotice] = useState("");
  const [loginError, setLoginError] = useState("");
  const [forbiddenNotice, setForbiddenNotice] = useState("");
  const loginPassword = useRef<string>();
  // 로그인 결과의 사용자 이름(첫 로그인 안내·로그인 토스트, 시안).
  const loginName = useRef<string>();
  const passwordChanged = useRef(false);
  const sessionGeneration = useRef(0);
  // 이 탭에서 실제로 로그인된 적이 있는지. 처음 열 때(세션 없음) 내 정보 401은 만료가 아니다.
  const signedIn = useRef(false);
  const screenId = location.pathname.split("/").pop();
  const mobile =
    typeof window !== "undefined" &&
    window.matchMedia?.("(max-width: 767px)").matches;
  const first = firstAccountScreen(meta.permissions ?? [], Boolean(mobile));
  function reset(text = "", error = "") {
    sessionGeneration.current += 1;
    signedIn.current = false;
    loginPassword.current = undefined;
    loginName.current = undefined;
    passwordChanged.current = false;
    setInfo(undefined);
    setMeta({});
    setLoading(false);
    setNotice(text);
    setLoginError(error);
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
          signedIn.current = true;
          setInfo(response.data);
          setMeta(response.meta);
        }
      })
      .catch(() => {
        // 처음 열 때 로그인 안 된 상태면 안내 없이 로그인 카드만(시안).
        if (active && generation === sessionGeneration.current) reset();
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
    // 세션이 끝나면 한 가지 문구로 토스트(시안: 「30분 동안 사용하지 않아 로그아웃되었습니다」).
    // 로그인된 적 없는 탭(처음 방문)의 401은 만료 토스트 없이 로그인 카드만 둔다.
    const expired = () => {
      if (signedIn.current) reset(SESSION_EXPIRED_MESSAGE);
      else if (screenId !== "UI-22") reset();
    };
    const changed = () => {
      void refresh().catch(() =>
        reset(signedIn.current ? SESSION_EXPIRED_MESSAGE : ""),
      );
    };
    window.addEventListener("account-session-expired", expired);
    window.addEventListener("account-permissions-changed", changed);
    return () => {
      window.removeEventListener("account-session-expired", expired);
      window.removeEventListener("account-permissions-changed", changed);
    };
  });
  async function completed(response: AccountResponse<LoginResult>) {
    const generation = sessionGeneration.current;
    signedIn.current = true;
    if (response.data.userName) loginName.current = response.data.userName;
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
    // 시안 loginDone·첫 비밀번호 변경: 토스트 「이름(역할)으로 로그인했습니다.」
    setNotice(
      `${passwordChanged.current ? "비밀번호를 바꿨습니다. " : ""}${loginNotice(identity.data, loginName.current)}`,
    );
    passwordChanged.current = false;
    loginName.current = undefined;
    navigate(`/screens/${target.screenId}`, { replace: true });
  }
  const failedLogin = () => reset("", "다시 로그인해 주세요.");
  const restricted = meta.passwordChangeRequired;
  const special = ["UI-34", "UI-39", "UI-40", "UI-41", "UI-42"].includes(
    screenId ?? "",
  );
  // 권한 검사는 화면 주소(/screens/화면 ID)에만 건다. 첫 주소(/)·없는 주소는 화면 틀이 첫 화면·「없는 화면」으로 보낸다.
  const screenRoute = /^\/screens\/[^/]+$/.test(location.pathname);
  // 권한표에 있는 화면인데 조회 권한이 없을 때만 막는다(시안 go(): META[id] && !canSee(id)).
  // 권한표에 없는 주소는 화면 틀의 「없는 화면」, 휴대폰의 PC 전용 화면은 화면 틀의 PC 전용 안내가 맡는다.
  const rows = (meta.permissions ?? []).filter(
    (item) => item.screenId === screenId,
  );
  const forbidden = Boolean(
    info &&
      screenRoute &&
      !special &&
      rows.length > 0 &&
      !rows.some((item) => item.readYn === "Y"),
  );
  useEffect(() => {
    if (forbidden) {
      setForbiddenNotice(forbiddenScreenMessage(first.screenId));
      navigate(`/screens/${first.screenId}`, { replace: true });
    } else if (restricted && screenId !== "UI-40")
      navigate("/screens/UI-40", { replace: true });
  }, [forbidden, restricted, screenId, first.screenId, navigate]);
  const noticeToast = notice && (
    <Toast
      message={notice}
      kind={notice === SESSION_EXPIRED_MESSAGE ? "info" : "ok"}
      testId="account-login-message"
      onDismiss={() => setNotice("")}
    />
  );
  if (screenId === "UI-22")
    return (
      <>
        {noticeToast}
        <AccountLoginPage
          key={`${sessionGeneration.current}:${loginError}`}
          api={api}
          initialError={loginError}
          onCompleted={(response, currentPassword) => {
            loginPassword.current = currentPassword;
            setLoginError("");
            void completed(response).catch(failedLogin);
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
        onCompleted={(response) => void completed(response).catch(failedLogin)}
        // 인증번호 5번 틀림 등 서버가 처음으로 돌리면 그 문구를 로그인 카드 비밀번호 칸 아래에(시안).
        onRestart={(text) => reset("", text ?? "")}
      />
    );
  if (loading) return <p role="status">불러오는 중…</p>;
  if (restricted)
    return (
      <AccountPasswordPage
        api={api}
        meta={meta}
        currentPassword={loginPassword.current}
        name={loginName.current ?? info?.name}
        onCompleted={(updated) => {
          passwordChanged.current = true;
          void completed({
            success: true,
            data: {
              nextScreenId: "UI-34",
              userName: loginName.current ?? info?.name ?? "",
            },
            meta: updated,
          }).catch(failedLogin);
        }}
        onBack={async () => {
          await api.logout().catch(() => undefined);
          reset();
        }}
      />
    );
  if (!info || forbidden) return null;
  const signOut = () => reset();
  if (first.message)
    return (
      <div data-testid="account-empty-shell" data-screen-id="UI-34">
        {first.message === NO_ACCOUNT_MENUS ? (
          <div className="account-empty" role="status">
            <h2>{NO_ACCOUNT_MENUS_TITLE}</h2>
            <p>{NO_ACCOUNT_MENUS}</p>
          </div>
        ) : (
          <p role="status">{first.message}</p>
        )}
        <AccountLogout
          api={api}
          dirty={dirty}
          className="primary-button"
          onLoggedOut={signOut}
        />
      </div>
    );
  const forbiddenToast = forbiddenNotice && (
    <Toast
      message={forbiddenNotice}
      kind="info"
      testId="account-forbidden-toast"
      onDismiss={() => setForbiddenNotice("")}
    />
  );
  // 내 정보(UI-41)·비밀번호 변경(UI-40)은 시안처럼 헤더 사용자 메뉴의 창이다. 옛 주소로 들어와도 같은 창을 띄운다.
  const leave = () => navigate(`/screens/${first.screenId}`, { replace: true });
  return (
    <AccountSessionContext.Provider
      value={{ info, meta, refresh, api, signOut }}
    >
      <div data-testid="account-authenticated-shell" data-screen-id="UI-34" style={{ display: "contents" }}>
        {noticeToast}
        {forbiddenToast}
        {screenId === "UI-41" ? (
          <AccountMyInfo api={api} onClose={leave} />
        ) : screenId === "UI-40" ? (
          <AccountPasswordDialog
            api={api}
            meta={meta}
            onClose={leave}
            onCompleted={() => {
              setNotice(PASSWORD_CHANGED_MESSAGE);
              void refresh().catch(() => undefined);
              leave();
            }}
          />
        ) : (
          children
        )}
      </div>
    </AccountSessionContext.Provider>
  );
}
