import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { DetailPanel } from "../../shared/list/DetailPanel";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useScreenTitleOverride } from "../../shared/layout/ScreenTitleOverride";
import {
  clientPayload,
  ClientRequestError,
  formatBusinessNumber,
  getCommonClient,
  getCommonClientPaymentAccount,
  postCommonClient,
  putCommonClient,
  type Client,
  type ClientAccount,
  type ClientCode,
  type ClientInput,
} from "./clients";

export function ClientForm({
  client,
  codes,
  onCancel,
  onSaved,
  onUnchanged,
}: {
  client?: Client;
  codes: Record<string, ClientCode[]>;
  onCancel: () => void;
  onSaved: (client: Client, warnings: string[]) => void;
  /** 수정에서 바뀐 것 없이 저장을 누를 때(시안: 수정을 닫고 안내 알림). */
  onUnchanged?: () => void;
}) {
  const [draft, setDraft] = useState<ClientInput>(() => ({
    clientTypeCode: client?.clientTypeCode || "",
    clientName: client?.clientName || "",
    businessNumber: client?.businessNumber || "",
    representativeName: client?.representativeName || "",
    industryCode: client?.industryCode || "",
    contactName: client?.contactName || "",
    contactPhone: client?.contactPhone || "",
    taxEmail: client?.taxEmail || "",
    bankCode: client?.bankCode || "",
    modifiedAt: client?.modifiedAt ?? null,
  }));
  const [original, setOriginal] = useState<ClientAccount | null>(null);
  const [loading, setLoading] = useState(
    !!client && client.clientTypeCode !== "CUST",
  );
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [error, setError] = useState<ClientRequestError | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [accountFailure, setAccountFailure] = useState(false);
  const [revision, setRevision] = useState(0);
  const initial = useRef(JSON.stringify(draft));
  const setDirty = useScreenDirty();
  const dirty = JSON.stringify(draft) !== initial.current;
  useEffect(() => {
    setDirty("UI-15", dirty);
    return () => setDirty("UI-15", false);
  }, [dirty, setDirty]);
  const formRef = useRef<HTMLElement>(null);
  // FR-0106: 저장하지 않은 내용이 있으면 나가기 전에 묻는다(시안 확인 창).
  const [leaving, setLeaving] = useState(false);
  const leave = () => (dirty ? setLeaving(true) : onCancel());
  // 시안: 폼이 열리면 제목줄이 [‹ 목록] + 「거래처 등록/수정」으로 바뀐다.
  useScreenTitleOverride("UI-15", client ? null : "거래처 등록", leave);
  useEffect(() => {
    if (!client || client.clientTypeCode === "CUST") return;
    let active = true;
    setLoading(true);
    setAccountFailure(false);
    getCommonClientPaymentAccount(client.clientId)
      .then((account) => {
        if (!active) return;
        setOriginal(account);
        setDraft((value) => {
          const next = { ...value, ...account };
          initial.current = JSON.stringify(next);
          return next;
        });
      })
      .catch((reason) => {
        if (active) {
          setAccountFailure(true);
          setError(reason);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [client, revision]);
  const vendor =
    draft.clientTypeCode === "VNDR" || draft.clientTypeCode === "BOTH";
  const customer =
    draft.clientTypeCode === "CUST" || draft.clientTypeCode === "BOTH";
  const set = (field: keyof ClientInput, value: string) =>
    setDraft((current) => ({ ...current, [field]: value }));
  // 시안: 등록 폼은 글 칸을 벗어날 때, 고르는 칸은 고를 때 그 칸만 검사해 칸 아래에 알린다.
  const check = (field: keyof ClientInput, value: ClientInput = draft) => {
    if (client) return;
    const message = clientErrors(value, true)[field];
    const rest = (error?.errors ?? []).filter((item) => item.field !== field);
    const next = message ? [...rest, { field, message }] : rest;
    if (!next.length && (!error || error.status === 400)) setError(null);
    else
      setError(
        new ClientRequestError(error?.status ?? 400, error?.message ?? "", next),
      );
  };
  const validate = () => {
    const errors = Object.entries(clientErrors(draft, !client)).map(
      ([field, message]) => ({ field, message: message as string }),
    );
    if (errors.length) {
      setError(new ClientRequestError(400, "입력 값을 확인해 주세요.", errors));
      // FR-0106: 첫 오류 칸으로 커서를 옮긴다.
      setTimeout(() => {
        formRef.current?.querySelector<HTMLElement>("[aria-invalid='true']")?.focus();
      }, 0);
      return false;
    }
    return true;
  };
  const persist = async () => {
    if (saving.current || loading || accountFailure) return;
    saving.current = true;
    setBusy(true);
    setError(null);
    try {
      const payload = clientPayload(draft, !!client);
      const saved = client
        ? await putCommonClient(client.clientId, payload)
        : await postCommonClient(payload);
      onSaved(saved.client, saved.warnings);
    } catch (reason) {
      setError(
        reason instanceof ClientRequestError
          ? reason
          : new ClientRequestError(0, "저장하지 못했습니다."),
      );
    } finally {
      saving.current = false;
      setBusy(false);
      setConfirm(false);
    }
  };
  const submit = () => {
    if (busy || loading || accountFailure || confirm || !validate()) return;
    // 시안: 수정에서 바뀐 것이 없으면 저장하지 않고 「바뀐 내용이 없습니다.」(안내)를 띄운다.
    if (client && trimmed(draft) === trimmed(JSON.parse(initial.current))) {
      onUnchanged?.();
      return;
    }
    const submitted = clientPayload(draft, !!client);
    // 시안: 계좌가 이미 있던 거래처만 바꾸기 전에 묻는다.
    if (
      client &&
      !!original?.accountNumber &&
      ((submitted.bankCode || "") !==
        (original?.bankCode ||
          (client.clientTypeCode !== "CUST" ? client.bankCode : "") ||
          "") ||
        (submitted.accountNumber || "") !== (original?.accountNumber || "") ||
        (submitted.accountHolder || "") !== (original?.accountHolder || ""))
    ) {
      setConfirm(true);
      return;
    }
    void persist();
  };
  const currentSubmit = useRef(submit);
  currentSubmit.current = submit;
  // FR-0104 Ctrl+S: 공통 단축키 훅(숨은 화면 탭이면 넘김, 수정은 상세 패널 안에서만 받음).
  const saveShortcut = useCallback(() => currentSubmit.current(), []);
  usePreferenceBusinessShortcuts(
    { onSave: saveShortcut, scope: formRef, dialogTarget: client ? formRef : undefined },
    !leaving && !confirm,
  );
  const message = (field: string) =>
    error?.errors
      .filter((item) => item.field === field)
      .map((item, index) => (
        <small role="alert" className="field-error" key={index}>
          {item.message}
        </small>
      ));
  const invalid = (field: string) => !!error?.errors.some((item) => item.field === field);
  const input = (
    field: keyof ClientInput,
    label: string,
    max: number,
    required = false,
    help?: string,
    full = false,
  ) => (
    <div className={`client-fld${full ? " full" : ""}`} key={field}>
      <label className={`client-fl${required ? " req" : ""}`} htmlFor={`client-field-${field}`}>
        {label}
      </label>
      <div className="client-fv">
      <input
        id={`client-field-${field}`}
        className="ls-in"
        aria-invalid={invalid(field)}
        data-testid={`client-${field.replace(/[A-Z]/g, (value) => `-${value.toLowerCase()}`)}`}
        aria-label={label}
        style={client ? { width: EDIT_WIDTH[field] } : undefined}
        value={String(draft[field] ?? "")}
        maxLength={max}
        required={required}
        readOnly={field === "businessNumber" && !!client}
        onChange={(event) =>
          set(
            field,
            field === "businessNumber"
              ? formatBusinessNumber(event.target.value)
              : field === "accountNumber" || field === "contactPhone"
                ? event.target.value.replace(/[^0-9-]/g, "")
                : event.target.value,
          )
        }
        onBlur={() => check(field)}
      />
      {help && <span className="client-help">{help}</span>}
      {message(field)}
      </div>
    </div>
  );
  const select = (
    field: "clientTypeCode" | "industryCode" | "bankCode",
    label: string,
    group: string,
    required = false,
    help?: string,
  ) => (
    <div className="client-fld">
      <label className={`client-fl${required ? " req" : ""}`} htmlFor={`client-field-${field}`}>
        {label}
      </label>
      <div className="client-fv">
      <select
        id={`client-field-${field}`}
        className="ls-in"
        aria-invalid={invalid(field)}
        aria-label={label}
        data-testid={`client-${group.toLowerCase().replace(/_/g, "-")}-select`}
        style={client ? { width: EDIT_WIDTH[field] } : undefined}
        value={draft[field] || ""}
        onChange={(event) => {
          set(field, event.target.value);
          check(field, { ...draft, [field]: event.target.value });
        }}
      >
        <option value="">선택</option>
        {(codes[group] || []).map((code) => (
          <option key={code.code} value={code.code}>
            {code.codeName}
          </option>
        ))}
      </select>
      {help && <span className="client-help">{help}</span>}
      {message(field)}
      </div>
    </div>
  );
  // 칸 아래 알림만 있을 때(벗어날 때 검사)는 위 알림 상자를 띄우지 않는다.
  const errorBox = error &&
    (error.message || error.status === 409 || accountFailure) && (
    <div role="alert" className="client-error">
      {error.message}
      {error.status === 409 && client && (
        <button
          data-testid="client-version-refresh"
          onClick={() => {
            void getCommonClient(client.clientId)
              .then((latest) => {
                setDraft((value) => ({
                  ...value,
                  modifiedAt: latest.modifiedAt,
                }));
                setError(null);
              })
              .catch((reason) => setError(reason));
          }}
        >
          최신 버전 불러오기 (입력 유지)
        </button>
      )}
      {accountFailure && (
        <button
          data-testid="client-account-retry"
          onClick={() => setRevision((value) => value + 1)}
        >
          계좌 다시 조회
        </button>
      )}
    </div>
  );
  const cancelButton = (
    <button
      type="button"
      className="ls-btn"
      data-testid="client-form-cancel"
      disabled={busy}
      onClick={leave}
    >
      취소
    </button>
  );
  const saveButton = (
    <button
      type="button"
      className="ls-btn ls-btn-p"
      data-testid="client-form-save"
      disabled={busy || loading || accountFailure}
      onClick={submit}
    >
      {busy ? "저장 중…" : "저장"}
    </button>
  );
  const dialogs = (
    <>
      {leaving && (
        <Dialog
          title={client ? "고친 내용을 버릴까요?" : "저장하지 않은 내용이 있습니다"}
          testId="client-leave-confirm"
          closable
          onClose={() => setLeaving(false)}
        >
          <p>{client ? "저장하지 않은 내용이 사라집니다." : "나가면 입력한 내용이 사라집니다."}</p>
          <div className="app-actions">
            <button type="button" data-testid="client-leave-cancel" onClick={() => setLeaving(false)}>
              취소
            </button>
            <button type="button" className="primary-button" data-testid="client-leave-go" onClick={onCancel}>
              {client ? "버리기" : "나가기"}
            </button>
          </div>
        </Dialog>
      )}
      {confirm && (
        <Dialog
          title="지급 계좌를 바꿀까요?"
          testId="client-account-confirm"
          closable
          onClose={() => !busy && setConfirm(false)}
        >
          <p>
            다음 지급부터 새 계좌(
            {(codes.BANK || []).find((code) => code.code === draft.bankCode)?.codeName ||
              draft.bankCode}{" "}
            …{String(draft.accountNumber || "").replace(/\D/g, "").slice(-4)})로 보냅니다. 바꾼
            기록이 남습니다.
          </p>
          <footer>
            <button
              data-testid="client-account-cancel"
              disabled={busy}
              onClick={() => setConfirm(false)}
            >
              취소
            </button>
            <button
              data-testid="client-account-save"
              disabled={busy}
              onClick={() => void persist()}
            >
              바꾸기
            </button>
          </footer>
        </Dialog>
      )}
    </>
  );

  // 시안: [수정]은 상세 패널 안에서 고친다(제목=상호, 작은 제목 「수정 중 · 사업자번호」, 한 줄씩, 아래 [취소][저장]).
  if (client)
    return (
      <DetailPanel
        title={client.clientName}
        sub={`수정 중 · ${client.businessNumber}`}
        testId="client-form"
        onClose={leave}
        footer={
          <>
            {cancelButton}
            {saveButton}
          </>
        }
      >
        <div
          ref={formRef as RefObject<HTMLDivElement>}
          className="client-form client-edit"
          data-screen-id="UI-15"
        >
          {loading && <p role="status">계좌 조회 중…</p>}
          <div className="client-fsec">
            {select("clientTypeCode", "구분", "CLNT_TYPE", true, "고객사이면서 협력사이면 '고객·협력'")}
            {input("clientName", "상호", 50, true)}
            {input(
              "businessNumber",
              "사업자번호",
              12,
              false,
              "프로젝트·청구·지급과 연결되어 있어 바꿀 수 없습니다. 잘못 넣었으면 사용 중지 후 새로 등록하세요.",
            )}
            {input("representativeName", "대표자", 20)}
            {select("industryCode", "업종", "INDUTY", false, "공통코드 「업종」에서 고릅니다")}
            {input("contactName", "담당자", 20)}
            {input("contactPhone", "연락처", 20)}
            {customer && input("taxEmail", "세금계산서 이메일", 100, true)}
            {vendor && select("bankCode", "은행", "BANK", true)}
            {vendor && input("accountNumber", "계좌번호", 20, true, "숫자와 하이픈(-)만 씁니다")}
            {vendor && input("accountHolder", "예금주", 30, true)}
          </div>
          {errorBox}
          {dialogs}
        </div>
      </DetailPanel>
    );

  return (
    <section
      ref={formRef}
      className="client-form"
      data-testid="client-form"
      data-screen-id="UI-15"
    >
      {loading && <p role="status">계좌 조회 중…</p>}
      {!draft.clientTypeCode && (
        <p className="ls-banner ls-banner-info client-form-banner" role="status">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" /></svg>
          구분을 고르면 고객사는 세금계산서 이메일, 협력사는 지급 계좌 칸이 나옵니다.
        </p>
      )}
      <section className="client-card">
        <h2>기본 정보</h2>
        <div className="client-fgrid">
          {select("clientTypeCode", "구분", "CLNT_TYPE", true, "고객사이면서 협력사이면 '고객·협력'을 고릅니다")}
          {input("clientName", "상호", 50, true)}
          {input("businessNumber", "사업자번호", 12, true, "숫자만 입력하면 하이픈이 자동으로 들어갑니다")}
          {input("representativeName", "대표자", 20)}
          {select("industryCode", "업종", "INDUTY", false, "공통코드 「업종」에서 고릅니다. 없으면 관리자에게 추가를 요청하세요")}
          {input("contactName", "담당자", 20)}
          {input("contactPhone", "연락처", 20)}
        </div>
      </section>
      {customer && (
        <section className="client-card">
          <h2>세금계산서</h2>
          <div className="client-fgrid">{input("taxEmail", "세금계산서 이메일", 100, true, undefined, true)}</div>
        </section>
      )}
      {vendor && (
        <section className="client-card">
          <h2>지급 계좌</h2>
          <div className="client-fgrid">
            {select("bankCode", "은행", "BANK", true)}
            {input("accountNumber", "계좌번호", 20, true, "숫자와 하이픈(-)만 씁니다")}
            {input("accountHolder", "예금주", 30, true)}
          </div>
        </section>
      )}
      {errorBox}
      <p className="client-note">
        <span className="client-bad">*</span> 표시는 꼭 입력해야 하는 항목입니다.
      </p>
      <footer className="client-form-foot">
        {cancelButton}
        {saveButton}
      </footer>
      {dialogs}
    </section>
  );
}

/** 시안 상세 패널 안 수정 칸 폭. */
const EDIT_WIDTH: Partial<Record<keyof ClientInput, string>> = {
  clientTypeCode: "160px",
  clientName: "100%",
  businessNumber: "160px",
  representativeName: "200px",
  industryCode: "200px",
  contactName: "200px",
  contactPhone: "200px",
  taxEmail: "100%",
  bankCode: "160px",
  accountNumber: "220px",
  accountHolder: "220px",
};

/** 바뀐 것 비교용: 시안처럼 앞뒤 빈칸은 빼고 본다. */
function trimmed(value: ClientInput) {
  return JSON.stringify(
    Object.fromEntries(
      Object.entries(value)
        .map(([key, item]): [string, unknown] => [key, typeof item === "string" ? item.trim() : item])
        .filter(([, item]) => item !== "" && item !== undefined)
        .sort(([a], [b]) => String(a).localeCompare(String(b))),
    ),
  );
}

/** 시안 clientErrors 문구 그대로. 칸마다 첫 오류 하나. 등록일 때만 사업자번호를 본다. */
function clientErrors(draft: ClientInput, isNew: boolean) {
  const errors: Partial<Record<keyof ClientInput, string>> = {};
  const text = (field: keyof ClientInput) => String(draft[field] ?? "").trim();
  const kind = draft.clientTypeCode;
  if (!kind) errors.clientTypeCode = "구분을 골라 주세요.";
  if (!text("clientName")) errors.clientName = "상호를 입력해 주세요.";
  if (isNew && !/^\d{3}-\d{2}-\d{5}$/.test(draft.businessNumber || ""))
    errors.businessNumber = "사업자번호 10자리를 입력해 주세요. (예: 123-45-67890)";
  if (kind && kind !== "VNDR") {
    if (!text("taxEmail")) errors.taxEmail = "고객사는 세금계산서 이메일이 꼭 필요합니다.";
    else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(text("taxEmail")))
      errors.taxEmail = "이메일 형식이 맞지 않습니다. (예: tax@company.co.kr)";
  }
  if (kind && kind !== "CUST") {
    if (!draft.bankCode) errors.bankCode = "은행을 골라 주세요.";
    if (!/^[\d-]{8,20}$/.test(draft.accountNumber || ""))
      errors.accountNumber = "계좌번호를 숫자와 하이픈(-)으로 입력해 주세요.";
    if (!text("accountHolder")) errors.accountHolder = "예금주를 입력해 주세요.";
  }
  // 시안에 없는 칸 규칙(글자 수·연락처)은 그대로 둔다.
  for (const [field, max] of Object.entries({
    clientName: 50,
    representativeName: 20,
    contactName: 20,
    contactPhone: 20,
    taxEmail: 100,
    accountHolder: 30,
  }) as [keyof ClientInput, number][]) {
    if (!errors[field] && [...String(draft[field] || "")].length > max)
      errors[field] = `${max}자 이하로 입력해 주세요.`;
  }
  if (!errors.contactPhone && contactPhoneInvalid(draft.contactPhone))
    errors.contactPhone = "연락처는 숫자와 하이픈(-)만 써 주세요.";
  return errors;
}

/** 연락처: 숫자와 하이픈만(FR-0106 숫자 칸). 비어 있으면 통과. */
function contactPhoneInvalid(value: string | null | undefined) {
  return !!value && !/^[0-9-]{2,20}$/.test(value);
}
