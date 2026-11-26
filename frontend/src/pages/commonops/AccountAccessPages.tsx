import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  AccountApiError,
  type AccountAccessApi,
  type AccountInfo,
  type AccountMeta,
  type AccountResponse,
} from "./accountAccessApi";
import "./accountAccess.css";

type Errors = Record<string, string>;
function errorFields(error: unknown): Errors {
  return error instanceof AccountApiError
    ? Object.fromEntries(error.errors.map((item) => [item.field, item.message]))
    : {};
}
function message(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청을 처리하지 못했습니다. 다시 시도해 주세요.";
}
function AccountFrame({
  children,
  screenId,
}: {
  children: ReactNode;
  screenId: string;
}) {
  return (
    <main
      className="account-access"
      data-testid={`account-${screenId.toLowerCase()}`}
      data-screen-id={screenId}
    >
      <section className="account-card">{children}</section>
    </main>
  );
}
export function AccountLoginPage({
  api,
  onCompleted,
}: {
  api: AccountAccessApi;
  onCompleted: (
    response: AccountResponse<{ nextScreenId: string }>,
    currentPassword: string,
  ) => void;
}) {
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [find, setFind] = useState(false);
  const running = useRef(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    const fields: Errors = {};
    if (!userId.trim()) fields.userId = "아이디를 넣어 주세요.";
    if (!password) fields.password = "비밀번호를 입력해 주세요.";
    setErrors(fields);
    setError("");
    if (Object.keys(fields).length || running.current) return;
    running.current = true;
    setBusy(true);
    try {
      onCompleted(await api.login(userId, password), password);
      setPassword("");
    } catch (failure) {
      setErrors(errorFields(failure));
      setError(message(failure));
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return (
    <AccountFrame screenId="UI-22">
      <h1>로그인</h1>
      <form onSubmit={submit} noValidate>
        <label>
          아이디
          <input
            data-testid="account-login-user-id"
            autoComplete="username"
            value={userId}
            maxLength={30}
            onChange={(event) => setUserId(event.target.value)}
            aria-invalid={Boolean(errors.userId)}
          />
        </label>
        {errors.userId && <p className="account-error">{errors.userId}</p>}
        <label>
          비밀번호
          <input
            data-testid="account-login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(errors.password)}
          />
        </label>
        {errors.password && <p className="account-error">{errors.password}</p>}
        {error && <p role="alert">{error}</p>}
        <button data-testid="account-login-submit" disabled={busy}>
          {busy ? "로그인 중…" : "로그인"}
        </button>
      </form>
      <p>비밀번호를 5번 틀리면 계정이 잠깁니다.</p>
      <button
        data-testid="account-login-find"
        type="button"
        onClick={() => setFind(true)}
      >
        비밀번호 찾기
      </button>
      {find && <AccountPasswordFind api={api} onClose={() => setFind(false)} />}
    </AccountFrame>
  );
}
export function AccountPasswordFind({
  api,
  onClose,
}: {
  api: AccountAccessApi;
  onClose: () => void;
}) {
  const [userId, setUserId] = useState("");
  const [email, setEmail] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [result, setResult] = useState("");
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    const fields: Errors = {};
    if (!userId.trim()) fields.userId = "아이디를 넣어 주세요.";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
      fields.email = "이메일 형식이 맞지 않습니다.";
    setErrors(fields);
    setResult("");
    if (Object.keys(fields).length || running.current) return;
    running.current = true;
    setBusy(true);
    try {
      setResult((await api.find(userId, email)).data.message);
    } catch (failure) {
      setErrors(errorFields(failure));
      setResult(message(failure));
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="비밀번호 찾기"
      data-testid="account-find-dialog"
      className="account-dialog"
      data-screen-id="UI-22-M1"
    >
      <h2>비밀번호 찾기</h2>
      <form noValidate onSubmit={submit}>
        <label>
          아이디
          <input
            data-testid="account-find-user-id"
            autoComplete="username"
            value={userId}
            onChange={(event) => setUserId(event.target.value)}
          />
        </label>
        {errors.userId && <p>{errors.userId}</p>}
        <label>
          등록 이메일
          <input
            data-testid="account-find-email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        {errors.email && <p>{errors.email}</p>}
        <button data-testid="account-find-submit" disabled={busy}>
          임시 비밀번호 받기
        </button>
      </form>
      {result && <p role="status">{result}</p>}
      <button
        data-testid="account-find-close"
        onClick={onClose}
        disabled={busy}
      >
        닫기
      </button>
    </div>
  );
}
export function AccountPasswordPage({
  api,
  meta,
  onCompleted,
  onBack,
  currentPassword,
}: {
  api: AccountAccessApi;
  meta: AccountMeta;
  onCompleted: (meta: AccountMeta) => void;
  onBack: () => void | Promise<void>;
  currentPassword?: string;
}) {
  const [current, setCurrent] = useState(currentPassword ?? "");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const elapsed =
    meta.passwordChangedAt && meta.serverRespondedAt
      ? Date.parse(meta.serverRespondedAt) - Date.parse(meta.passwordChangedAt)
      : 0;
  async function submit(event: FormEvent) {
    event.preventDefault();
    const fields: Errors = {};
    if (!current) fields.currentPassword = "현재 비밀번호를 입력해 주세요.";
    if (!/^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,72}$/.test(next))
      fields.newPassword =
        "비밀번호는 8자 이상 영문·숫자·특수문자를 조합해 주세요.";
    else if (next === current)
      fields.newPassword = meta.passwordChangeRequired
        ? "임시 비밀번호와 다르게 정해 주세요."
        : "현재 비밀번호와 다르게 정해 주세요.";
    if (next !== confirm) fields.confirm = "새 비밀번호가 서로 다릅니다.";
    setErrors(fields);
    setError("");
    if (Object.keys(fields).length || running.current) return;
    if (!meta.modifiedAt) {
      setError("저장 시각을 확인할 수 없습니다. 다시 로그인해 주세요.");
      return;
    }
    running.current = true;
    setBusy(true);
    try {
      const response = await api.change(current, next, meta.modifiedAt);
      setCurrent("");
      setNext("");
      setConfirm("");
      onCompleted(response.meta);
    } catch (failure) {
      setErrors(errorFields(failure));
      setError(message(failure));
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return (
    <AccountFrame screenId="UI-40">
      <h1>비밀번호 변경</h1>
      <p>
        마지막 변경 날짜:{" "}
        {meta.passwordChangedAt
          ? new Date(meta.passwordChangedAt).toLocaleDateString("ko-KR")
          : "변경 이력 없음"}
      </p>
      {meta.passwordChangeDays &&
      elapsed >= meta.passwordChangeDays * 86400000 ? (
        <p>
          {meta.passwordChangeDays}일이 지났습니다. 비밀번호 변경을 권장합니다.
        </p>
      ) : null}
      <p>8자 이상 영문·숫자·특수문자를 조합해 주세요.</p>
      <form noValidate onSubmit={submit}>
        {(!meta.passwordChangeRequired || !currentPassword) && (
          <label>
            현재 비밀번호
            <input
              data-testid="account-password-current"
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
            />
          </label>
        )}
        {errors.currentPassword && <p>{errors.currentPassword}</p>}
        <label>
          새 비밀번호
          <input
            data-testid="account-password-new"
            type="password"
            autoComplete="new-password"
            value={next}
            onChange={(event) => setNext(event.target.value)}
          />
        </label>
        {errors.newPassword && <p>{errors.newPassword}</p>}
        <label>
          새 비밀번호 확인
          <input
            data-testid="account-password-confirm"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
          />
        </label>
        {errors.confirm && <p>{errors.confirm}</p>}
        {error && <p role="alert">{error}</p>}
        <button data-testid="account-password-submit" disabled={busy}>
          {meta.passwordChangeRequired ? "변경하고 시작" : "변경"}
        </button>
      </form>
      <button
        data-testid="account-password-back"
        disabled={busy}
        onClick={() => void onBack()}
      >
        {meta.passwordChangeRequired ? "처음으로" : "취소"}
      </button>
    </AccountFrame>
  );
}
export function AccountMyInfo({
  api,
  onClose,
}: {
  api: AccountAccessApi;
  onClose: () => void;
}) {
  const [info, setInfo] = useState<AccountInfo>();
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    api
      .myInfo()
      .then((response) => {
        if (active) setInfo(response.data);
      })
      .catch((failure) => {
        if (active) setError(message(failure));
      });
    return () => {
      active = false;
    };
  }, [api]);
  return (
    <section
      role="dialog"
      aria-modal="true"
      aria-label="내 정보"
      data-testid="account-my-info"
      data-screen-id="UI-41"
      className="account-card"
    >
      <h1>내 정보</h1>
      {error ? (
        <p role="alert">{error}</p>
      ) : !info ? (
        <p role="status">불러오는 중…</p>
      ) : (
        <dl>
          <dt>이름</dt>
          <dd>{info.name}</dd>
          <dt>역할</dt>
          <dd>{info.roles.join(", ") || "없음"}</dd>
          <dt>소속 파트</dt>
          <dd>{info.parts.join(", ") || "없음"}</dd>
          <dt>아이디</dt>
          <dd>{info.userId}</dd>
        </dl>
      )}
      <button data-testid="account-info-close" onClick={onClose}>
        닫기
      </button>
    </section>
  );
}
export function AccountLogout({
  api,
  dirty,
  onLoggedOut,
}: {
  api: AccountAccessApi;
  dirty: boolean;
  onLoggedOut: () => void;
}) {
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function logout() {
    if (busy) return;
    setBusy(true);
    try {
      await api.logout();
      onLoggedOut();
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <button
        data-testid="account-logout"
        disabled={busy}
        onClick={() => (dirty ? setConfirm(true) : void logout())}
      >
        로그아웃
      </button>
      {confirm && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="로그아웃 확인"
          data-testid="account-logout-dialog"
          data-screen-id="UI-34-M3"
        >
          <p>저장하지 않은 내용이 있습니다.</p>
          <p>로그아웃하면 입력한 내용이 사라집니다.</p>
          <button
            data-testid="account-logout-confirm"
            disabled={busy}
            onClick={() => void logout()}
          >
            로그아웃
          </button>
          <button
            data-testid="account-logout-cancel"
            disabled={busy}
            onClick={() => setConfirm(false)}
          >
            취소
          </button>
        </div>
      )}
      {error && <p role="alert">{error}</p>}
    </>
  );
}
export function AccountIdentity({ info }: { info: AccountInfo }) {
  const role = info.roles[0] ?? "역할 없음";
  return (
    <span data-testid="account-identity">
      {info.name}({role}
      {info.roles.length > 1 ? ` 외 ${info.roles.length - 1}` : ""})
    </span>
  );
}
