import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useScreenTitleOverride } from "../../shared/layout/ScreenTitleOverride";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import { getCommonParts } from "../parts/parts";
import {
  HQ_PART,
  ProjectRequestError,
  defaultBudget,
  getCommonProjectBudgetRule,
  postCommonProject,
  projectFailure,
  putCommonProject,
  type Project,
  type ProjectBudgetRule,
  type ProjectInput,
} from "./projects";
import "./projects.css";

type Option = [string, string];
const digits = (value: string) => value.replace(/[^0-9]/g, "");
const money = (value: number | null | undefined) => (value == null ? "" : value.toLocaleString("ko-KR"));
/** 시안 문구: 「프로젝트명을 입력해 주세요.」「계약유형을 입력해 주세요.」(받침에 따라 을/를) */
const enter = (label: string) => {
  const code = label.charCodeAt(label.length - 1) - 0xac00;
  const particle = code < 0 || code > 11171 ? "을(를)" : code % 28 === 0 ? "를" : "을";
  return `${label}${particle} 입력해 주세요.`;
};
const LOCK = {
  contractTypeCode: "계약유형은 등록 후 바꿀 수 없습니다. 원가·매출 계산 방식이 달라지기 때문입니다.",
  statusCode: "상태는 여기서 바꿀 수 없습니다. 수주예정이면 프로젝트 상세의 [⋯]에서 진행 또는 실주로 바꿉니다.",
  salesPartId:
    "영업 파트는 여기서 바꿀 수 없습니다. 프로젝트 상세의 영업 파트 [변경]에서 적용 시작 월과 함께 바꿉니다(그 달부터 새 파트로 잡힘).",
};

