import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import * as api from "./parts";
import "./parts.css";

type Mode = "create" | "standards" | "rename" | "stop";
export interface PartDialogProps {
  part?: api.Part;
  onClose: () => void;
  onSaved: (partId?: string) => void;
}
interface Draft {
  partName: string;
  effectiveMonth: string;
  leaderUserId: string;
  distributionRate: string;
  salesExpense: string;
}
const titles: Record<Mode, string> = {
  create: "영업 파트 추가",
  standards: "기준 변경",
  rename: "파트명 수정",
  stop: "파트 사용 중지",
};
const screenIds: Record<Mode, string> = {
  create: "UI-15-M1",
  standards: "UI-15-M5",
  rename: "UI-15-M6",
  stop: "UI-15-M8",
};
const fromPart = (part?: api.Part): Draft => ({
  partName: part?.partName ?? "",
  effectiveMonth: part?.effectiveMonth ?? "",
  leaderUserId: part?.leaderUserId ?? "",
  distributionRate: part ? String(part.distributionRate) : "",
  salesExpense: part ? String(part.salesExpense) : "",
});

export function PartEditDialog({
  mode,
  part,
  onClose,
  onSaved,
}: PartDialogProps & { mode: Mode }) {
  const [draft, setDraft] = useState<Draft>(() => fromPart(part));
  const initial = useRef<Draft>(fromPart(part));
  const [options, setOptions] = useState<api.PartOptions | null>(null);
  const [standards, setStandards] = useState<api.PartStandard[]>([]);
  const [token, setToken] = useState(part?.modifiedAt ?? null);
  const [currentPart, setCurrentPart] = useState(part);
  const [loading, setLoading] = useState(
    mode === "create" || mode === "standards",
  );
  const [readError, setReadError] = useState("");
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [stale, setStale] = useState(false);
  const [closing, setClosing] = useState(false);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const initialized = useRef(false);
  const scope = useRef<HTMLFormElement>(null);
  const setDirty = useScreenDirty();
  const periodMode = mode === "create" || mode === "standards";
  const dirty = JSON.stringify(draft) !== JSON.stringify(initial.current);
  useEffect(() => {
    setDirty("UI-15", dirty);
    return () => setDirty("UI-15", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    if (!periodMode) return;
    const controller = new AbortController();
    setLoading(true);
    setReadError("");
    void Promise.all([
      api.getCommonPartOptions(controller.signal),
      mode === "standards" && part
        ? api.getCommonPartStandards(part.partId, controller.signal)
        : Promise.resolve({ items: [] }),
    ])
      .then(([nextOptions, history]) => {
        if (controller.signal.aborted) return;
        setOptions(nextOptions);
        setStandards(history.items);
        if (history.items.length) setToken(history.items[0].modifiedAt);
        if (!initialized.current) {
          const applicable = history.items
            .filter(
              (row) =>
                row.effectiveMonth <= nextOptions.earliestMonth &&
                (!row.endMonth || row.endMonth >= nextOptions.earliestMonth),
            )
            .at(-1);
          const next =
            mode === "create"
              ? {
                  ...fromPart(),
                  effectiveMonth: nextOptions.earliestMonth,
                  distributionRate: String(nextOptions.distributionRate),
                  salesExpense: String(nextOptions.salesExpense),
                }
              : {
                  ...fromPart(part),
                  effectiveMonth: nextOptions.earliestMonth,
                  ...(applicable
                    ? {
                        leaderUserId: applicable.leaderUserId,
                        distributionRate: String(applicable.distributionRate),
                        salesExpense: String(applicable.salesExpense),
                      }
                    : {}),
                };
          initial.current = next;
          setDraft(next);
          initialized.current = true;
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted)
          setReadError(api.partErrorMessage(failure));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [mode, part, periodMode, revision]);
  const close = () => {
    if (busyRef.current) return;
    if (closing) setClosing(false);
    else if (dirty) setClosing(true);
    else onClose();
  };
  const refresh = async () => {
    if (!part || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      const result = await api.getCommonParts();
      const latest = result.items.find((item) => item.partId === part.partId);
      if (!latest) throw new Error("파트를 찾을 수 없습니다.");
      setToken(latest.modifiedAt);
      setCurrentPart(latest);
      if (mode === "standards")
        setStandards((await api.getCommonPartStandards(part.partId)).items);
      setStale(false);
      setError(
        "최신 기준을 불러왔습니다. 입력 내용을 확인하고 다시 저장해 주세요.",
      );
    } catch (failure) {
      setError(api.partErrorMessage(failure));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const blockedStop =
    mode === "stop" &&
    currentPart &&
    (currentPart.personCount > 0 || currentPart.projectCount > 0);
  const save = async () => {
    if (
      busyRef.current ||
      loading ||
      readError ||
      closing ||
      blockedStop ||
      stale
    )
      return;
    const invalid: Record<string, string> = {};
    if (
      (mode === "create" || mode === "rename") &&
      (!draft.partName.trim() || [...draft.partName.trim()].length > 30)
    )
      invalid.partName = "파트명은 1~30자로 입력해 주세요.";
    if (periodMode) {
      if (
        !/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(draft.effectiveMonth) ||
        draft.effectiveMonth.startsWith("0000") ||
        (options && draft.effectiveMonth < options.earliestMonth)
      )
        invalid.effectiveMonth = `${options?.earliestMonth ?? "허용월"}부터 선택해 주세요.`;
      if (
        !draft.leaderUserId ||
        !options?.leaders.some((row) => row.userId === draft.leaderUserId)
      )
        invalid.leaderUserId = "사용 중인 영업 파트장을 선택해 주세요.";
      if (
        !/^\d+(\.\d{1,2})?$/.test(draft.distributionRate) ||
        Number(draft.distributionRate) > 100
      )
        invalid.distributionRate =
          "배분율은 0~100, 소수 둘째 자리까지 입력해 주세요.";
      if (!/^\d{1,15}$/.test(draft.salesExpense))
        invalid.salesExpense =
          "공통관리비는 15자리 이내의 정수로 입력해 주세요.";
    }
    setFields(invalid);
    setError("");
    if (Object.keys(invalid).length) {
      scope.current
        ?.querySelector<HTMLElement>(`[name="${Object.keys(invalid)[0]}"]`)
        ?.focus();
      return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      const values: api.PartValues = {
        effectiveMonth: draft.effectiveMonth,
        leaderUserId: draft.leaderUserId,
        distributionRate: Number(draft.distributionRate),
        salesExpense: Number(draft.salesExpense),
      };
      let savedId = part?.partId;
      if (mode === "create")
        savedId = (
          await api.postCommonPart({
            ...values,
            partName: draft.partName.trim(),
          })
        ).partId;
      else if (part && mode === "standards")
        await api.putCommonPartStandards(part.partId, {
          ...values,
          modifiedAt: token,
        });
      else if (part && mode === "rename")
        await api.putCommonPart(part.partId, {
          partName: draft.partName.trim(),
          modifiedAt: token,
        });
      else if (part) await api.putCommonPartStop(part.partId, token);
      setDirty("UI-15", false);
      onSaved(savedId);
    } catch (failure) {
      setError(api.partErrorMessage(failure));
      if (failure instanceof api.PartRequestError) {
        setFields(
          Object.fromEntries(
            failure.errors.map((item) => [item.field, item.message]),
          ),
        );
        setStale(failure.status === 409);
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  usePreferenceBusinessShortcuts(
    {
      onSave: () => {
        void save();
      },
      dialogTarget: scope,
    },
    !closing,
  );
  const field = (key: keyof Draft, label: string, control: ReactNode, help?: string, unit?: string) => (
    <label className="part-field" key={key}>
      <span>
        {label}
        <span className="field-required"> *</span>
      </span>
      <span className="part-control">
        {control}
        {unit && <span className="part-unit">{unit}</span>}
      </span>
      {help && <span className="part-help">{help}</span>}
      {fields[key] && <small role="alert">{fields[key]}</small>}
    </label>
  );
  const change = (key: keyof Draft, value: string) =>
    setDraft((old) => ({ ...old, [key]: value }));
  const changeMonth = (month: string) => {
    const row = standards
      .filter(
        (value) =>
          value.effectiveMonth <= month &&
          (!value.endMonth || value.endMonth >= month),
      )
      .at(-1);
    setDraft((old) =>
      row
        ? {
            ...old,
            effectiveMonth: month,
            leaderUserId: row.leaderUserId,
            distributionRate: String(row.distributionRate),
            salesExpense: String(row.salesExpense),
          }
        : { ...old, effectiveMonth: month },
    );
  };
  return (
    <Dialog
      title={mode === "standards" && part ? `${part.partName} 기준 변경` : titles[mode]}
      onClose={close}
      closable
      testId={`part-${mode}-dialog`}
    >
      <form
        ref={scope}
        className="part-form"
        data-testid={`part-${mode}-form`}
        data-screen-id={screenIds[mode]}
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        {loading && (
          <p role="status">기준과 파트장 선택지를 불러오는 중입니다.</p>
        )}
        {readError && (
          <div role="alert">
            {readError}
            <button
              type="button"
              data-testid="part-options-retry"
              onClick={() => setRevision((value) => value + 1)}
            >
              다시 시도
            </button>
          </div>
        )}
        <fieldset disabled={busy || loading || !!readError}>
          {(mode === "create" || mode === "rename") &&
            field(
              "partName",
              "파트명",
              <input
                name="partName"
                data-testid="part-name-input"
                placeholder="예: 영업4파트"
                value={draft.partName}
                aria-invalid={!!fields.partName}
                onChange={(event) => change("partName", event.target.value)}
              />,
              mode === "rename" ? "소속 인력·프로젝트·사업계획의 파트명도 함께 바뀝니다." : undefined,
            )}
          {periodMode && (
            <>
              {field(
                "effectiveMonth",
                "적용 시작월",
                <input
                  type="month"
                  name="effectiveMonth"
                  data-testid="part-month-input"
                  min={options?.earliestMonth}
                  value={draft.effectiveMonth}
                  aria-invalid={!!fields.effectiveMonth}
                  onChange={(event) => changeMonth(event.target.value)}
                />,
                options?.closedMonth
                  ? `마감된 달(${options.closedMonth}) 다음 달부터 고를 수 있습니다.`
                  : `${options?.earliestMonth ?? ""}부터 고를 수 있습니다.`,
              )}
              {field(
                "leaderUserId",
                "파트장",
                <select
                  name="leaderUserId"
                  aria-label="파트장"
                  data-testid="part-leader-select"
                  value={draft.leaderUserId}
                  aria-invalid={!!fields.leaderUserId}
                  onChange={(event) =>
                    change("leaderUserId", event.target.value)
                  }
                >
                  <option value="">선택</option>
                  {options?.leaders.map((leader) => (
                    <option key={leader.userId} value={leader.userId}>
                      {leader.userName}
                    </option>
                  ))}
                </select>,
                "영업 파트장 역할인 사용자만 나옵니다. 적용 시작월부터 이 사람이 파트 데이터를 보고 미수 2단계 알림을 받습니다. 시스템 › 사용자에서는 고치지 않습니다.",
              )}
              {options && !options.leaders.length && (
                <p>선택할 수 있는 영업 파트장이 없습니다.</p>
              )}
              {field(
                "distributionRate",
                "이익 배분율",
                <input
                  name="distributionRate"
                  inputMode="decimal"
                  data-testid="part-rate-input"
                  value={draft.distributionRate}
                  aria-invalid={!!fields.distributionRate}
                  onChange={(event) =>
                    change(
                      "distributionRate",
                      event.target.value.replace(/[^0-9.]/g, ""),
                    )
                  }
                />,
                undefined,
                "%",
              )}
              {field(
                "salesExpense",
                "공통관리비 (자사 인력 1인·월)",
                <input
                  name="salesExpense"
                  inputMode="numeric"
                  data-testid="part-expense-input"
                  value={draft.salesExpense.replace(
                    /\B(?=(\d{3})+(?!\d))/g,
                    ",",
                  )}
                  aria-invalid={!!fields.salesExpense}
                  onChange={(event) =>
                    change(
                      "salesExpense",
                      event.target.value.replace(/\D/g, ""),
                    )
                  }
                />,
                undefined,
                "원",
              )}
            </>
          )}
          {mode === "stop" && (
            <p>
              {part?.partName} 사용을 중지할까요? 기존 기준과 기록은 남습니다.
            </p>
          )}
          {blockedStop && (
            <p role="alert">
              소속 인력과 진행 프로젝트를 먼저 다른 파트로 이동해 주세요.
            </p>
          )}
        </fieldset>
        {error && (
          <p role="alert" data-testid="part-save-error">
            {error}
          </p>
        )}
        {stale && (
          <button
            type="button"
            data-testid="part-refresh-version"
            disabled={busy}
            onClick={() => {
              void refresh();
            }}
          >
            최신 기준 다시 불러오기 (입력 유지)
          </button>
        )}
        {closing ? (
          <InlineConfirmation
            title="나가시겠습니까?"
            description="저장하지 않은 내용이 사라집니다."
            onCancel={() => setClosing(false)}
            onProceed={onClose}
          />
        ) : (
          <div className="app-actions">
            <button
              type="button"
              data-testid="part-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              data-testid="part-save"
              className={mode === "stop" ? "app-danger" : "primary-button"}
              disabled={
                busy ||
                loading ||
                !!readError ||
                !!blockedStop ||
                stale ||
                (periodMode && !options?.leaders.length)
              }
            >
              {busy
                ? "저장 중…"
                : mode === "create"
                  ? "추가"
                  : mode === "stop"
                    ? "사용 중지"
                    : "저장"}
            </button>
          </div>
        )}
      </form>
    </Dialog>
  );
}
