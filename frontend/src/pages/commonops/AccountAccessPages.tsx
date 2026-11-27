import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  AccountApiError,
  firstRoleLabel,
  PASSWORD_RULE_ERROR,
  PASSWORD_RULE_HELP,
  roleLabels,
  roleShortLabels,
  type AccountAccessApi,
  type AccountInfo,
  type AccountMeta,
  type AccountResponse,
  type LoginResult,
} from "./accountAccessApi";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import { formatDate } from "../../shared/time";
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
/** 시안 .lcard: 로고 + 「프로젝트·원가 관리」 카드(로그인·인증번호·첫 비밀번호 변경이 같이 쓴다). */
export function AccountFrame({
  children,
  screenId,
  className = "",
  testId,
}: {
  children: ReactNode;
  screenId: string;
  className?: string;
  testId?: string;
}) {
  return (
    <main
      className={`account-access ${className}`.trim()}
      data-testid={testId ?? `account-${screenId.toLowerCase()}`}
      data-screen-id={screenId}
    >
      <section className="account-card">
        <div className="account-brand">
          <span className="account-logo" aria-hidden="true">
            SI
          </span>
          <span>프로젝트·원가 관리</span>
        </div>
        {children}
      </section>
    </main>
  );
}
/** 칸 하나: 이름표 + 입력 + 칸 아래 오류(빨강) + 도움말. */
function Field({
  id,
  label,
  error,
  help,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  help?: string;
  children: ReactNode;
}) {
  return (
    <div className="account-field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <span className="account-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      ) : null}
      {help ? <span className="account-cap">{help}</span> : null}
    </div>
  );
}
export function AccountLoginPage({
  api,
  onCompleted,
  initialError = "",
}: {
  api: AccountAccessApi;
  onCompleted: (
    response: AccountResponse<LoginResult>,
    currentPassword: string,
  ) => void;
  /** 처음부터 다시 로그인할 때 비밀번호 칸 아래에 보일 문구(인증번호 5번 틀림 등, 시안). */
  initialError?: string;
}) {
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState(initialError);
  const [busy, setBusy] = useState(false);
  const [find, setFind] = useState(false);
  const [notice, setNotice] = useState("");
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
      const fieldErrors = errorFields(failure);
      const text = message(failure);
      // 서버 오류(아이디·비밀번호 틀림, 잠김, 중지)는 시안처럼 비밀번호 칸 아래 한 줄로 보인다.
      if (fieldErrors.password === text) delete fieldErrors.password;
      setErrors(fieldErrors);
      setError(text);
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  const passwordError = errors.password ?? error;
  return (
    <AccountFrame screenId="UI-22">
      <h1>로그인</h1>
      <form onSubmit={submit} noValidate className="account-form">
        <Field id="account-login-id" label="아이디" error={errors.userId}>
          <input
            id="account-login-id"
            data-testid="account-login-user-id"
            autoComplete="username"
            value={userId}
            maxLength={30}
            onChange={(event) => setUserId(event.target.value)}
            aria-invalid={Boolean(errors.userId)}
          />
        </Field>
        <Field
          id="account-login-password"
          label="비밀번호"
          error={passwordError}
        >
          <input
            id="account-login-password"
            data-testid="account-login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(errors.password)}
          />
        </Field>
        <button
          data-testid="account-login-submit"
          className="account-primary"
          disabled={busy}
        >
          {busy ? "로그인 중…" : "로그인"}
        </button>
        <p className="account-cap">
          비밀번호를 5번 틀리면 계정이 잠깁니다.{" "}
          <button
            data-testid="account-login-find"
            type="button"
            className="account-link"
            onClick={() => setFind(true)}
          >
            비밀번호 찾기
          </button>
        </p>
      </form>
      {find && (
        <AccountPasswordFind
          api={api}
          onClose={() => setFind(false)}
          onSent={(text) => {
            setFind(false);
            setNotice(text);
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind="ok"
          testId="account-find-message"
          onDismiss={() => setNotice("")}
        />
      )}
    </AccountFrame>
  );
}
/** 시안 pwfind 창: 아이디·등록 이메일, 안내, [취소] [임시 비밀번호 받기]. 보내면 창을 닫고 토스트. */
export function AccountPasswordFind({
  api,
  onClose,
  onSent,
}: {
  api: AccountAccessApi;
  onClose: () => void;
  onSent: (message: string) => void;
}) {
  const [userId, setUserId] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [field, setField] = useState("");
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (running.current) return;
    if (!userId.trim()) {
      setField("userId");
      setError("아이디를 넣어 주세요.");
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setField("email");
      setError("이메일 형식이 맞지 않습니다.");
      return;
    }
    setField("");
    setError("");
    running.current = true;
    setBusy(true);
    try {
      onSent((await api.find(userId, email.trim())).data.message);
    } catch (failure) {
      const fields = Object.entries(errorFields(failure));
      setField(fields[0]?.[0] ?? "");
      setError(fields[0]?.[1] ?? message(failure));
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog
      title="비밀번호 찾기"
      onClose={() => {
        if (!busy) onClose();
      }}
      testId="account-find-dialog"
      screenId="UI-22-M1"
      closable
    >
      <form noValidate onSubmit={submit} className="account-dialog-form">
        <Field id="account-find-id" label="아이디">
          <input
            id="account-find-id"
            data-testid="account-find-user-id"
            autoComplete="username"
            value={userId}
            aria-invalid={field === "userId"}
            onChange={(event) => setUserId(event.target.value)}
          />
        </Field>
        <Field id="account-find-email" label="등록 이메일">
          <input
            id="account-find-email"
            data-testid="account-find-email"
            type="email"
            inputMode="email"
            autoComplete="email"
            value={email}
            aria-invalid={field === "email"}
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>
        <p className="account-cap">
          아이디와 이메일이 맞으면 그 이메일로 임시 비밀번호를 보냅니다.
          로그인한 뒤 바로 바꿔야 합니다.
        </p>
        {error && (
          <p className="account-error" role="alert">
            {error}
          </p>
        )}
        <div className="app-actions">
          <button
            type="button"
            data-testid="account-find-close"
            onClick={onClose}
            disabled={busy}
          >
            취소
          </button>
          <button
            data-testid="account-find-submit"
            className="primary-button"
            disabled={busy}
          >
            임시 비밀번호 받기
          </button>
        </div>
      </form>
    </Dialog>
  );
}
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,72}$/;
/** 시안 pwModal·첫 로그인 변경: 위에서부터 한 칸씩 확인하고 첫 오류만 그 칸 아래에 보인다. */
function passwordErrors(
  current: string | null,
  next: string,
  confirm: string,
  temporary: boolean,
): Errors {
  if (current !== null && !current)
    return { currentPassword: "현재 비밀번호를 입력해 주세요." };
  if (!next) return { newPassword: "새 비밀번호를 입력해 주세요." };
  if (!PASSWORD_PATTERN.test(next)) return { newPassword: PASSWORD_RULE_ERROR };
  if (current !== null && next === current)
    return {
      newPassword: temporary
        ? "임시 비밀번호와 다르게 정해 주세요."
        : "현재 비밀번호와 다르게 정해 주세요.",
    };
  if (next !== confirm) return { confirm: "새 비밀번호가 서로 다릅니다." };
  return {};
}
function usePasswordChange(
  api: AccountAccessApi,
  meta: AccountMeta,
  onCompleted: (meta: AccountMeta) => void,
  remembered?: string,
) {
  const temporary = Boolean(meta.passwordChangeRequired);
  const askCurrent = !temporary || !remembered;
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    const currentValue = askCurrent ? current : (remembered ?? "");
    const fields = passwordErrors(currentValue, next, confirm, temporary);
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
      const response = await api.change(currentValue, next, meta.modifiedAt);
      setCurrent("");
      setNext("");
      setConfirm("");
      onCompleted(response.meta);
    } catch (failure) {
      const fieldErrors = errorFields(failure);
      const text = message(failure);
      setErrors(fieldErrors);
      // 칸 오류로 보인 문구는 아래에 한 번 더 쓰지 않는다.
      setError(Object.values(fieldErrors).includes(text) ? "" : text);
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  const fields = (
    <>
      {askCurrent && (
        <Field
          id="account-password-current"
          label="현재 비밀번호"
          error={errors.currentPassword}
        >
          <input
            id="account-password-current"
            data-testid="account-password-current"
            type="password"
            autoComplete="current-password"
            value={current}
            aria-invalid={Boolean(errors.currentPassword)}
            onChange={(event) => setCurrent(event.target.value)}
          />
        </Field>
      )}
      <Field
        id="account-password-new"
        label="새 비밀번호"
        error={errors.newPassword}
        help={PASSWORD_RULE_HELP}
      >
        <input
          id="account-password-new"
          data-testid="account-password-new"
          type="password"
          autoComplete="new-password"
          value={next}
          aria-invalid={Boolean(errors.newPassword)}
          onChange={(event) => setNext(event.target.value)}
        />
      </Field>
      <Field
        id="account-password-confirm"
        label="새 비밀번호 확인"
        error={errors.confirm}
      >
        <input
          id="account-password-confirm"
          data-testid="account-password-confirm"
          type="password"
          autoComplete="new-password"
          value={confirm}
          aria-invalid={Boolean(errors.confirm)}
          onChange={(event) => setConfirm(event.target.value)}
        />
      </Field>
      {error && (
        <p className="account-error" role="alert">
          {error}
        </p>
      )}
    </>
  );
  return { submit, fields, busy };
}
/** 시안 pwModal 맨 위: 「마지막 변경 YYYY-MM-DD · n일 뒤 변경 안내」 또는 「90일이 지나 변경을 권합니다」. */
export function passwordAge(meta: AccountMeta) {
  const days = meta.passwordChangeDays ?? 90;
  if (!meta.passwordChangedAt) return `마지막 변경 – · ${days}일이 지나 변경을 권합니다`;
  const changed = formatDate(meta.passwordChangedAt);
  const today = formatDate(meta.serverRespondedAt ?? new Date().toISOString());
  const passed = Math.round(
    (Date.parse(`${today}T00:00:00Z`) - Date.parse(`${changed}T00:00:00Z`)) /
      86400000,
  );
  const left = days - (Number.isFinite(passed) ? passed : 0);
  return `마지막 변경 ${changed} · ${left > 0 ? `${left}일 뒤 변경 안내` : `${days}일이 지나 변경을 권합니다`}`;
}
/** 사용자 메뉴 [비밀번호 변경] 창(시안 pwModal). */
export function AccountPasswordDialog({
  api,
  meta,
  onClose,
  onCompleted,
}: {
  api: AccountAccessApi;
  meta: AccountMeta;
  onClose: () => void;
  onCompleted: (meta: AccountMeta) => void;
}) {
  const ordinary = { ...meta, passwordChangeRequired: false };
  const { submit, fields, busy } = usePasswordChange(api, ordinary, onCompleted);
  return (
    <Dialog
      title="비밀번호 변경"
      onClose={() => {
        if (!busy) onClose();
      }}
      testId="account-password-dialog"
      screenId="UI-40"
      closable
    >
      <form noValidate onSubmit={submit} className="account-dialog-form">
        <p className="account-cap" data-testid="account-password-age">
          {passwordAge(meta)}
        </p>
        {fields}
        <div className="app-actions">
          <button
            type="button"
            data-testid="account-password-back"
            disabled={busy}
            onClick={onClose}
          >
            취소
          </button>
          <button
            data-testid="account-password-submit"
            className="primary-button"
            disabled={busy}
          >
            변경
          </button>
        </div>
      </form>
    </Dialog>
  );
}
/** 첫 로그인(임시 비밀번호) 비밀번호 변경 카드(시안 renderPwForce). 바꾸기 전에는 들어갈 수 없다. */
export function AccountPasswordPage({
  api,
  meta,
  onCompleted,
  onBack,
  currentPassword,
  name,
}: {
  api: AccountAccessApi;
  meta: AccountMeta;
  onCompleted: (meta: AccountMeta) => void;
  onBack: () => void | Promise<void>;
  currentPassword?: string;
  /** 로그인한 사람 이름(알 때만 「○○님은」을 붙인다). */
  name?: string;
}) {
  const forced = { ...meta, passwordChangeRequired: true };
  const { submit, fields, busy } = usePasswordChange(
    api,
    forced,
    onCompleted,
    currentPassword,
  );
  return (
    <AccountFrame screenId="UI-40">
      <h1>비밀번호 변경</h1>
      <p className="account-sub">
        {name ? `${name}님은 ` : ""}임시 비밀번호로 로그인했습니다. 새
        비밀번호로 바꿔야 시작할 수 있습니다.
      </p>
      <form noValidate onSubmit={submit} className="account-form">
        {fields}
        <button
          data-testid="account-password-submit"
          className="account-primary"
          disabled={busy}
        >
          변경하고 시작
        </button>
        <div className="account-row account-row-end">
          <button
            type="button"
            className="account-text-button"
            data-testid="account-password-back"
            disabled={busy}
            onClick={() => void onBack()}
          >
            처음으로
          </button>
        </div>
      </form>
    </AccountFrame>
  );
}
/** 사용자 메뉴 [내 정보] 창(시안 myInfo): 보기만, 고칠 칸 없음. */
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
    <Dialog
      title="내 정보"
      onClose={onClose}
      testId="account-my-info"
      screenId="UI-41"
      closable
    >
      {error ? (
        <p className="account-error" role="alert">
          {error}
        </p>
      ) : !info ? (
        <p role="status">불러오는 중…</p>
      ) : (
        <dl className="account-kv">
          <dt>이름</dt>
          <dd>{info.name}</dd>
          <dt>역할</dt>
          <dd>{roleLabels(info).join(", ") || "–"}</dd>
          <dt>소속 파트</dt>
          <dd>{info.parts.join(", ") || "–"}</dd>
          <dt>아이디</dt>
          <dd>{info.userId}</dd>
        </dl>
      )}
      <p className="account-cap account-info-note">
        보기만 할 수 있습니다. 이름·역할은 관리자가 사용자 관리에서 바꿉니다.
        소속 파트는 영업 파트장이면 영업 파트 탭의 파트장, PM이면 담당
        프로젝트의 영업 파트로 정해집니다. 보유 스킬·거주지는 개발자 풀에서
        입력합니다.
      </p>
      <div className="app-actions">
        <button
          type="button"
          data-testid="account-info-close"
          className="primary-button"
          onClick={onClose}
        >
          닫기
        </button>
      </div>
    </Dialog>
  );
}
export function AccountLogout({
  api,
  dirty,
  onLoggedOut,
  className,
  menuItem = false,
}: {
  api: AccountAccessApi;
  dirty: boolean;
  onLoggedOut: () => void;
  className?: string;
  /** 사용자 메뉴 안 항목으로 그린다(role="menuitem"). */
  menuItem?: boolean;
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
        type="button"
        data-testid="account-logout"
        className={className}
        role={menuItem ? "menuitem" : undefined}
        disabled={busy}
        onClick={() => (dirty ? setConfirm(true) : void logout())}
      >
        로그아웃
      </button>
      {confirm && (
        <Dialog
          title="저장하지 않은 내용이 있습니다."
          role="alertdialog"
          onClose={() => setConfirm(false)}
          testId="account-logout-dialog"
          screenId="UI-34-M3"
        >
          <p>로그아웃하면 입력한 내용이 사라집니다.</p>
          <div className="app-actions">
            <button
              type="button"
              data-testid="account-logout-cancel"
              disabled={busy}
              onClick={() => setConfirm(false)}
            >
              취소
            </button>
            <button
              type="button"
              data-testid="account-logout-confirm"
              className="primary-button"
              disabled={busy}
              onClick={() => void logout()}
            >
              로그아웃
            </button>
          </div>
        </Dialog>
      )}
      {error && (
        <p className="account-error" role="alert">
          {error}
        </p>
      )}
    </>
  );
}
/** 헤더 사용자 단추 글: 「이름 (첫 역할 외 n)」(명세 ④-1, 역할은 줄인 이름). */
export function AccountIdentity({ info }: { info: AccountInfo }) {
  return (
    <span data-testid="account-identity">
      {info.name}{" "}
      <span className="hd-sub">({firstRoleLabel(roleShortLabels(info))})</span>
    </span>
  );
}