/** 시안 프로젝트 등록·수정(폼 화면): 제목줄 [‹ 목록], 카드 「기본 정보」·「금액」·「특징」, 아래 [취소][저장]. */
export function ProjectForm({
  project,
  onSaved,
  onClose,
}: {
  project?: Project;
  onSaved: (p: Project) => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState(() => ({
    projectName: project?.projectName ?? "",
    customerId: project?.customerId ?? "",
    contractTypeCode: project?.contractTypeCode ?? "",
    startDate: project?.startDate ?? "",
    endDate: project?.endDate ?? "",
    statusCode: project?.statusCode ?? "ING",
    salesPartId: project?.salesPartId ?? "",
    workplace: project?.workplace ?? "",
    estimateAmount: money(project?.estimateAmount),
    contractAmount: project && project.contractTypeCode !== "IN" ? money(project.contractAmount) : "",
    budgetAmount: money(project?.budgetAmount),
    teamExpenseAmount: money(project?.teamExpenseAmount),
    projectExpenseAmount: money(project?.projectExpenseAmount),
    feature: project?.feature ?? "",
  }));
  type Draft = typeof draft;
  const [options, setOptions] = useState<{ customers: Option[]; types: Option[]; states: Option[]; parts: Option[] }>({
    customers: [],
    types: [],
    states: [],
    parts: [],
  });
  const [fees, setFees] = useState<{ team: number | null; project: number | null }>({ team: null, project: null });
  const [rule, setRule] = useState<ProjectBudgetRule | null>(null);
  const [budgetTouched, setBudgetTouched] = useState(project?.budgetManualYn === "Y");
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const original = useRef(JSON.stringify(draft));
  const dirty = original.current !== JSON.stringify(draft);
  const setDirty = useScreenDirty();
  useEffect(() => {
    setDirty("UI-01", dirty);
    return () => setDirty("UI-01", false);
  }, [dirty, setDirty]);
  const leave = () => (dirty ? setLeaving(true) : onClose());
  // 시안 제목 문구 그대로: 등록 「프로젝트 등록」, 수정 「프로젝트 등록 수정」
  useScreenTitleOverride("UI-01", project ? "프로젝트 등록 수정" : "프로젝트 등록", leave);
  useEffect(() => {
    let active = true;
    Promise.all([
      getCommonCodes("CTRT_TYPE"),
      getCommonCodes("PROJ_STAT"),
      getCommonCodes("ALWNC"),
      getCommonClients(),
      getCommonParts(),
      getCommonProjectBudgetRule().catch(() => null),
    ])
      .then(([types, states, allowances, clients, parts, budgetRule]) => {
        if (!active) return;
        setOptions({
          types: types.items.map((c) => [c.code, c.code === "IN" ? "내부" : c.codeName]),
          states: states.items.filter((c) => ["ING", "PRE"].includes(c.code)).map((c) => [c.code, c.codeName]),
          customers: clients.items
            .filter((c) => (c.useYn === "Y" || c.clientId === project?.customerId) && ["CUST", "BOTH"].includes(c.clientTypeCode))
            .map((c) => [c.clientId, c.clientName]),
          parts: [
            ...parts.items.filter((p) => p.useYn === "Y" || p.partId === project?.salesPartId).map((p): Option => [p.partId, p.partName]),
            [HQ_PART, "본사(직접 영업)"],
          ],
        });
        const fee = (code: string) => {
          const value = allowances.items.find((c) => c.code === code)?.codeVal;
          return value == null ? null : Number(value);
        };
        setFees({ team: fee("TEAM_FEE"), project: fee("PEXP_FEE") });
        setRule(budgetRule);
        if (!project) {
          const start = {
            ...draft,
            teamExpenseAmount: money(fee("TEAM_FEE")),
            projectExpenseAmount: money(fee("PEXP_FEE")),
          };
          original.current = JSON.stringify(start);
          setDraft(start);
        }
        setLoaded(true);
      })
      .catch((e) => active && setError(projectFailure(e)));
    return () => {
      active = false;
    };
  }, [project]);
  const internal = draft.contractTypeCode === "IN";
  const change = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    setDraft((d) => {
      const next = { ...d, [key]: value };
      // 시안: 계약금액을 넣으면 예산 기본값(식-19)을 채운다. 사람이 예산을 고친 뒤에는 덮어쓰지 않는다.
      if (key === "contractAmount" && !budgetTouched) {
        const amount = Number(digits(String(value)));
        next.budgetAmount = amount ? money(defaultBudget(amount, rule)) : "";
      }
      return next;
    });
    if (key === "budgetAmount") setBudgetTouched(true);
    setErrors((e) => {
      if (!e[key as string]) return e;
      const next = { ...e };
      delete next[key as string];
      return next;
    });
  };
  const validate = () => {
    const found: Record<string, string> = {};
    if (!draft.projectName.trim()) found.projectName = enter("프로젝트명");
    if (!internal && !draft.customerId) found.customerId = enter("고객사");
    if (!draft.contractTypeCode) found.contractTypeCode = enter("계약유형");
    if (!draft.startDate) found.startDate = enter("계약 시작일");
    if (!draft.endDate) found.endDate = enter("계약 종료일");
    else if (draft.startDate && draft.endDate < draft.startDate) found.endDate = "종료일이 시작일보다 앞입니다. 날짜를 다시 확인해 주세요.";
    if (!project && !internal && !draft.salesPartId) found.salesPartId = enter("영업 파트");
    if (!internal && !digits(draft.contractAmount)) found.contractAmount = enter("계약금액");
    setErrors(found);
    const first = Object.keys(found)[0];
    if (first) document.getElementById(`project-${first}`)?.focus();
    return !first;
  };
  const amount = (value: string) => (digits(value) ? Number(digits(value)) : undefined);
  const save = async () => {
    if (saving.current || !loaded || !validate()) return;
    const body: ProjectInput = {
      projectName: draft.projectName.trim(),
      startDate: draft.startDate,
      endDate: draft.endDate,
      feature: draft.feature,
      estimateAmount: amount(draft.estimateAmount),
      contractAmount: internal ? amount(draft.contractAmount) ?? 0 : amount(draft.contractAmount),
      teamExpenseAmount: amount(draft.teamExpenseAmount),
      projectExpenseAmount: amount(draft.projectExpenseAmount),
    };
    if (project) body.modifiedAt = project.modifiedAt ?? null;
    if (!project) {
      body.contractTypeCode = draft.contractTypeCode;
      body.statusCode = draft.statusCode;
      if (!internal) body.salesPartId = draft.salesPartId;
    }
    if (!internal) {
      body.customerId = draft.customerId;
      body.workplace = draft.workplace;
      if (budgetTouched && digits(draft.budgetAmount)) body.budgetAmount = amount(draft.budgetAmount);
    }
    saving.current = true;
    setBusy(true);
    setError("");
    try {
      const saved = project ? await putCommonProject(project.projectId, body) : await postCommonProject(body);
      original.current = JSON.stringify(draft);
      setDirty("UI-01", false);
      onSaved(saved);
    } catch (e) {
      setError(projectFailure(e));
      if (e instanceof ProjectRequestError) setErrors(Object.fromEntries(e.errors.map((f) => [f.field, f.message])));
    } finally {
      saving.current = false;
      setBusy(false);
    }
  };
  const currentSave = useRef(save);
  currentSave.current = save;
  useEffect(() => {
    // 공통 기능: Ctrl+S로 저장
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        void currentSave.current();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
  const fld = (key: string, label: string, required: boolean, control: ReactNode, help?: ReactNode, full = false) => (
    <div className={`project-fld${full ? " project-full" : ""}`} key={key}>
      <label className={`project-fl${required ? " req" : ""}`} htmlFor={`project-${key}`}>
        {label}
      </label>
      <div className="project-fv">
        {control}
        {help && <span className="project-help">{help}</span>}
        {errors[key] && (
          <small role="alert" className="field-error">
            {errors[key]}
          </small>
        )}
      </div>
    </div>
  );
  /** 등록 뒤 바꿀 수 없는 칸(시안 lockEdit): 회색 읽기 전용 + 안내. */
  const locked = (key: keyof typeof LOCK, label: string, text: string) =>
    fld(key, label, true, <input id={`project-${key}`} data-testid={`project-${key}`} className="ls-in" value={text} readOnly tabIndex={-1} />, LOCK[key]);
  const select = (key: keyof Draft, label: string, required: boolean, items: Option[], help?: string, placeholder = true) =>
    fld(
      key,
      label,
      required,
      <select
        id={`project-${key}`}
        data-testid={`project-${key}`}
        className="ls-in"
        aria-invalid={!!errors[key]}
        value={draft[key]}
        disabled={busy}
        onChange={(e) => change(key, e.target.value)}
      >
        {placeholder && <option value="">선택</option>}
        {items.map(([value, name]) => (
          <option key={value} value={value}>
            {name}
          </option>
        ))}
      </select>,
      help,
    );
  const text = (key: "projectName" | "workplace", label: string, required: boolean, max: number, help?: string) =>
    fld(
      key,
      label,
      required,
      <input
        id={`project-${key}`}
        data-testid={`project-${key}`}
        className="ls-in project-wide"
        aria-invalid={!!errors[key]}
        maxLength={max}
        value={draft[key]}
        disabled={busy}
        onChange={(e) => change(key, e.target.value)}
      />,
      help,
      true,
    );
  const date = (key: "startDate" | "endDate", label: string) =>
    fld(
      key,
      label,
      true,
      <input
        id={`project-${key}`}
        data-testid={`project-${key}`}
        className="ls-in project-date"
        type="date"
        aria-invalid={!!errors[key]}
        value={draft[key]}
        disabled={busy}
        onChange={(e) => change(key, e.target.value)}
      />,
    );
  const moneyField = (
    key: "estimateAmount" | "contractAmount" | "budgetAmount" | "teamExpenseAmount" | "projectExpenseAmount",
    label: string,
    required: boolean,
    help?: string,
  ) =>
    fld(
      key,
      label,
      required,
      <span className="project-row">
        <input
          id={`project-${key}`}
          data-testid={`project-${key}`}
          className="ls-in project-money"
          inputMode="numeric"
          aria-invalid={!!errors[key]}
          value={draft[key]}
          disabled={busy}
          onChange={(e) => {
            const plain = digits(e.target.value);
            change(key, plain ? Number(plain).toLocaleString("ko-KR") : "");
          }}
        />
        <span className="project-unit">원</span>
      </span>,
      help,
    );
  const nameOf = (items: Option[], value: string) => items.find(([v]) => v === value)?.[1] ?? value;
  const fee = (value: number | null) => (value == null ? "" : `${value.toLocaleString("ko-KR")}원`);
  const margin = rule
    ? `목표 마진율 ${Math.round(rule.targetMarginRate)}%면 ${Math.round(rule.budgetFactor * 100)}%`
    : "목표 마진율 20%면 80%";
  return (
    <section className="project-form" data-testid="project-form" data-screen-id="UI-01">
      <section className="project-card">
        <h2>기본 정보</h2>
        <div className="project-fgrid">
          {text("projectName", "프로젝트명", true, 100)}
          {!internal &&
            select("customerId", "고객사", true, options.customers)}
          {project
            ? locked("contractTypeCode", "계약유형", nameOf(options.types, draft.contractTypeCode) || project.contractTypeName)
            : select("contractTypeCode", "계약유형", true, options.types)}
          {date("startDate", "계약 시작일")}
          {date("endDate", "계약 종료일")}
          {project
            ? locked("statusCode", "상태", project.statusName)
            : select(
                "statusCode",
                "상태",
                true,
                options.states,
                "계약 전에 사람부터 보내면 「수주예정」. 종료는 계약 종료일이 지나면 자동으로 바뀝니다. 등록한 뒤에는 수주예정일 때만 프로젝트 상세의 [⋯]에서 진행 또는 실주로 바꿉니다.",
                false,
              )}
          {!internal &&
            (project
              ? locked("salesPartId", "영업 파트", project.salesPartId ? nameOf(options.parts, project.salesPartId) : "미지정")
              : select(
                  "salesPartId",
                  "영업 파트",
                  true,
                  options.parts,
                  "본사가 직접 영업했으면 「본사(직접 영업)」 — 파트 이익금 정산에서 빠집니다. 내부(자체 개발)는 이 칸이 없습니다.",
                ))}
          {!internal &&
            text(
              "workplace",
              "근무지",
              false,
              200,
              "근무지 주소 전체를 적습니다(예: 서울 중구 을지로 66). 맨 앞 시·도로 AI 인력 추천의 근무지 점수를 매깁니다.",
            )}
        </div>
      </section>
      <section className="project-card">
        <h2>금액</h2>
        <div className="project-fgrid">
          {moneyField("estimateAmount", "견적 금액", false)}
          {moneyField(
            "contractAmount",
            "계약금액",
            !internal,
            "부가세 제외. 내부(자체 개발)는 필수가 아니며 비우면 0으로 저장되고 매출로 잡히지 않습니다.",
          )}
          {moneyField(
            "budgetAmount",
            "예산(목표 원가)",
            false,
            `기본값은 계약금액 × (1 − 목표 마진율)입니다(공통코드 › 운영 기준값, ${margin}). 실제 승인된 예산이 있으면 고쳐서 입력하세요.`,
          )}
          {moneyField(
            "teamExpenseAmount",
            "팀비(월)",
            false,
            `기본값 ${fee(fees.team)}(공통코드 UI-27). 회식비. 자사 인력 1인당 월 금액이라 1.0MM 투입이면 이 금액, 0.5MM이면 절반이 그 사람 원가에 붙습니다(외주 제외).`,
          )}
          {moneyField(
            "projectExpenseAmount",
            "프로젝트경비(월)",
            false,
            `기본값 ${fee(fees.project)}(공통코드 UI-27). 물품 구매비. 자사 인력 1인당 월 금액이라 1.0MM 투입이면 이 금액, 0.5MM이면 절반이 그 사람 원가에 붙습니다(외주 제외).`,
          )}
        </div>
      </section>
      <section className="project-card">
        <h2>특징</h2>
        <div className="project-fgrid">
          {fld(
            "feature",
            "특징",
            false,
            <>
              <textarea
                id="project-feature"
                data-testid="project-feature"
                className="ls-in project-area"
                maxLength={500}
                value={draft.feature}
                disabled={busy}
                onChange={(e) => change("feature", e.target.value)}
              />
              <span className="project-counter" data-testid="project-feature-count">
                {draft.feature.length}/500
              </span>
            </>,
            "일정·업무 특징을 적어 두는 메모입니다. 추천 점수(직무·등급·공백·고객사·수주 확률)에는 쓰지 않습니다.",
            true,
          )}
        </div>
      </section>
      {error && (
        <div role="alert" className="project-error" data-testid="project-form-error">
          {error}
        </div>
      )}
      <p className="project-note">
        <span className="project-bad">*</span> 표시는 꼭 입력해야 하는 항목입니다.
      </p>
      <footer className="project-form-foot">
        <button type="button" className="ls-btn" data-testid="project-cancel" disabled={busy} onClick={leave}>
          취소
        </button>
        <button type="button" className="ls-btn ls-btn-p" data-testid="project-save" disabled={busy || !loaded} onClick={() => void save()}>
          {busy ? "저장 중…" : "저장"}
        </button>
      </footer>
      {leaving && (
        <Dialog title="저장하지 않은 내용이 있습니다" closable testId="project-leave-confirm" onClose={() => setLeaving(false)}>
          <p>나가면 입력한 내용이 사라집니다.</p>
          <div className="app-actions">
            <button type="button" data-testid="project-leave-cancel" onClick={() => setLeaving(false)}>
              취소
            </button>
            <button type="button" className="primary-button" data-testid="project-leave-go" onClick={onClose}>
              나가기
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
