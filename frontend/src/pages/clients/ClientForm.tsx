import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
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
  useEffect(() => {
    if (!client || client.clientTypeCode === "CUST") return;
    let active = true;
    setLoading(true);
    setAccountFailure(false);
    getCommonClientPaymentAccount(client.clientId)
      .then((account) => {
        if (!active) return;
        setOriginal(account);
        setDraft((value) => ({ ...value, ...account }));
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
    if (errors.length) {
      setError(new ClientRequestError(400, "입력 값을 확인해 주세요.", errors));
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
        <small role="alert" key={index}>
          {item.message}
        </small>
      ));
  const input = (
    field: keyof ClientInput,
    label: string,
    max: number,
    required = false,
  ) => (
    <label key={field}>
      {label}
      {required && " *"}
      <input
        data-testid={`client-${field.replace(/[A-Z]/g, (value) => `-${value.toLowerCase()}`)}`}
        aria-label={label}
        value={String(draft[field] ?? "")}
        maxLength={max}
        required={required}
        readOnly={field === "businessNumber" && !!client}
        onChange={(event) =>
          set(
            field,
            field === "businessNumber"
              ? formatBusinessNumber(event.target.value)
              : field === "accountNumber"
                ? event.target.value.replace(/[^0-9-]/g, "")
                : event.target.value,
          )
        }
      />
      {message(field)}
    </label>
  );
  const select = (
    field: "clientTypeCode" | "industryCode" | "bankCode",
    label: string,
    group: string,
    required = false,
  ) => (
    <label>
      {label}
      {required && " *"}
      <select
        aria-label={label}
        data-testid={`client-${group.toLowerCase().replace(/_/g, "-")}-select`}
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
      {message(field)}
    </label>
  );
  return (
    <section
      className="client-form"
      data-testid="client-form"
      data-screen-id="UI-15"
    >
      <h2>{client ? "거래처 수정" : "거래처 등록"}</h2>
      {loading && <p role="status">계좌 조회 중…</p>}
      {select("clientTypeCode", "구분", "CLNT_TYPE", true)}
      {!draft.clientTypeCode ? (
        <p className="client-banner">
          구분을 선택하면 입력할 항목이 나타납니다.
        </p>
      ) : (
        <div className="client-form-grid">
          {input("clientName", "상호", 50, true)}
          {input("businessNumber", "사업자번호", 12, true)}
          {!!client && (
            <small>
              사업자번호는 변경할 수 없습니다. 잘못 등록한 경우 중지 후 새로
              등록해 주세요.
            </small>
          )}
          {input("representativeName", "대표자", 20)}
          {select("industryCode", "업종", "INDUTY")}
          {input("contactName", "담당자", 20)}
          {input("contactPhone", "연락처", 20)}
          {customer && input("taxEmail", "세금계산서 이메일", 100, true)}
          {vendor && (
            <>
              {select("bankCode", "은행", "BANK", true)}
              {input("accountNumber", "계좌번호", 20, true)}
              {input("accountHolder", "예금주", 30, true)}
            </>
          )}
        </div>
      )}
      {error && (
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
      )}
      <footer>
        <button
          data-testid="client-form-cancel"
          disabled={busy}
          onClick={onCancel}
        >
          취소
        </button>
        <button
          className="primary-button"
          data-testid="client-form-save"
          disabled={busy || loading || accountFailure || !draft.clientTypeCode}
          onClick={submit}
        >
          {busy ? "저장 중…" : client ? "저장" : "등록 저장"}
        </button>
      </footer>
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
    </section>
  );
}
