import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Banner, DetailPanel } from "../../shared/list/DetailPanel";
import { Dialog } from "../../shared/feedback/Dialog";
import { focusFirstError } from "../../shared/feedback/messages";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { koreanToday } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import {
  HQ_PART,
  directLabel,
  getCommonDeveloper,
  getCommonDeveloperAssignments,
  getCommonDeveloperStatuses,
  getCommonDevelopers,
  type DeveloperAssignment,
  type DeveloperListRow,
  type DeveloperStatus,
} from "../developers/developers";
import {
  ProjectRequestError,
  getCommonProject,
  getCommonProjectDistribution,
  projectFailure,
  typeLabel,
  won,
  type Project,
} from "../projects/projects";
import {
  KIND_CODES,
  KIND_LABEL,
  addDays,
  assignmentInitialDates,
  assignmentPriceEditable,
  getCommonAssignmentContractDevelopers,
  isAssignmentConflict,
  mmf,
  postCommonAssignment,
  type Assignment,
  type AssignmentCandidate,
  type AssignmentContractDeveloper,
  type AssignmentInput,
} from "./assignments";
const warnIcon = (size = 16) => (
  <svg className="assignment-warn-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3l9 16H3zM12 10v4M12 17h.01" />
  </svg>
);
import "../projects/projects.css";
import "./assignments.css";

type Option = { code: string; codeName: string };
const digits = (value: string) => value.replace(/[^0-9]/g, "");
const money = (value: number | null | undefined) => (value == null || !(value > 0) ? "" : won(value));
const sparkle = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z" />
  </svg>
);
const PM_NO_RATE = "월 단가가 없어 배정할 수 없습니다. 파트장·재무에게 요청해 주세요.";
const TK_REUPLOAD = "턴키 배분표는 다시 옮겨 올려 주셔야 합니다. 그 전까지는 배분 불일치 경고가 뜹니다.";
const serverField: Record<string, string> = {
  projectId: "project",
  developerId: "developer",
  jobCode: "job",
  startDate: "dates",
  endDate: "dates",
  monthlyMm: "mm",
  inputTypeCode: "kind",
  contractDeveloperId: "orig",
  billingPrice: "price",
};
/** 시안 배정 창 개발자 목록 옆 표시: 휴직 / 투입 중 ~철수일 / 대기(2026-10-24 결정). */
const availability = (p: DeveloperListRow) =>
  p.workStatusName === "휴직" ? "휴직" : p.withdrawalDate ? `투입 중 ~${p.withdrawalDate}` : "대기";
const personText = (p: DeveloperListRow) => `${p.name} (${p.managementNumber})`;

