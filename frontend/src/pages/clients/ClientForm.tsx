import { useEffect, useRef, useState, type RefObject } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
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
}: {
  client?: Client;
  codes: Record<string, ClientCode[]>;
  onCancel: () => void;
  onSaved: (client: Client, warnings: string[]) => void;
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
  const validate = () => {
    const errors: { field: string; message: string }[] = [];
    const required = [
      "clientTypeCode",
      "clientName",
      ...(!client ? ["businessNumber"] : []),
      ...(customer ? ["taxEmail"] : []),
      ...(vendor ? ["bankCode", "accountNumber", "accountHolder"] : []),
    ];
    for (const field of required)
      if (!String(draft[field as keyof ClientInput] || "").trim())
        errors.push({ field, message: "필수 입력입니다." });
    for (const [field, max] of Object.entries({
      clientName: 50,
      representativeName: 20,
      contactName: 20,
      contactPhone: 20,
      taxEmail: 100,
      accountHolder: 30,
    })) {
      if ([...String(draft[field as keyof ClientInput] || "")].length > max)
        errors.push({ field, message: `${max}자 이하로 입력해 주세요.` });
    }
    if (
      !client &&
      !/^[0-9]{3}-[0-9]{2}-[0-9]{5}$/.test(draft.businessNumber || "")
    )
      errors.push({
        field: "businessNumber",
        message: "000-00-00000 형식으로 입력해 주세요.",
      });
    if (vendor && !/^[0-9-]{8,20}$/.test(draft.accountNumber || ""))
      errors.push({
        field: "accountNumber",
        message: "숫자·하이픈 8~20자로 입력해 주세요.",
      });
    if (customer && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.taxEmail || ""))
      errors.push({ field: "taxEmail", message: "이메일을 확인해 주세요." });
    if (contactPhoneInvalid(draft.contactPhone))
      errors.push({ field: "contactPhone", message: "연락처는 숫자와 하이픈(-)만 써 주세요." });
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
    const submitted = clientPayload(draft, !!client);
    if (
      client &&
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
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        currentSubmit.current();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
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
        onChange={(event) => set(field, event.target.value)}
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
  const errorBox = error && (
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
          title="지급 계좌 변경"
          testId="client-account-confirm"
          closable
          onClose={() => !busy && setConfirm(false)}
        >
          <p>다음 지급부터 새 계좌로 보냅니다</p>
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
              확인 저장
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

/** 연락처: 숫자와 하이픈만(FR-0106 숫자 칸). 비어 있으면 통과. */
function contactPhoneInvalid(value: string | null | undefined) {
  return !!value && !/^[0-9-]{2,20}$/.test(value);
}
