import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DetailPanel, Banner } from "../../shared/list/DetailPanel";
import { Dialog } from "../../shared/feedback/Dialog";
import { koreanToday } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import {
  getCommonDevelopers,
  getCommonDeveloper,
  type DeveloperListRow,
} from "../developers/developers";
import { ProjectRequestError, type Project } from "../projects/projects";
import {
  assignmentInitialDates,
  assignmentPriceEditable,
  getCommonAssignmentContractDevelopers,
  postCommonAssignment,
  type Assignment,
  type AssignmentCandidate,
  type AssignmentContractDeveloper,
  type AssignmentInput,
} from "./assignments";

type Option = { code: string; codeName: string };
export function AssignmentForm({
  projects,
  candidate,
  roles,
  onClose,
  onSaved,
  recommendedMonth,
}: {
  projects: Project[];
  candidate?: AssignmentCandidate;
  roles: readonly string[];
  onClose: () => void;
  onSaved: (row: Assignment) => void;
  recommendedMonth?: string;
}) {
  const navigate = useNavigate();
  const initial =
    projects.find((p) => p.projectId === candidate?.projectId) ?? projects[0];
  const [draft, setDraft] = useState<AssignmentInput>(() => ({
    projectId: initial?.projectId ?? "",
    developerId: candidate?.developerId ?? "",
    jobCode: candidate?.jobCode ?? "",
    ...(initial
      ? assignmentInitialDates(koreanToday(), initial, false, recommendedMonth)
      : { startDate: koreanToday(), endDate: "" }),
    ...(candidate
      ? { startDate: candidate.startDate, endDate: candidate.endDate }
      : {}),
    monthlyMm: 1,
    inputTypeCode: "NEW",
  }));
  const [people, setPeople] = useState<DeveloperListRow[]>([]);
  const [jobs, setJobs] = useState<Option[]>([]),
    [types, setTypes] = useState<Option[]>([]),
    [reasons, setReasons] = useState<Option[]>([]);
  const [contracts, setContracts] = useState<AssignmentContractDeveloper[]>([]);
  const [keyword, setKeyword] = useState("");
  const [poolPrice, setPoolPrice] = useState<number | undefined>();
  const [error, setError] = useState(""),
    [fields, setFields] = useState<Record<string, string>>({});
  const [loaded, setLoaded] = useState(false),
    [busy, setBusy] = useState(false),
    [confirm, setConfirm] = useState(false);
  const [selected, setSelected] = useState<string[]>([]),
    [reason, setReason] = useState(""),
    [memo, setMemo] = useState("");
  const [distributionWarning, setDistributionWarning] = useState(false);
  const [closedMonth, setClosedMonth] = useState<string | null>(null);
  const project = projects.find((p) => p.projectId === draft.projectId);
  const developer = people.find((p) => p.developerId === draft.developerId);
  const contract = contracts.find(
    (p) => p.developerId === draft.contractDeveloperId,
  );
  const canPrice =
    assignmentPriceEditable(roles) &&
    ["MO", "DP"].includes(project?.contractTypeCode ?? "") &&
    draft.inputTypeCode !== "FREE";
  const warnings: string[] = [];
  if (project?.statusCode === "PRE") warnings.push("미계약 선투입");
  if (developer?.workStatusName === "휴직")
    warnings.push("휴직 기간에 배정됩니다.");
  if (
    project?.contractTypeCode === "DP" &&
    ["FL", "OS"].includes(developer?.employeeTypeCode ?? "")
  )
    warnings.push("파견 프로젝트에 프리랜서/외주 인력을 배정합니다.");
  if (
    project &&
    developer &&
    project.contractTypeCode !== "IN" &&
    developer.salesPartId !== project.salesPartId
  )
    warnings.push(
      `영업 파트가 다릅니다: ${developer.name}님은 ${developer.salesPartName ?? "미지정"} 소속, 프로젝트는 ${project.salesPartName ?? "미지정"}입니다. 매출은 프로젝트 파트로, 이 사람 이익금은 소속 파트(본사면 파트 정산 제외)로 잡힙니다. 파트를 잘못 골랐다면 개발자 풀에서 고쳐 주세요.`,
    );
  if (closedMonth && draft.startDate.slice(0, 7) <= closedMonth)
    warnings.push(
      "마감월 금액은 유지됩니다. 전월 조정은 UI-10에서 직접 입력해 주세요.",
    );
  const reupload =
    "턴키 배분표는 다시 옮겨 올려 주셔야 합니다. 그 전까지는 배분 불일치 경고가 뜹니다.";
  useEffect(() => {
    let active = true;
    Promise.all([
      getCommonDevelopers({}),
      getCommonCodes("JOB"),
      getCommonCodes("INPUT_TYPE"),
      getCommonCodes("ASGN_CHG_RSN"),
    ])
      .then(([p, j, t, r]) => {
        if (active) {
          setPeople(p.items);
          setJobs(j.items);
          setTypes(
            t.items.filter((c) =>
              ["NEW", "ADD", "SUB", "FREE"].includes(c.code),
            ),
          );
          setReasons(r.items);
          setLoaded(true);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    let active = true;
    if (!draft.projectId || draft.inputTypeCode !== "SUB") {
      setContracts([]);
      return;
    }
    getCommonAssignmentContractDevelopers(
      draft.projectId,
      draft.developerId,
      draft.startDate,
    )
      .then((r) => {
        if (active) setContracts(r.items);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [
    draft.projectId,
    draft.developerId,
    draft.startDate,
    draft.inputTypeCode,
  ]);
  useEffect(() => {
    let active = true;
    setDistributionWarning(false);
    setClosedMonth(null);
    if (!project) return;
    import("../projects/projects")
      .then(async (api) => {
        const p = await api.getCommonProject(project.projectId);
        if (active) setClosedMonth(p.closedMonth ?? null);
        if (project.contractTypeCode === "TK" && draft.contractDeveloperId) {
          const d = await api.getCommonProjectDistribution(project.projectId);
          if (active)
            setDistributionWarning(
              d.items.some(
                (r) =>
                  r.managementNumber ===
                  people.find(
                    (p) => p.developerId === draft.contractDeveloperId,
                  )?.managementNumber,
              ),
            );
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [project, draft.contractDeveloperId, people]);
  const changeProject = (id: string) => {
    const p = projects.find((p) => p.projectId === id);
    setDraft((d) => ({
      ...d,
      projectId: id,
      contractDeveloperId: undefined,
      billingPrice: poolPrice,
      ...(p ? assignmentInitialDates(koreanToday(), p, true) : {}),
    }));
  };
  const chooseDeveloper = (id: string) => {
    setDraft((d) => ({
      ...d,
      developerId: id,
      contractDeveloperId:
        d.contractDeveloperId === id ? undefined : d.contractDeveloperId,
      billingPrice:
        d.contractDeveloperId && d.contractDeveloperId !== id
          ? d.billingPrice
          : undefined,
    }));
  };
  useEffect(() => {
    let active = true;
    setPoolPrice(undefined);
    if (!draft.developerId) return;
    getCommonDeveloper(draft.developerId)
      .then((p) => {
        if (!active) return;
        const price = p.monthlyRate ?? undefined;
        setPoolPrice(price);
        setDraft((d) =>
          d.contractDeveloperId ? d : { ...d, billingPrice: price },
        );
      })
      .catch((e) => {
        if (active) setError((e as Error).message);
      });
    return () => {
      active = false;
    };
  }, [draft.developerId]);
  const save = async (action?: "together" | "assignment-only") => {
    setError("");
    setFields({});
    if (!draft.developerId) {
      setFields({ developerId: "개발자를 골라 주세요." });
      return;
    }
    if (!action && contract?.overlappingAssignments.length) {
      setSelected(contract.overlappingAssignments.map((r) => r.assignmentId));
      setMemo(`대체투입(${developer?.name ?? ""})`);
      setReason("");
      setConfirm(true);
      return;
    }
    if (action === "together" && !reason) {
      setFields({ reasonCode: "철수 사유를 골라 주세요." });
      return;
    }
    setBusy(true);
    try {
      const payload: AssignmentInput = { ...draft };
      if (!canPrice) delete payload.billingPrice;
      if (draft.inputTypeCode !== "SUB") delete payload.contractDeveloperId;
      if (action) payload.replacementAction = action;
      if (action === "together")
        Object.assign(payload, {
          earlyWithdrawalAssignmentIds: selected,
          reasonCode: reason,
          memo,
        });
      const row = await postCommonAssignment(payload);
      onSaved(row);
    } catch (e) {
      if (e instanceof ProjectRequestError)
        setFields(
          Object.fromEntries(e.errors.map((f) => [f.field, f.message])),
        );
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const input = (
    field: "startDate" | "endDate" | "monthlyMm" | "billingPrice",
    label: string,
    type: string,
  ) => (
    <label>
      {label}
      <input
        data-testid={`assignment-${field.replace(/[A-Z]/g, (c) => "-" + c.toLowerCase())}`}
        type={type}
        value={draft[field] ?? ""}
        step={field === "monthlyMm" ? "0.01" : undefined}
        onChange={(e) =>
          setDraft((d) => ({
            ...d,
            [field]:
              type === "number" ? Number(e.target.value) : e.target.value,
          }))
        }
      />
      {fields[field] && <small role="alert">{fields[field]}</small>}
    </label>
  );
  return (
    <>
      <DetailPanel
        title="인력 투입 배정"
        testId="assignment-form"
        onClose={onClose}
        footer={
          <>
            <button data-testid="assignment-form-cancel" onClick={onClose}>
              취소
            </button>
            <button
              data-testid="assignment-form-save"
              disabled={busy || !loaded}
              onClick={() => void save()}
            >
              저장
            </button>
          </>
        }
      >
        {error && <p role="alert">{error}</p>}
        {!loaded && !error && <p>불러오는 중…</p>}
        <div className="assignment-form-grid">
          <label>
            프로젝트
            <select
              data-testid="assignment-project"
              value={draft.projectId}
              onChange={(e) => changeProject(e.target.value)}
            >
              <option value="">선택</option>
              {projects
                .filter((p) => ["PRE", "ING"].includes(p.statusCode))
                .map((p) => (
                  <option key={p.projectId} value={p.projectId}>
                    {p.projectName}
                  </option>
                ))}
            </select>
            {fields.projectId && <small role="alert">{fields.projectId}</small>}
          </label>
          <label>
            이름/관리번호 검색
            <input
              data-testid="assignment-developer-search"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </label>
          <label>
            개발자
            <select
              data-testid="assignment-developer"
              value={draft.developerId}
              onChange={(e) => void chooseDeveloper(e.target.value)}
            >
              <option value="">선택</option>
              {people
                .filter((p) =>
                  `${p.name} ${p.managementNumber}`.includes(keyword),
                )
                .map((p) => (
                  <option key={p.developerId} value={p.developerId}>
                    {p.name} / {p.managementNumber} ·{" "}
                    {p.workStatusName === "휴직"
                      ? "휴직"
                      : p.withdrawalDate
                        ? `투입 중 ~${p.withdrawalDate}`
                        : "대기"}
                  </option>
                ))}
            </select>
            {fields.developerId && (
              <small role="alert">{fields.developerId}</small>
            )}
          </label>
          <label>
            직무
            <select
              data-testid="assignment-job"
              value={draft.jobCode}
              onChange={(e) =>
                setDraft((d) => ({ ...d, jobCode: e.target.value }))
              }
            >
              <option value="">선택</option>
              {jobs.map((j) => (
                <option key={j.code} value={j.code}>
                  {j.codeName}
                </option>
              ))}
            </select>
            {fields.jobCode && <small role="alert">{fields.jobCode}</small>}
          </label>
          {input("startDate", "투입일", "date")}
          {input("endDate", "철수일", "date")}
          {input("monthlyMm", "월 MM", "number")}
          <label>
            투입 구분
            <select
              data-testid="assignment-input-type"
              value={draft.inputTypeCode}
              onChange={(e) =>
                setDraft((d) => ({
                  ...d,
                  inputTypeCode: e.target.value,
                  contractDeveloperId: undefined,
                  billingPrice: poolPrice,
                }))
              }
            >
              {types.map((t) => (
                <option key={t.code} value={t.code}>
                  {t.codeName}
                </option>
              ))}
            </select>
          </label>
          {draft.inputTypeCode === "SUB" && (
            <label>
              계약상 인력
              <select
                data-testid="assignment-contract-developer"
                value={draft.contractDeveloperId ?? ""}
                onChange={(e) => {
                  const c = contracts.find(
                    (c) => c.developerId === e.target.value,
                  );
                  setDraft((d) => ({
                    ...d,
                    contractDeveloperId: c?.developerId,
                    billingPrice: c?.billingPrice ?? undefined,
                  }));
                }}
              >
                <option value="">선택</option>
                {contracts.map((c) => (
                  <option key={c.developerId} value={c.developerId}>
                    {c.name} (철수 {c.endDate})
                  </option>
                ))}
              </select>
              {fields.contractDeveloperId && (
                <small role="alert">{fields.contractDeveloperId}</small>
              )}
            </label>
          )}
          {canPrice && input("billingPrice", "청구 단가", "number")}
        </div>
        {warnings.map((w) => (
          <Banner key={w} kind="warn">
            {w}
          </Banner>
        ))}
        {distributionWarning && <Banner kind="warn">{reupload}</Banner>}
        <button
          data-testid="assignment-ai-recommend"
          onClick={() => navigate("/screens/UI-30")}
        >
          AI 추천 받기
        </button>
      </DetailPanel>
      {confirm && (
        <Dialog
          title="대체투입 확인"
          testId="assignment-replacement"
          onClose={() => setConfirm(false)}
        >
          <p>{contract?.name}님의 선택 배정을 투입일 전날로 조기 철수합니다.</p>
          {contract?.overlappingAssignments.map((r) => (
            <label
              key={r.assignmentId}
              data-testid={`replacement-row-${r.assignmentId}`}
            >
              <input
                data-testid={`replacement-check-${r.assignmentId}`}
                type="checkbox"
                checked={selected.includes(r.assignmentId)}
                onChange={(e) =>
                  setSelected((s) =>
                    e.target.checked
                      ? [...s, r.assignmentId]
                      : s.filter((id) => id !== r.assignmentId),
                  )
                }
              />
              {r.projectName} {r.startDate} ~ {r.endDate}
            </label>
          ))}
          <label>
            철수 사유
            <select
              data-testid="replacement-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            >
              <option value="">선택</option>
              {reasons.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.codeName}
                </option>
              ))}
            </select>
            {fields.reasonCode && (
              <small role="alert">{fields.reasonCode}</small>
            )}
          </label>
          <label>
            메모
            <input
              data-testid="replacement-memo"
              maxLength={200}
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
            />
          </label>
          {distributionWarning && <Banner kind="warn">{reupload}</Banner>}
          <div className="app-actions">
            <button
              data-testid="replacement-cancel"
              disabled={busy}
              onClick={() => setConfirm(false)}
            >
              취소
            </button>
            <button
              data-testid="replacement-only"
              disabled={busy}
              onClick={() => void save("assignment-only")}
            >
              대체투입만 저장
            </button>
            <button
              data-testid="replacement-together"
              disabled={busy}
              onClick={() => void save("together")}
            >
              같이 처리
            </button>
          </div>
        </Dialog>
      )}
    </>
  );
}
