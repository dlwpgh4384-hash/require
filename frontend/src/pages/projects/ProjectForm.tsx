import { useEffect, useState } from "react";
import { ConfirmDialog } from "../../shared/feedback/Dialog";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import { getCommonParts } from "../parts/parts";
import { getCommonOperatingStandards } from "../operatingstandards/operatingStandards";
import {
  postCommonProject,
  putCommonProject,
  ProjectRequestError,
  projectFailure,
  projectToday,
  type Project,
  type ProjectInput,
} from "./projects";

type Option = { value: string; label: string };
export function ProjectForm({
  project,
  onSaved,
  onClose,
}: {
  project?: Project;
  onSaved: (p: Project) => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState<Record<string, string>>({
    projectName: project?.projectName ?? "",
    customerId: project?.customerId ?? "",
    contractTypeCode: project?.contractTypeCode ?? "",
    statusCode: project?.statusCode ?? "ING",
    salesPartId: project?.salesPartId ?? "",
    startDate: project?.startDate ?? projectToday(),
    endDate: project?.endDate ?? projectToday(),
    workplace: project?.workplace ?? "",
    estimateAmount: project?.estimateAmount?.toString() ?? "",
    contractAmount: project?.contractAmount?.toString() ?? "",
    budgetAmount: project?.budgetAmount?.toString() ?? "",
    teamExpenseAmount: project?.teamExpenseAmount?.toString() ?? "",
    projectExpenseAmount: project?.projectExpenseAmount?.toString() ?? "",
    feature: project?.feature ?? "",
  });
  const [options, setOptions] = useState<Record<string, Option[]>>({});
  const [margin, setMargin] = useState<number | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [manual, setManual] = useState(false);
  const [closing, setClosing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      getCommonCodes("CTRT_TYPE"),
      getCommonCodes("PROJ_STAT"),
      getCommonCodes("ALWNC"),
      getCommonClients(),
      getCommonParts(),
      getCommonOperatingStandards(),
    ])
      .then(([types, states, allowances, clients, parts, standards]) => {
        if (cancelled) return;
        const codes = (items: { code: string; codeName: string }[]) =>
          items.map((c) => ({ value: c.code, label: c.codeName }));
        setOptions({
          contractTypeCode: codes(types.items),
          statusCode: codes(
            states.items.filter((c) => ["ING", "PRE"].includes(c.code)),
          ),
          customerId: clients.items
            .filter(
              (c) =>
                c.useYn === "Y" && ["CUST", "BOTH"].includes(c.clientTypeCode),
            )
            .map((c) => ({ value: c.clientId, label: c.clientName })),
          salesPartId: parts.items
            .filter((p) => p.useYn === "Y")
            .map((p) => ({ value: p.partId, label: p.partName })),
        });
        if (!project)
          setForm((f) => ({
            ...f,
            teamExpenseAmount:
              allowances.items
                .find((c) => c.code === "TEAM_FEE")
                ?.codeVal?.toString() ?? "",
            projectExpenseAmount:
              allowances.items
                .find((c) => c.code === "PEXP_FEE")
                ?.codeVal?.toString() ?? "",
          }));
        const applicable = standards.items
          .filter(
            (s) =>
              s.standardCode === "TGT_MRGN_RT" &&
              (!s.effectiveMonth ||
                s.effectiveMonth <= projectToday().slice(0, 7)),
          )
          .sort((a, b) =>
            (b.effectiveMonth ?? "").localeCompare(a.effectiveMonth ?? ""),
          )[0];
        setMargin(applicable ? Number(applicable.value) : null);
        setLoaded(true);
      })
      .catch((e) => !cancelled && setError(projectFailure(e)));
    return () => {
      cancelled = true;
    };
  }, [project]);
  const internal = form.contractTypeCode === "IN";
  const automatic =
    form.contractAmount && margin != null
      ? Math.floor(Number(form.contractAmount) * (1 - margin / 100)).toString()
      : "";
  const contractChanged =
    !project || Number(form.contractAmount) !== project.contractAmount;
  const budget =
    manual || project?.budgetManualYn === "Y" || !contractChanged
      ? form.budgetAmount
      : automatic;
  const change = (key: string, value: string) => {
    setDirty(true);
    if (key === "budgetAmount") setManual(true);
    setForm((f) => ({ ...f, [key]: value }));
  };
  const close = () => (dirty ? setClosing(true) : onClose());
  const submit = async () => {
    if (busy || !loaded) return;
    const errors: Record<string, string> = {};
    if (!form.projectName.trim() || [...form.projectName].length > 100)
      errors.projectName = "프로젝트명은 필수이며 100자 이내입니다.";
    if (form.endDate < form.startDate)
      errors.endDate = "종료일은 시작일 이후여야 합니다.";
    if (form.workplace.length > 200)
      errors.workplace = "근무지는 200자 이내입니다.";
    if (form.feature.length > 500) errors.feature = "특징은 500자 이내입니다.";
    if (!project && !form.contractTypeCode)
      errors.contractTypeCode = "계약유형을 선택해 주세요.";
    if (!project && !internal && !form.salesPartId)
      errors.salesPartId = "영업 파트를 선택해 주세요.";
    if (!internal && !form.customerId)
      errors.customerId = "고객사를 선택해 주세요.";
    if (!internal && !form.contractAmount)
      errors.contractAmount = "계약금액을 입력해 주세요.";
    if (Object.keys(errors).length) {
      setFields(errors);
      return;
    }
    const body: ProjectInput = {
      projectName: form.projectName,
      startDate: form.startDate,
      endDate: form.endDate,
      feature: form.feature,
    };
    if (!project) {
      body.contractTypeCode = form.contractTypeCode;
      body.statusCode = form.statusCode;
      if (!internal && form.salesPartId) body.salesPartId = form.salesPartId;
    }
    if (!internal) {
      body.customerId = form.customerId;
      body.workplace = form.workplace;
    }
    for (const key of [
      "estimateAmount",
      "contractAmount",
      "teamExpenseAmount",
      "projectExpenseAmount",
    ] as const)
      if (form[key] !== "") body[key] = Number(form[key]);
    if (!internal && manual && form.budgetAmount !== "")
      body.budgetAmount = Number(form.budgetAmount);
    setBusy(true);
    setError("");
    setFields({});
    try {
      onSaved(
        project
          ? await putCommonProject(project.projectId, body)
          : await postCommonProject(body),
      );
    } catch (e) {
      setError(projectFailure(e));
      if (e instanceof ProjectRequestError)
        setFields(
          Object.fromEntries(e.errors.map((f) => [f.field, f.message])),
        );
    } finally {
      setBusy(false);
    }
  };
  const input = (
    key: string,
    label: string,
    type = "text",
    maxLength?: number,
  ) => (
    <label key={key}>
      {label}
      <input
        data-testid={`project-${key.replace(/[A-Z]/g, (s) => `-${s.toLowerCase()}`)}`}
        aria-invalid={!!fields[key]}
        type={type}
        maxLength={maxLength}
        min={type === "number" ? 0 : undefined}
        step={type === "number" ? 1 : undefined}
        value={key === "budgetAmount" ? budget : form[key]}
        disabled={busy || !loaded}
        onChange={(e) => change(key, e.target.value)}
      />
      {fields[key] && <small role="alert">{fields[key]}</small>}
    </label>
  );
  const select = (key: string, label: string) => (
    <label key={key}>
      {label}
      <select
        data-testid={`project-${key.replace(/[A-Z]/g, (s) => `-${s.toLowerCase()}`)}`}
        value={form[key]}
        disabled={busy || !loaded}
        onChange={(e) => change(key, e.target.value)}
      >
        <option value="">선택</option>
        {options[key]?.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {fields[key] && <small role="alert">{fields[key]}</small>}
    </label>
  );
  return (
    <section
      className="project-page"
      data-testid="project-form-workspace"
      data-screen-id="UI-01"
    >
      <h2>{project ? "프로젝트 수정" : "프로젝트 등록"}</h2>
      <form
        className="project-form"
        data-testid="project-form"
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        {!loaded && !error && <p role="status">입력 정보를 불러오는 중…</p>}
        {error && <p role="alert">{error}</p>}
        {input("projectName", "프로젝트명", "text", 100)}
        {project ? (
          <>
            <label>
              계약유형
              <input
                data-testid="project-type-readonly"
                className="project-readonly"
                readOnly
                value={project.contractTypeName}
              />
            </label>
            <label>
              상태
              <input
                data-testid="project-state-readonly"
                className="project-readonly"
                readOnly
                value={project.statusName}
              />
            </label>
            <label>
              영업 파트
              <input
                data-testid="project-part-readonly"
                className="project-readonly"
                readOnly
                value={project.salesPartName ?? "미지정"}
              />
            </label>
            <p>
              계약유형은 변경할 수 없습니다. 상태와 영업 파트는 상세 화면에서
              변경해 주세요. 진행에서 수주예정으로 되돌릴 수 없습니다.
            </p>
          </>
        ) : (
          <>
            {select("contractTypeCode", "계약유형")}
            {select("statusCode", "상태")}
            {!internal && select("salesPartId", "영업 파트")}
          </>
        )}
        {!internal && (
          <>
            {select("customerId", "고객사")}
            {input("workplace", "근무지", "text", 200)}
          </>
        )}
        {input("startDate", "시작일", "date")}
        {input("endDate", "종료일", "date")}
        {input("estimateAmount", "견적 금액", "number")}
        {input("contractAmount", "계약금액", "number")}
        {!internal && (
          <>
            {input("budgetAmount", "예산(목표원가)", "number")}
            <small>
              {manual || project?.budgetManualYn === "Y"
                ? "직접 입력한 예산은 계약금액이 바뀌어도 유지됩니다."
                : "자동 예산은 저장할 때 최신 DB 계산식으로 확정됩니다."}
            </small>
          </>
        )}
        {input("teamExpenseAmount", "팀비(1인·월)", "number")}
        {input("projectExpenseAmount", "프로젝트경비(1인·월)", "number")}
        <label>
          특징
          <textarea
            data-testid="project-feature"
            maxLength={500}
            value={form.feature}
            disabled={busy}
            onChange={(e) => change("feature", e.target.value)}
          />
          {fields.feature && (
            <small className="field-error">{fields.feature}</small>
          )}
        </label>
        <footer>
          <button
            type="button"
            data-testid="project-form-cancel"
            disabled={busy}
            onClick={close}
          >
            취소
          </button>
          <button
            className="primary-button"
            data-testid="project-form-save"
            disabled={busy || !loaded}
          >
            저장
          </button>
        </footer>
      </form>
      {closing && (
        <ConfirmDialog
          title="변경 내용 버리기"
          description="저장하지 않은 변경 내용을 버릴까요?"
          proceedLabel="버리기"
          onCancel={() => setClosing(false)}
          onProceed={onClose}
        />
      )}
    </section>
  );
}