/** 시안 투입 배정 창(오른쪽 창): 경고 띠, 프로젝트·개발자·직무·기간·월 MM·투입 구분·계약상 인력·청구 단가, AI 추천. */
export function AssignmentForm({
  projects,
  candidate,
  roles,
  onClose,
  onSaved,
  onConflict,
  recommendedMonth,
}: {
  projects: Project[];
  candidate?: AssignmentCandidate;
  roles: readonly string[];
  onClose: () => void;
  onSaved: (row: Assignment, message: string) => void;
  /** 저장이 409(다른 사람이 먼저 바꿈, 예: 후보를 이미 확정·뺌)면 부른다. 없으면 창 안에 안내만 띄운다. */
  onConflict?: (reason: unknown) => void;
  recommendedMonth?: string;
}) {
  const navigate = useNavigate();
  const canRate = assignmentPriceEditable(roles);
  const initial = projects.find((p) => p.projectId === candidate?.projectId) ?? projects[0];
  const [v, setV] = useState(() => ({
    projectId: initial?.projectId ?? "",
    developerId: candidate?.developerId ?? "",
    jobCode: candidate?.jobCode ?? "BE",
    ...(initial ? assignmentInitialDates(koreanToday(), initial, false, recommendedMonth) : { startDate: koreanToday(), endDate: "" }),
    ...(candidate ? { startDate: candidate.startDate, endDate: candidate.endDate } : {}),
    mm: "1.00",
    kind: "NEW",
    orig: "",
    price: "",
  }));
  const [text, setText] = useState<string | null>(null);
  const [priceTouched, setPriceTouched] = useState(false);
  const [touched, setTouched] = useState(false);
  const [people, setPeople] = useState<DeveloperListRow[]>([]);
  const [jobs, setJobs] = useState<Option[]>([]);
  const [reasons, setReasons] = useState<Option[]>([]);
  const [contracts, setContracts] = useState<AssignmentContractDeveloper[]>([]);
  const [pool, setPool] = useState<{ rate?: number | null; retirementDate?: string | null } | null>(null);
  const [statuses, setStatuses] = useState<DeveloperStatus[]>([]);
  const [booked, setBooked] = useState<DeveloperAssignment[]>([]);
  const [closedMonth, setClosedMonth] = useState<string | null>(null);
  const [distribution, setDistribution] = useState<string[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [leaving, setLeaving] = useState(false);
  const [replace, setReplace] = useState<{ ids: string[]; reason: string; memo: string; error: string } | null>(null);
  const body = useRef<HTMLDivElement>(null);
  const project = projects.find((p) => p.projectId === v.projectId);
  const dv = people.find((p) => p.developerId === v.developerId);
  const contract = contracts.find((c) => c.developerId === v.orig);
  const showRate = !!project && ["MO", "DP"].includes(project.contractTypeCode) && v.kind !== "FREE" && canRate;
  const setDirty = useScreenDirty();
  useEffect(() => {
    setDirty("UI-12", touched);
    return () => setDirty("UI-12", false);
  }, [touched, setDirty]);
  const edit = (patch: Partial<typeof v>) => {
    setTouched(true);
    setV((d) => ({ ...d, ...patch }));
  };
  const leave = () => (touched ? setLeaving(true) : onClose());
  useEffect(() => {
    let active = true;
    Promise.all([getCommonDevelopers({}), getCommonCodes("JOB"), getCommonCodes("ASGN_CHG_RSN")])
      .then(([p, j, r]) => {
        if (!active) return;
        setPeople(p.items ?? []);
        setJobs(j.items ?? []);
        setReasons(r.items ?? []);
        setLoaded(true);
      })
      .catch((e) => active && setError(projectFailure(e)));
    return () => {
      active = false;
    };
  }, []);
  // 개발자를 고르면 개발자 풀 월 단가·퇴사일·휴직 기간을 읽는다(경고와 기본값).
  useEffect(() => {
    let active = true;
    setPool(null);
    setStatuses([]);
    setBooked([]);
    if (!v.developerId) return;
    getCommonDeveloper(v.developerId)
      .then((d) => {
        if (!active) return;
        setPool({ rate: d.monthlyRate, retirementDate: d.retirementDate });
        if (!priceTouched) setV((x) => (x.orig ? x : { ...x, price: money(d.monthlyRate) }));
      })
      .catch((e) => active && setError(projectFailure(e)));
    getCommonDeveloperStatuses(v.developerId)
      .then((s) => active && setStatuses(s?.items ?? []))
      .catch(() => undefined);
    // 시안 MM 1.0 초과: 이 사람의 다른 배정(지금 월 MM, 취소 제외)을 읽어 같은 기간 합계를 센다.
    getCommonDeveloperAssignments(v.developerId)
      .then((r) => active && setBooked(r?.items ?? []))
      .catch(() => undefined);
    return () => {
      active = false;
    };
    // 단가 직접 입력 여부는 고른 순간의 값만 본다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [v.developerId]);
  useEffect(() => {
    let active = true;
    if (!v.projectId || v.kind !== "SUB") {
      setContracts([]);
      return;
    }
    getCommonAssignmentContractDevelopers(v.projectId, v.developerId, v.startDate)
      .then((r) => active && setContracts((r.items ?? []).filter((c) => c.developerId !== v.developerId)))
      .catch((e) => active && setError(projectFailure(e)));
    return () => {
      active = false;
    };
  }, [v.projectId, v.developerId, v.startDate, v.kind]);
  useEffect(() => {
    let active = true;
    setClosedMonth(null);
    setDistribution([]);
    if (!v.projectId) return;
    getCommonProject(v.projectId)
      .then(async (p) => {
        if (!active) return;
        setClosedMonth(p.closedMonth ?? null);
        if (p.contractTypeCode === "TK") {
          const d = await getCommonProjectDistribution(v.projectId);
          if (active) setDistribution(d.items.filter((r) => r.rowTypeCode !== "ITEM").map((r) => r.managementNumber ?? r.name));
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [v.projectId]);
  const origPerson = people.find((p) => p.developerId === v.orig);
  const distWarn =
    project?.contractTypeCode === "TK" &&
    !!contract &&
    distribution.some((key) => key === origPerson?.managementNumber || key === contract.name);
  const pickDeveloper = (value: string) => {
    setTouched(true);
    setText(value);
    const m = value.match(/\(([^()]+)\)\s*$/);
    const byNo = people.find((p) => p.managementNumber === (m ? m[1] : value.trim()));
    const byName = people.filter((p) => p.name === value.trim());
    const next = byNo ?? (byName.length === 1 ? byName[0] : undefined);
    setV((d) => {
      if ((next?.developerId ?? "") === d.developerId) return d;
      return {
        ...d,
        developerId: next?.developerId ?? "",
        jobCode: next?.primaryJobCode ?? d.jobCode,
        orig: d.orig === next?.developerId ? "" : d.orig,
        // 단가를 직접 고치지 않았으면 새 사람의 월 단가로(읽은 뒤 채움)
        price: priceTouched || (d.orig && d.orig !== next?.developerId) ? d.price : "",
      };
    });
  };
  const changeProject = (id: string) => {
    const p = projects.find((x) => x.projectId === id);
    edit({
      projectId: id,
      orig: "",
      ...(p ? assignmentInitialDates(koreanToday(), p, true) : {}),
      ...(v.orig && !priceTouched ? { price: money(pool?.rate) } : {}),
    });
  };
  const changeKind = (kind: string) =>
    edit({ kind, orig: "", ...(v.orig && !priceTouched ? { price: money(pool?.rate) } : {}) });
  const changeOrig = (id: string) => {
    const c = contracts.find((x) => x.developerId === id);
    setPriceTouched(false);
    edit({ orig: id, price: c ? money(c.billingPrice) : money(pool?.rate) });
  };
  // 시안 경고 띠(저장은 됨)
  const warnings: string[] = [];
  if (dv) {
    // 시안: 기간이 [투입일, 철수일]과 겹치는 이 사람 배정의 지금 월 MM 합(프로젝트·연도·달 구분 없음) + 입력 MM
    const cur =
      v.startDate && v.endDate
        ? booked.filter((a) => a.startDate <= v.endDate && a.endDate >= v.startDate).reduce((sum, a) => sum + (Number(a.mm) || 0), 0)
        : 0;
    const add = Number(v.mm) || 0;
    if (Math.round((cur + add) * 100) / 100 > 1)
      warnings.push(`MM 1.0 초과: ${dv.name}님은 같은 기간에 이미 ${mmf(cur)}MM 배정되어 있습니다(합계 ${mmf(cur + add)}). 저장은 됩니다.`);
    const leave = statuses.find(
      (s) => s.statusName === "휴직" && v.startDate && v.endDate && s.startDate <= v.endDate && (!s.endDate || s.endDate >= v.startDate),
    );
    if (leave) warnings.push(`${dv.name}님은 배정 기간에 휴직(${leave.startDate} ~ ${leave.endDate || "복직일 미정"})이 있습니다. 저장은 됩니다.`);
    else if (dv.workStatusName === "휴직" && !statuses.length) warnings.push(`${dv.name}님은 휴직 기간입니다. 저장은 됩니다.`);
    if (project && project.contractTypeCode !== "IN" && project.salesPartId && dv.salesPartId && dv.salesPartId !== project.salesPartId) {
      const label = (id: string, name?: string | null) => (id === HQ_PART ? "본사" : name ?? id);
      const mine = label(dv.salesPartId, dv.salesPartName);
      const theirs = label(project.salesPartId, project.salesPartName);
      warnings.push(
        `영업 파트가 다릅니다: ${dv.name}님은 ${mine} 소속, 프로젝트는 ${theirs}입니다. 매출은 ${theirs}로, 이 사람 이익금은 ${dv.salesPartId === HQ_PART ? "본사(파트 정산 제외)" : mine}로 잡힙니다. 파트를 잘못 골랐다면 개발자 풀에서 고쳐 주세요. 저장은 됩니다.`,
      );
    }
    if (project?.contractTypeCode === "DP" && ["FL", "OS"].includes(dv.employeeTypeCode))
      warnings.push(`파견 프로젝트에 ${dv.employeeTypeName} 인력을 배정합니다. 막지는 않으니 계약 조건을 확인하세요.`);
  }
  if (project?.statusCode === "PRE") warnings.push("수주예정 프로젝트입니다. 저장하면 「미계약 선투입」으로 표시됩니다.");
  if (closedMonth && v.startDate && v.startDate.slice(0, 7) <= closedMonth)
    warnings.push(`마감한 달(${v.startDate.slice(0, 7)})에 걸칩니다. 마감한 달 금액은 그대로 두고 차액은 이번 달 전월 조정으로 넣습니다.`);
  const overlapping = contract?.overlappingAssignments ?? [];
  const withdrawDay = v.startDate ? addDays(v.startDate, -1) : "";
  const commit = async (withdraw: string[], reasonCode?: string, memo?: string) => {
    if (!project) return;
    setBusy(true);
    setError("");
    try {
      const payload: AssignmentInput = {
        projectId: v.projectId,
        developerId: v.developerId,
        jobCode: v.jobCode,
        startDate: v.startDate,
        endDate: v.endDate,
        monthlyMm: Number(v.mm),
        inputTypeCode: v.kind,
      };
      if (showRate) payload.billingPrice = Number(digits(v.price));
      if (v.kind === "SUB") payload.contractDeveloperId = v.orig;
      if (v.kind === "SUB" && overlapping.length) {
        payload.replacementAction = withdraw.length ? "together" : "assignment-only";
        if (withdraw.length) Object.assign(payload, { earlyWithdrawalAssignmentIds: withdraw, reasonCode, ...(memo ? { memo } : {}) });
      }
      if (candidate) payload.candidateId = candidate.candidateId;
      const row = await postCommonAssignment(payload);
      setReplace(null);
      setTouched(false);
      const message =
        "배정을 저장했습니다. 변경 이력에 「투입」 1줄을 남겼습니다." +
        (withdraw.length ? ` ${contract?.name}님 배정 ${withdraw.length}건을 ${withdrawDay}로 조기 철수했습니다.` : "") +
        (distWarn ? " 턴키 배분표는 다시 옮겨 올려 주셔야 합니다." : "") +
        (row.warnings?.length ? " 경고: " + row.warnings.join(", ") : "");
      onSaved(row, message);
    } catch (e) {
      setReplace(null);
      if (isAssignmentConflict(e) && onConflict) {
        setTouched(false);
        onConflict(e);
        return;
      }
      setError(projectFailure(e));
      if (e instanceof ProjectRequestError && e.errors.length) {
        // 서버 칸 오류(예: 후보의 프로젝트·개발자만 확정, 간접 인력 제외)는 그 칸 밑에 보이고 첫 오류 칸으로 간다.
        setErrors(Object.fromEntries(e.errors.map((f) => [serverField[f.field] ?? f.field, f.message])));
        window.setTimeout(() => focusFirstError(body.current), 0);
      }
    } finally {
      setBusy(false);
    }
  };
  const save = () => {
    if (busy || !loaded) return;
    const e: Record<string, string> = {};
    const mm = Number(v.mm);
    if (project && ["MO", "DP"].includes(project.contractTypeCode) && v.kind !== "FREE" && dv && !canRate && !(v.kind === "SUB" && !v.orig)) {
      // 시안: PM은 청구 단가 칸이 없어 개발자 풀 월 단가(대체투입이면 계약상 인력 단가)로 저장한다.
      const base = v.kind === "SUB" ? contract?.billingPrice : pool?.rate;
      if (!((base ?? 0) > 0)) e[v.kind === "SUB" ? "orig" : "developer"] = PM_NO_RATE;
    }
    if (!dv) e.developer = "개발자를 골라 주세요.";
    if (!v.startDate || !v.endDate) e.dates = "투입일과 철수일을 넣어 주세요.";
    else if (v.endDate < v.startDate) e.dates = "철수일이 투입일보다 앞입니다.";
    else if (project?.endDate && v.endDate > project.endDate)
      e.dates = `철수일이 프로젝트 계약 종료일(${project.endDate})보다 늦습니다. 「프로젝트·투입 › 프로젝트」에서 계약 기간을 먼저 연장해 주세요.`;
    else if (dv && pool?.retirementDate && v.endDate > pool.retirementDate)
      e.dates = `철수일이 ${dv.name}님의 퇴사일(${pool.retirementDate})보다 늦습니다.`;
    if (!(mm > 0 && mm <= 1)) e.mm = "월 MM은 0보다 크고 1.00 이하로 넣어 주세요.";
    if (v.kind === "SUB" && !v.orig) e.orig = "계약상 인력을 골라 주세요.";
    if (showRate && !(Number(digits(v.price)) > 0)) e.price = "청구 단가를 넣어 주세요.";
    setErrors(e);
    setError("");
    if (Object.keys(e).length) {
      window.setTimeout(() => focusFirstError(body.current), 0);
      return;
    }
    // 대체투입: 계약상 인력의 이 프로젝트 배정 중 투입일에 걸친 것이 있으면 조기 철수할지 묻는다.
    if (v.kind === "SUB" && overlapping.length) {
      setReplace({ ids: overlapping.map((r) => r.assignmentId), reason: "", memo: `대체투입(${dv!.name})`, error: "" });
      return;
    }
    void commit([]);
  };
  const currentSave = useRef(save);
  currentSave.current = save;
  useEffect(() => {
    // 공통 기능: Ctrl+S로 저장
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        if (!document.querySelector(".app-scrim")) currentSave.current();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
  // 목록 응답에는 코드(directClassCode)만 온다: 간접 인력(INDRCT)은 배정 대상이 아니다(SFR-102-10).
  const people2 = useMemo(
    () => people.filter((p) => p.directClassCode !== "INDRCT" && directLabel(p.directClassName) !== "간접" && p.useYn !== "N"),
    [people],
  );
  const field = (id: string, key: string, label: string, required: boolean, control: ReactNode, help?: ReactNode) => (
    <div className="project-fc" data-testid={`assignment-field-${key}`}>
      <label htmlFor={id}>
        {label}
        {required && <span className="project-bad"> *</span>}
      </label>
      {control}
      {help && <span className="project-help">{help}</span>}
      {errors[key] && (
        <small role="alert" className="field-error assignment-err">
          {errors[key]}
        </small>
      )}
    </div>
  );
  const invalid = (key: string) => (errors[key] ? { "aria-invalid": true as const } : {});
  const recommend = () => {
    // 시안 asgrec: 진행 중이고 내부가 아닌 프로젝트만 대상·직무·시작월(투입월과 프로젝트 시작월 중 늦은 달)을 넘긴다.
    // 묻지 않고 창을 닫은 뒤 AI 인력 추천(UI-30)으로 간다.
    const target = project && project.statusCode === "ING" && project.contractTypeCode !== "IN" ? project : undefined;
    let state: { projectId: string; jobCode: string; startMonth: string } | undefined;
    if (target) {
      const projectStart = target.startDate.slice(0, 7);
      const startMonth = v.startDate && v.startDate.slice(0, 7) > projectStart ? v.startDate.slice(0, 7) : projectStart;
      state = { projectId: target.projectId, jobCode: v.jobCode, startMonth };
    }
    setTouched(false);
    onClose();
    navigate("/screens/UI-30", state ? { state } : undefined);
  };
  const noRateHelp =
    dv && !((pool?.rate ?? 0) > 0) && v.kind !== "SUB"
      ? "⚠ 개발자 풀에 월 단가가 없는 사람입니다. PM은 이 칸이 안 보여 「월 단가가 없어 배정할 수 없습니다. 파트장·재무에게 요청해 주세요.」로 막히므로, 파트장·재무가 단가를 넣어 배정합니다. "
      : "";
  return (
    <>
      <DetailPanel
        title="투입 배정"
        sub={candidate ? "배정 후보 확정" : recommendedMonth ? "AI 인력 추천에서 채움" : "오른쪽 창 입력(유형 B)"}
        testId="assignment-form"
        onClose={leave}
        footer={
          <>
            <button type="button" className="ls-btn" data-testid="assignment-form-cancel" onClick={leave}>
              취소
            </button>
            <button type="button" className="ls-btn ls-btn-p" data-testid="assignment-form-save" disabled={busy || !loaded} onClick={save}>
              저장
            </button>
          </>
        }
      >
        <div className="assignment-form" ref={body}>
          {warnings.length > 0 && (
            <Banner kind="warn">
              {warnIcon(16)}
              <span data-testid="assignment-form-warnings">
                {warnings.map((w, i) => (
                  <span key={w}>
                    {i > 0 && <br />}
                    {w}
                  </span>
                ))}
              </span>
            </Banner>
          )}
          {error && (
            <p role="alert" className="field-error" data-testid="assignment-form-error">
              {error}
            </p>
          )}
          {field(
            "assignment-project",
            "project",
            "프로젝트",
            true,
            <select id="assignment-project" data-testid="assignment-project" value={v.projectId} onChange={(e) => changeProject(e.target.value)} {...invalid("project")}>
              {projects.map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName} ({typeLabel(p)}
                  {p.statusCode === "PRE" ? " · 수주예정" : ""})
                </option>
              ))}
            </select>,
          )}
          {field(
            "assignment-developer",
            "developer",
            "개발자",
            true,
            <>
              <input
                id="assignment-developer"
                data-testid="assignment-developer"
                list="assignment-developer-list"
                placeholder="이름 또는 관리번호로 찾기"
                autoComplete="off"
                value={text ?? (dv ? personText(dv) : "")}
                onChange={(e) => pickDeveloper(e.target.value)}
                {...invalid("developer")}
              />
              <datalist id="assignment-developer-list">
                {people2.map((p) => (
                  <option key={p.developerId} value={personText(p)} label={availability(p)} />
                ))}
              </datalist>
            </>,
            dv
              ? `${dv.employeeTypeName} · ${dv.gradeName ?? "–"} · ${dv.primaryJobName}${(pool?.rate ?? 0) > 0 ? ` · 월 단가 ${won(pool?.rate)}원` : ""}`
              : undefined,
          )}
          {field(
            "assignment-job",
            "job",
            "직무",
            true,
            <select id="assignment-job" data-testid="assignment-job" value={v.jobCode} onChange={(e) => edit({ jobCode: e.target.value })} {...invalid("job")}>
              {jobs.map((j) => (
                <option key={j.code} value={j.code}>
                  {j.codeName}
                </option>
              ))}
            </select>,
            "개발자를 고르면 주 직무로 채웁니다.",
          )}
          {field(
            "assignment-start-date",
            "dates",
            "투입일 ~ 철수일",
            true,
            <span className="project-row assignment-row">
              <input
                id="assignment-start-date"
                data-testid="assignment-start-date"
                type="date"
                value={v.startDate}
                onChange={(e) => edit({ startDate: e.target.value })}
                {...invalid("dates")}
              />
              <span>~</span>
              <input
                data-testid="assignment-end-date"
                type="date"
                aria-label="철수일"
                value={v.endDate}
                onChange={(e) => edit({ endDate: e.target.value })}
              />
            </span>,
          )}
          {field(
            "assignment-monthly-mm",
            "mm",
            "월 MM",
            true,
            <span className="project-row assignment-row">
              <input
                id="assignment-monthly-mm"
                data-testid="assignment-monthly-mm"
                className="project-money"
                style={{ width: 100 }}
                inputMode="decimal"
                value={v.mm}
                onChange={(e) => edit({ mm: e.target.value.replace(/[^0-9.]/g, "") })}
                {...invalid("mm")}
              />
              <span className="project-unit">MM</span>
            </span>,
            "기본 1.00(풀 출근). 주 3일이면 0.6, 주 1~2회면 0.25처럼 줄여서 금액을 맞춥니다. 특정 달만 다르면 저장한 뒤 [변경 기록] → 월 MM 변경으로 그 달부터 바꿉니다.",
          )}
          {field(
            "assignment-input-type",
            "kind",
            "투입 구분",
            true,
            <select id="assignment-input-type" data-testid="assignment-input-type" value={v.kind} onChange={(e) => changeKind(e.target.value)}>
              {KIND_CODES.map((k) => (
                <option key={k} value={k}>
                  {KIND_LABEL[k]}
                </option>
              ))}
            </select>,
          )}
          {v.kind === "SUB" &&
            field(
              "assignment-contract-developer",
              "orig",
              "계약상 인력",
              true,
              <select
                id="assignment-contract-developer"
                data-testid="assignment-contract-developer"
                value={v.orig}
                onChange={(e) => changeOrig(e.target.value)}
                {...invalid("orig")}
              >
                <option value="">선택</option>
                {contracts.map((c) => (
                  <option key={c.developerId} value={c.developerId}>
                    {c.name}
                    {c.jobName ? ` · ${c.jobName}` : ""}
                    {showRate && c.billingPrice ? ` · ${won(c.billingPrice)}원` : ""}
                    {c.endDate < koreanToday() ? ` (철수 ${c.endDate})` : ""}
                  </option>
                ))}
              </select>,
              "계약서에 이름이 올라간 사람입니다(이 프로젝트에 배정된 적 있는 사람, 철수한 사람 포함). " +
                (showRate ? "고르면 청구 단가 기본값이 이 사람의 최근 월 단가로 바뀝니다. " : "") +
                "저장할 때 이 사람 배정을 투입일 전날로 조기 철수할지 묻습니다.",
            )}
          {showRate
            ? field(
                "assignment-billing-price",
                "price",
                "청구 단가(월)",
                true,
                <span className="project-row assignment-row">
                  <input
                    id="assignment-billing-price"
                    data-testid="assignment-billing-price"
                    className="project-money"
                    style={{ width: 160 }}
                    inputMode="numeric"
                    value={v.price}
                    onChange={(e) => {
                      setPriceTouched(true);
                      const n = digits(e.target.value);
                      edit({ price: n ? won(Number(n)) : "" });
                    }}
                    {...invalid("price")}
                  />
                  <span className="project-unit">원</span>
                </span>,
                noRateHelp +
                  "기본값은 개발자 월 단가입니다. 고객과 다르게 계약했으면 고쳐 넣습니다. 풀로 출근하지 않으면 단가는 그대로 두고 월 MM을 줄입니다(예: 주 3일 0.6).",
              )
            : (
                <p className="project-cap" data-testid="assignment-no-price">
                  {project?.contractTypeCode === "TK"
                    ? "턴키는 배분표 금액으로 매출을 계산하므로 청구 단가 칸이 없습니다."
                    : "내부 프로젝트는 매출이 없어 청구 단가 칸이 없습니다."}
                </p>
              )}
          <p className="project-cap">연장 가능성은 처음에 「미정」이고 철수 리스트(UI-05)에서 바꿉니다.</p>
          <button type="button" className="ls-btn assignment-ai" data-testid="assignment-ai-recommend" onClick={recommend}>
            {sparkle} 이 자리에 맞는 사람 AI 추천 받기
          </button>
        </div>
      </DetailPanel>
      {replace && contract && project && (
        <Dialog title="대체투입 저장" testId="assignment-replacement" closable onClose={() => !busy && setReplace(null)}>
          <div className="project-modal">
            <p>
              {contract.name}님의 「{project.projectName}」 배정을 <b>{withdrawDay}</b>로 조기 철수합니다.
            </p>
            <div className="assignment-sub-list">
              {overlapping.map((r) => (
                <label key={r.assignmentId} className="assignment-check" data-testid={`replacement-row-${r.assignmentId}`}>
                  <input
                    type="checkbox"
                    data-testid={`replacement-check-${r.assignmentId}`}
                    checked={replace.ids.includes(r.assignmentId)}
                    onChange={(e) =>
                      setReplace((x) =>
                        x && {
                          ...x,
                          ids: e.target.checked ? [...x.ids, r.assignmentId] : x.ids.filter((id) => id !== r.assignmentId),
                        },
                      )
                    }
                  />{" "}
                  {r.startDate} ~ {r.endDate} · 월 MM {mmf(r.monthlyMm)} · {r.inputTypeName}
                </label>
              ))}
            </div>
            {closedMonth && withdrawDay.slice(0, 7) <= closedMonth && (
              <Banner kind="warn">
                {warnIcon(16)}
                <span>
                  마감한 달({withdrawDay.slice(0, 7)})에 걸칩니다. 마감한 달 금액은 그대로 두고 차액은 이번 달 전월 조정으로 넣습니다.
                </span>
              </Banner>
            )}
            {distWarn && (
              <Banner kind="warn">
                {warnIcon(16)}
                <span>{TK_REUPLOAD}</span>
              </Banner>
            )}
            <div className="project-fc">
              <label htmlFor="replacement-reason">
                철수 사유<span className="project-bad"> *</span>
              </label>
              <select
                id="replacement-reason"
                data-testid="replacement-reason"
                value={replace.reason}
                onChange={(e) => setReplace((x) => x && { ...x, reason: e.target.value, error: "" })}
              >
                <option value="">고르세요</option>
                {reasons.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.codeName}
                  </option>
                ))}
              </select>
            </div>
            <div className="project-fc">
              <label htmlFor="replacement-memo">메모</label>
              <input
                id="replacement-memo"
                data-testid="replacement-memo"
                maxLength={200}
                value={replace.memo}
                onChange={(e) => setReplace((x) => x && { ...x, memo: e.target.value })}
              />
            </div>
            <p className="field-error" role="alert" data-testid="replacement-error">
              {replace.error}
            </p>
            <p className="project-help">
              [대체투입만 저장]을 누르면 {contract.name}님 배정은 그대로 둡니다. 나중에 [변경 기록] › 조기 철수로 따로 처리할 수 있습니다.
            </p>
            <div className="app-actions">
              <button type="button" data-testid="replacement-cancel" disabled={busy} onClick={() => setReplace(null)}>
                취소
              </button>
              <button type="button" data-testid="replacement-only" disabled={busy} onClick={() => void commit([])}>
                대체투입만 저장
              </button>
              <button
                type="button"
                className="primary-button"
                data-testid="replacement-together"
                disabled={busy}
                onClick={() => {
                  if (replace.ids.length && !replace.reason) {
                    setReplace((x) => x && { ...x, error: "철수 사유를 골라 주세요." });
                    document.getElementById("replacement-reason")?.focus();
                    return;
                  }
                  void commit(replace.ids, replace.reason, replace.memo.trim());
                }}
              >
                같이 처리
              </button>
            </div>
          </div>
        </Dialog>
      )}
      {leaving && (
        <Dialog title="저장하지 않은 내용이 있습니다" closable testId="assignment-leave-confirm" onClose={() => setLeaving(false)}>
          {/* 시안 확인 창: 머리 [×]·폭 400(작은 창 모양) */}
          <div className="project-modal">
            <p>나가면 입력한 내용이 사라집니다.</p>
            <div className="app-actions">
              <button type="button" data-testid="assignment-leave-cancel" onClick={() => setLeaving(false)}>
                취소
              </button>
              <button type="button" className="primary-button" data-testid="assignment-leave-go" onClick={onClose}>
                나가기
              </button>
            </div>
          </div>
        </Dialog>
      )}
    </>
  );
}
