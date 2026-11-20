import { useEffect, useRef, useState, type ReactNode } from "react";
import { ConfirmDialog, Dialog, Toast } from "../../shared/feedback/Dialog";
import type { ToastKind } from "../../shared/feedback/Dialog";
import { Badge, Banner, DetailPanel, KeyValues } from "../../shared/list";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { formatDateTime } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonParts } from "../parts/parts";
import { PartLabel } from "./ProjectPage";
import * as api from "./projects";
import { ProjectField } from "./ProjectField";
import { useOpenProjectDistribution } from "./projectEdit";
import { ProjectProgressPanel } from "./ProjectProgressPanel";
import "./projects.css";

type Code = { code: string; codeName: string };
type Mode = "budget" | "part" | "expense" | "lose" | "";
const digits = (value: string) => value.replace(/[^0-9]/g, "");
const money = (value: string) =>
  digits(value) ? Number(digits(value)).toLocaleString("ko-KR") : "";
const mm = (value: number | null | undefined) =>
  value == null ? "–" : Number(value).toFixed(2);
const lightKind = (light: string | null) =>
  light === "위험"
    ? "bad"
    : light === "주의"
      ? "warn"
      : light === "정상"
        ? "ok"
        : "neutral";
const icon = (d: string, size = 16) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);
const plusIcon = icon("M12 5v14M5 12h14");
const xIcon = icon("M18 6L6 18M6 6l12 12");
const moreIcon = icon("M5 12h.01M12 12h.01M19 12h.01", 18);
const warnIcon = icon("M12 3l9 16H3zM12 10v4M12 17h.01");
const lockIcon = icon("M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4");
const infoIcon = icon(
  "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01",
);

/**
 * 시안 프로젝트 상세(오른쪽 패널): 머리 이름 · 「ID · 고객사 · 유형」, [수정][배분표][⋯],
 * 탭 기본 정보 · 진행 현황 · 이슈 (n) · 인원 계획 · 투입 인력 (n) · 계약 변경 이력.
 */
export function ProjectDetail({
  projectId,
  onClose,
  readOnly = false,
  onChanged,
  onDistribution,
  onEdit,
  dirtyScreenId,
  initialTab = "basic",
  renderIssues,
  boardOperations = false,
}: {
  /** 이 패널을 연 화면 번호. 저장 안 한 인원 계획이 있으면 그 화면 탭에 「●」를 보인다. */
  dirtyScreenId?: string;
  projectId: string;
  onClose: () => void;
  readOnly?: boolean;
  onChanged?: (message?: string, kind?: ToastKind) => void;
  onDistribution?: (id: string) => void;
  onEdit?: (project: api.Project) => void;
  /** 처음 열 탭(시안 openProject(id, tab)). 기본은 기본 정보. 인원 계획 대비 투입(UI-11)은 "plan". */
  initialTab?: string;
  renderIssues?: (
    project: api.Project,
    onChanged: (message?: string) => void,
  ) => ReactNode;
  boardOperations?: boolean;
}) {
  // 시안 distopen: 배분표를 넘겨받지 않은 화면(개발자 풀·현황판 등)에서는 상세를 닫고 프로젝트 화면 배분표로 간다.
  const openDistribution = useOpenProjectDistribution();
  const distribution =
    onDistribution ??
    ((id: string) => {
      onClose();
      openDistribution(id);
    });
  const [p, setP] = useState<api.Project | null>(null);
  const [tab, setTab] = useState(initialTab);
  const [expenses, setExpenses] = useState<api.ProjectExpense[]>([]);
  const [parts, setParts] = useState<api.ProjectPart[]>([]);
  const [history, setHistory] = useState<api.ProjectHistory[]>([]);
  const [jobs, setJobs] = useState<api.ProjectJob[]>([]);
  const [planAllowed, setPlanAllowed] = useState(false);
  const [codes, setCodes] = useState<{
    jobs: Code[];
    reasons: Code[];
    items: Code[];
  }>({ jobs: [], reasons: [], items: [] });
  const [partOptions, setPartOptions] = useState<
    { partId: string; partName: string }[]
  >([]);
  const [rule, setRule] = useState<api.ProjectBudgetRule | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState<Mode>("");
  const [menu, setMenu] = useState(false);
  const [starting, setStarting] = useState(false);
  const [remove, setRemove] = useState<api.ProjectExpense | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [modalError, setModalError] = useState("");
  const [impact, setImpact] = useState<{
    activePeopleCount: number;
    futurePeopleCount: number;
  } | null>(null);
  const [planDirty, setPlanDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const [newJob, setNewJob] = useState("");
  const [planMessage, setPlanMessage] = useState("");
  /** 계획 MM 칸에 적은 글자 그대로(저장할 때 시안 규칙으로 검사) */
  const [planText, setPlanText] = useState<Record<string, string>>({});
  /** 시안 toast(…,'info'): 창 안 입력 검사 알림 */
  const [hint, setHint] = useState("");
  const loadVersion = useRef(0);
  const setScreenDirty = useScreenDirty();
  const shortcut = useRef<() => void>(() => undefined);
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        shortcut.current();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
  useEffect(() => {
    if (!dirtyScreenId) return;
    setScreenDirty(dirtyScreenId, planDirty);
    return () => setScreenDirty(dirtyScreenId, false);
  }, [dirtyScreenId, planDirty, setScreenDirty]);
  const load = async () => {
    const version = ++loadVersion.current;
    setError("");
    try {
      const project = await api.getCommonProject(projectId);
      if (version !== loadVersion.current) return;
      const [e, s, h, m] = await Promise.all([
        project.contractTypeCode !== "IN"
          ? api.getCommonProjectDirectExpenses(projectId)
          : Promise.resolve({ items: [] }),
        api.getCommonProjectSalesParts(projectId),
        api.getCommonProjectChangeHistory(projectId),
        api.getCommonProjectManpowerPlan(projectId),
      ]);
      if (version !== loadVersion.current) return;
      setP(project);
      setExpenses(e.items);
      setParts(s.items);
      setHistory(h.items);
      setJobs(m.items);
      setPlanText({});
      setPlanAllowed(m.canSave);
      setPlanDirty(false);
    } catch (reason) {
      if (version === loadVersion.current) setError(api.projectFailure(reason));
    }
  };
  useEffect(() => {
    void load();
    return () => {
      loadVersion.current++;
    };
  }, [projectId]);
  useEffect(() => {
    let active = true;
    Promise.all([
      getCommonCodes("JOB"),
      getCommonCodes("LOSE_RSN"),
      getCommonCodes("DRCT_EXPS_ITEM"),
      getCommonParts(),
    ])
      .then(([j, r, e, s]) => {
        if (!active) return;
        setCodes({ jobs: j.items, reasons: r.items, items: e.items });
        setPartOptions(s.items.filter((part) => part.useYn === "Y"));
      })
      .catch(() => undefined);
    api
      .getCommonProjectBudgetRule()
      .then((r) => active && setRule(r))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);
  const lost = p?.statusCode === "LOSE";
  const writable = !!p && !readOnly && !lost;
  const internal = p?.contractTypeCode === "IN";
  const canSavePlan = writable && !!p?.canSaveManpowerPlan && planAllowed;
  const thisMonth = api.projectToday().slice(0, 7);
  const closedMonth = p?.closedMonth ?? null;
  const nextMonth = (m: string) => {
    let [y, mo] = m.split("-").map(Number);
    mo++;
    if (mo > 12) {
      mo = 1;
      y++;
    }
    return `${y}-${String(mo).padStart(2, "0")}`;
  };
  const firstOpen = closedMonth ? nextMonth(closedMonth) : thisMonth;
  const close = () => (planDirty ? setClosing(true) : onClose());
  const open = async (next: Mode) => {
    setMenu(false);
    setModalError("");
    setImpact(null);
    const current = parts.filter((s) => !s.scheduled).at(-1);
    setValues({
      budgetAmount:
        p?.budgetAmount != null ? p.budgetAmount.toLocaleString("ko-KR") : "",
      salesPartId: partChoices(current?.salesPartId)[0]?.[0] ?? "",
      effectiveMonth: firstOpen > thisMonth ? firstOpen : thisMonth,
      reason: "",
      yearMonth: thisMonth,
      itemCode: codes.items[0]?.code ?? "",
      amount: "",
      memo: "",
      reasonCode: "",
    });
    setMode(next);
    if (next === "lose")
      try {
        setImpact(await api.getCommonProjectLoseImpact(projectId));
      } catch (e) {
        setModalError(api.projectFailure(e));
      }
  };
  /** 새 파트 목록: 지금 파트는 빼되, 잡혀 있는(예정) 변경이 있으면 원래 파트를 골라 취소할 수 있다(시안). */
  const partChoices = (currentId?: string) => {
    const pending = parts.some((s) => s.scheduled);
    const all: [string, string][] = [
      ...partOptions.map((s): [string, string] => [s.partId, s.partName]),
      [api.HQ_PART, "본사(직접 영업)"],
    ];
    return all.filter(([id]) => pending || id !== currentId);
  };
  const run = async (
    action: () => Promise<unknown>,
    message: string,
    closePanel = false,
    kind: ToastKind = "ok",
  ) => {
    if (busy) return;
    setBusy(true);
    setModalError("");
    try {
      await action();
      setMode("");
      setRemove(null);
      setStarting(false);
      if (closePanel) {
        onChanged?.(message, kind);
        onClose();
        return;
      }
      await load();
      onChanged?.(message, kind);
    } catch (e) {
      setModalError(api.projectFailure(e));
    } finally {
      setBusy(false);
    }
  };
  const partName = (id: string) =>
    id === api.HQ_PART
      ? "본사(직접 영업)"
      : (partOptions.find((s) => s.partId === id)?.partName ?? id);
  const submit = () => {
    if (!p) return;
    if (mode === "budget") {
      const v = Number(digits(values.budgetAmount));
      if (!v) return setModalError("예산은 0보다 커야 합니다.");
      return void run(
        () => api.putCommonProjectBudget(projectId, v, p.modifiedAt),
        "예산(목표 원가)을 바꿨습니다.",
      );
    }
    if (mode === "part") {
      // 시안: 적용 시작 월이 틀리면 창은 그대로 두고 info 알림
      if (
        !values.effectiveMonth ||
        (closedMonth && values.effectiveMonth <= closedMonth)
      )
        return setHint(`적용 시작 월은 ${firstOpen} 이후로 골라 주세요.`);
      const current = parts.filter((s) => !s.scheduled).at(-1);
      const cancel = values.salesPartId === current?.salesPartId;
      if (!cancel && current && values.effectiveMonth <= current.effectiveMonth)
        return setHint(
          `적용 시작 월은 ${nextMonth(current.effectiveMonth)} 이후로 골라 주세요.`,
        );
      return void run(
        () =>
          api.putCommonProjectSalesPart(projectId, {
            salesPartId: values.salesPartId,
            effectiveMonth: values.effectiveMonth,
            ...(values.reason.trim() ? { reason: values.reason.trim() } : {}),
          }),
        cancel
          ? "잡혀 있던 영업 파트 변경을 취소했습니다"
          : `영업 파트를 ${partName(values.salesPartId)}(으)로 바꿨습니다 · ${values.effectiveMonth}부터 적용`,
      );
    }
    if (mode === "expense") {
      if (!values.yearMonth) return setModalError("경비 월을 넣어 주세요.");
      if (closedMonth && values.yearMonth <= closedMonth)
        return setModalError(
          "마감한 달에는 넣을 수 없습니다. 이번 달에 넣으면 전월 조정으로 처리됩니다.",
        );
      const amount = Number(digits(values.amount));
      if (!amount) return setModalError("금액을 넣어 주세요.");
      return void run(
        () =>
          api.postCommonProjectDirectExpense(projectId, {
            yearMonth: values.yearMonth,
            itemCode: values.itemCode,
            amount,
            ...(values.memo.trim() ? { memo: values.memo.trim() } : {}),
          }),
        "직접 경비를 추가했습니다.",
      );
    }
    if (mode === "lose") {
      if (!values.reasonCode) return setModalError("실주 사유를 골라 주세요.");
      const a = impact?.activePeopleCount ?? 0;
      const f = impact?.futurePeopleCount ?? 0;
      return void run(
        () =>
          api.postCommonProjectLose(
            projectId,
            values.reasonCode,
            values.memo.trim() || undefined,
          ),
        `실주 처리했습니다.${a ? ` ${a}명 오늘 철수.` : ""}${f ? ` ${f}명 배정 취소.` : ""}`,
        true,
      );
    }
  };
  const savePlan = async () => {
    if (busy || !planDirty) return;
    // 시안 plansave: 비우거나 0 이상 숫자(소수 둘째 자리까지)만
    const wrong = jobs.find((j) => {
      const text = planText[j.jobCode]?.trim();
      return text != null && text !== "" && !/^\d+(\.\d{1,2})?$/.test(text);
    });
    if (wrong)
      return setHint(
        `${wrong.jobName} 계획 MM은 0 이상 숫자(소수 둘째 자리까지)로 넣어 주세요.`,
      );
    setPlanMessage("");
    setBusy(true);
    try {
      await api.putCommonProjectManpowerPlan(
        projectId,
        jobs.map((j) => ({ jobCode: j.jobCode, plannedMm: j.plannedMm ?? 0 })),
      );
      await load();
      onChanged?.(
        "인원 계획을 저장했습니다. 인원 계획 대비 투입(UI-11)에 바로 반영됩니다.",
      );
    } catch (e) {
      setPlanMessage(api.projectFailure(e));
    } finally {
      setBusy(false);
    }
  };
  // 공통 기능: Ctrl+S — 열린 창이 있으면 그 창을 저장, 아니면 인원 계획 탭의 저장
  shortcut.current = () => {
    if (mode) submit();
    else if (tab === "plan" && planDirty && canSavePlan) void savePlan();
  };
  const set = (key: string, value: string) =>
    setValues((v) => ({ ...v, [key]: value }));
  const fc = (
    id: string,
    label: string,
    required: boolean,
    control: ReactNode,
    help?: string,
  ) => (
    <ProjectField id={id} label={label} required={required} help={help}>
      {control}
    </ProjectField>
  );
  const moneyInput = (id: string, key: string, width: number) => (
    <span className="project-row">
      <input
        id={id}
        data-testid={id}
        className="project-money"
        style={{ width }}
        inputMode="numeric"
        value={values[key] ?? ""}
        onChange={(e) => set(key, money(e.target.value))}
      />
      <span className="project-sub">원</span>
    </span>
  );
  if (!p)
    return (
      <DetailPanel title="프로젝트" onClose={onClose} testId="project-detail">
        {error ? (
          <Banner
            kind="bad"
            action={
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="project-detail-retry"
                onClick={() => void load()}
              >
                다시 시도
              </button>
            }
          >
            <span data-testid="project-detail-error">{error}</span>
          </Banner>
        ) : (
          <p className="project-sub" role="status">
            프로젝트를 불러오는 중…
          </p>
        )}
      </DetailPanel>
    );
  const done = p.statusCode === "END" || p.statusCode === "LOSE";
  const [sy, sm] = p.startDate.split("-").map(Number);
  const [ey, em] = p.endDate.split("-").map(Number);
  const projectMonths = (ey - sy) * 12 + (em - sm) + 1;
  const jobChoices = codes.jobs.filter(
    (o) => !jobs.some((j) => j.jobCode === o.code),
  );
  const jobChoice = jobChoices.some((j) => j.code === newJob)
    ? newJob
    : (jobChoices[0]?.code ?? "");
  const openIssues = (p.issues ?? []).filter((i) => i.statusCode !== "CLOSE");
  const assignments = p.assignments ?? [];
  const thisMonthExpense = expenses
    .filter((e) => e.yearMonth === thisMonth)
    .reduce((s, e) => s + e.amount, 0);
  const partHistory = parts.length > 1 && (
    <div
      className="project-cap project-part-history"
      data-testid="project-part-history"
    >
      변경 이력:{" "}
      {parts.map((s, i) => (
        <span key={`${s.effectiveMonth}-${i}`}>
          {i > 0 && " → "}
          {s.effectiveMonth}~{" "}
          {s.salesPartId === api.HQ_PART ? "본사" : s.salesPartName}
          {s.scheduled && " (예정)"}
          {s.reason && (
            <span
              className="project-sub"
              title={`사유: ${s.reason} · ${s.changedBy}`}
              data-testid={`project-part-reason-${i}`}
            >
              {" "}
              ⓘ
            </span>
          )}
        </span>
      ))}
    </div>
  );
  const expenseBlock = (
    <div className="project-expenses" data-testid="project-expenses">
      {expenses.length ? (
        <div className="ls-twrap project-expense-wrap">
          <table className="ls-t">
            <thead>
              <tr>
                <th>월</th>
                <th>항목</th>
                <th className="ls-right">금액</th>
                <th>메모</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {expenses.map((e) => (
                <tr
                  key={e.expenseId}
                  data-testid={`project-expense-${e.expenseId.replace(":", "-")}`}
                >
                  <td>{e.yearMonth}</td>
                  <td>{e.itemName}</td>
                  <td className="ls-right">{api.won(e.amount)}</td>
                  <td className="project-sub">{e.memo ?? ""}</td>
                  <td>
                    {closedMonth && e.yearMonth <= closedMonth ? (
                      <span className="project-sub">마감</span>
                    ) : (
                      writable && (
                        <button
                          type="button"
                          className="ls-ibtn ls-ibtn-sm"
                          aria-label="삭제"
                          data-testid={`project-expense-delete-${e.expenseId.replace(":", "-")}`}
                          onClick={() => {
                            setModalError("");
                            setRemove(e);
                          }}
                        >
                          {xIcon}
                        </button>
                      )
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <span className="project-sub">없음</span>
      )}
      <div className="project-row project-expense-foot">
        {writable && (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="project-expense-add"
            onClick={() => void open("expense")}
          >
            {plusIcon} 경비 추가
          </button>
        )}
        <span className="project-cap">
          출장비·회식비처럼 그 달에만 드는 비용(회사 지급). 그 달 이 프로젝트
          원가에만 들어갑니다(사람에게 나누지 않음, 식-11).
          {thisMonthExpense ? ` 이번 달 ${api.won(thisMonthExpense)}원` : ""}
        </span>
      </div>
    </div>
  );
  const change = (testId: string, next: Mode) =>
    writable && (
      <button
        type="button"
        className="ls-btn ls-btn-sm ls-btn-t"
        data-testid={testId}
        onClick={() => void open(next)}
      >
        변경
      </button>
    );
  const basic = (
    <>
      {!mode && modalError && (
        <Banner kind="bad">
          <span data-testid="project-detail-error">{modalError}</span>
        </Banner>
      )}
      {lost && (
        <Banner kind="lock" icon={lockIcon}>
          실주 {p.loseDate ?? ""} · 사유: {p.loseReasonName ?? ""}
          {p.loseMemo ? ` — ${p.loseMemo}` : ""}
        </Banner>
      )}
      <KeyValues
        items={[
          ["계약유형", <Badge kind="neutral">{api.typeLabel(p)}</Badge>],
          ["계약 기간", `${p.startDate} ~ ${p.endDate}`],
          ["계약금액", internal ? "–" : `${api.won(p.contractAmount)} 원`],
          [
            "예산(목표 원가)",
            <>
              {internal ? "–" : `${api.won(p.budgetAmount)} 원`}{" "}
              {!internal &&
                p.canChangeBudget &&
                change("project-budget-change", "budget")}
            </>,
          ],
          [
            "영업 파트",
            <>
              <PartLabel project={p} />{" "}
              {!internal &&
                p.canChangeSalesPart &&
                change("project-part-change", "part")}
              {partHistory}
            </>,
          ],
          ["근무지", internal ? "–" : p.workplace || "–"],
          [
            "팀비(월)·프로젝트경비(월)",
            internal
              ? "–"
              : `${api.won(p.teamExpenseAmount)} 원 · ${api.won(p.projectExpenseAmount)} 원 (1인당)`,
          ],
          ...(internal
            ? []
            : [["추가 직접 경비", expenseBlock] as [string, ReactNode]]),
          ["PM", p.pmName || "–"],
          ...(p.trafficLight && !done
            ? [
                [
                  "진행 상태",
                  <>
                    <Badge kind={lightKind(p.trafficLight)}>
                      {p.trafficLight}
                    </Badge>{" "}
                    <button
                      type="button"
                      className="project-lnk"
                      data-testid="project-progress-open"
                      onClick={() => setTab("progress")}
                    >
                      진행 현황 보기
                    </button>
                  </>,
                ] as [string, ReactNode],
              ]
            : []),
          [
            "상태",
            <>
              <Badge kind={api.statusKind(p.statusCode)}>{p.statusName}</Badge>
              {p.statusCode === "PRE" && (
                <span className="project-cap">
                  {" "}
                  수주예정이면 [⋯]에서 진행 또는 실주로 바꾸고, 종료는 계약
                  종료일이 지나면 자동입니다.
                </span>
              )}
            </>,
          ],
          ["특징", <span className="project-pre">{p.feature || "–"}</span>],
        ]}
      />
      {p.statusCode === "PRE" && p.preContractPeopleCount > 0 && (
        <Banner kind="warn" icon={warnIcon}>
          계약 전에 {p.preContractPeopleCount}명이 먼저
          투입되었습니다(미계약 선투입).
        </Banner>
      )}
    </>
  );
  const today = api.projectToday();
  const stepState = (s: {
    plannedStartDate: string;
    plannedEndDate: string;
    completedDate: string | null;
  }) =>
    s.completedDate
      ? "완료"
      : s.plannedEndDate < today
        ? "지연"
        : s.plannedStartDate <= today
          ? "진행 중"
          : "예정";
  const stepKind = (state: string) =>
    state === "완료"
      ? "ok"
      : state === "지연"
        ? "bad"
        : state === "진행 중"
          ? "info"
          : "neutral";
  const steps = p.progress?.steps ?? [];
  // 시안 cur: 완료되지 않은 첫 단계, 모두 완료면 마지막 단계
  const currentStep =
    steps.find((s) => !s.completedDate) ?? steps[steps.length - 1];
  // 진척률은 소수 첫째 자리(Q-식-72, 현황판과 같게)
  const one = (value: number | null | undefined) =>
    value == null ? null : Math.round(Number(value) * 10) / 10;
  const planRate = one(p.progress?.plannedProgressRate);
  const actRate =
    one(p.progress?.actualProgressRate) ??
    // 턴키·내부는 실적을 안 적었으면 0%로 본다(식-74)
    (planRate != null &&
    (p.contractTypeCode === "TK" || p.contractTypeCode === "IN")
      ? 0
      : null);
  const operational = p.contractTypeCode === "MO" || p.contractTypeCode === "DP";
  const lastMemo = p.memos?.[0];
  // 마지막 업데이트: 서버 값(메모 작성일, 없으면 단계를 처음 만든 날)을 쓰고, 없으면 메모에서 센다.
  const lastUpdate = p.lastUpdateDate ?? lastMemo?.writtenDate ?? null;
  const daysAgo =
    p.daysWithoutUpdate ??
    (lastUpdate
      ? Math.round(
          (Date.parse(`${today}T00:00:00Z`) -
            Date.parse(`${lastUpdate}T00:00:00Z`)) /
            86400000,
        )
      : null);
  const stale =
    p.staleUpdate ??
    (daysAgo != null &&
      daysAgo > 14 &&
      p.statusCode !== "END" &&
      p.statusCode !== "LOSE");
  const reasons = p.signalReasons ?? [];
  const signedGap = (value: number) =>
    `${value >= 0 ? "+" : ""}${Math.round(value * 10) / 10}%p`;
  const bar = (
    label: string,
    value: number | null | undefined,
    muted: boolean,
  ) => (
    <div className="project-bar">
      <span className="project-cap project-bar-label">{label}</span>
      <div className="project-track">
        <div
          className={`project-fill${muted ? " project-fill-plan" : ""}`}
          style={{ width: `${Math.min(100, value ?? 0)}%` }}
        />
      </div>
      <span className="project-cap project-bar-value">
        {value == null ? "–" : `${value}%`}
      </span>
    </div>
  );
  const progressSummary = (
    <>
      {/* 시안 sig: 현황판과 같은 판정(식-76)의 신호등과 이유. 메모·원가가 없어도 띠는 보인다. */}
      {!done && (
        <div data-testid="project-progress-signal">
          <Banner
            kind={
              p.trafficLight === "위험"
                ? "bad"
                : p.trafficLight === "주의"
                  ? "warn"
                  : "info"
            }
            icon={
              p.trafficLight === "위험" || p.trafficLight === "주의"
                ? warnIcon
                : infoIcon
            }
          >
            <strong>{p.trafficLight ?? "정상"}</strong> —{" "}
            {reasons.length ? reasons.join(" · ") : "계획대로 진행 중입니다."}
          </Banner>
        </div>
      )}
      <KeyValues
        items={[
          ["PM", p.pmName || "–"],
          [
            "현재 단계",
            currentStep ? (
              <>
                {currentStep.stepName}{" "}
                <Badge kind={stepKind(stepState(currentStep))}>
                  {stepState(currentStep)}
                </Badge>
              </>
            ) : (
              "–"
            ),
          ],
          [
            "진척률",
            <>
              <div className="project-bars">
                {bar("계획", planRate, true)}
                {bar("실적", actRate, false)}
              </div>
              <div className="project-cap">
                {operational
                  ? "운영형은 기간 기준으로 자동 계산"
                  : planRate != null && actRate != null
                    ? `계획 대비 ${signedGap(actRate - planRate)}`
                    : ""}
              </div>
            </>,
          ],
          ...(p.costUsageRate != null
            ? [
                [
                  "원가 사용률",
                  <>
                    <span
                      className={
                        (p.costProgressGap ?? 0) >= 15
                          ? "project-bad"
                          : (p.costProgressGap ?? 0) >= 5
                            ? "project-warn-text"
                            : ""
                      }
                    >
                      {Number(p.costUsageRate).toFixed(1)}%
                    </span>{" "}
                    <span className="project-cap">
                      {p.costProgressGap != null
                        ? `진척률보다 ${signedGap(p.costProgressGap)} · `
                        : ""}
                      누적 원가 ÷ 예산
                    </span>
                  </>,
                ] as [string, ReactNode],
              ]
            : []),
          [
            "마지막 업데이트",
            lastUpdate ? (
              <>
                {/* 시안: 끝나지 않은 프로젝트가 14일 넘게 업데이트가 없으면 경고 색 */}
                {lastUpdate}{" "}
                <span
                  className={stale ? "project-warn-text" : "project-sub"}
                  data-testid="project-last-update-age"
                >
                  ({daysAgo}일 전)
                </span>
              </>
            ) : (
              "–"
            ),
          ],
        ]}
      />
    </>
  );
  const progress = (
    <>
      {progressSummary}
      <h3 className="project-h3">단계</h3>
      <div className="ls-twrap">
        <table className="ls-t" data-testid="project-steps">
          <thead>
            <tr>
              <th>단계</th>
              <th className="ls-right">비중</th>
              <th>예정 기간</th>
              <th>완료일</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {steps.map((s, i) => (
              <tr key={i} data-testid={`project-progress-${i}`}>
                <td>{s.stepName}</td>
                <td className="ls-right">{s.weightRate}%</td>
                <td>
                  {s.plannedStartDate.slice(2, 7)} ~{" "}
                  {s.plannedEndDate.slice(2, 7)}
                </td>
                <td>{s.completedDate ? s.completedDate.slice(2) : "–"}</td>
                <td>
                  <Badge kind={stepKind(stepState(s))}>{stepState(s)}</Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!steps.length && (
          <p className="project-sub project-empty">등록된 단계가 없습니다.</p>
        )}
      </div>
      <h3 className="project-h3">주간 메모</h3>
      {(p.memos ?? []).length ? (
        <div className="project-memos" data-testid="project-memos">
          {(p.memos ?? []).map((m, i) => (
            <div key={i} className="project-memo">
              <div className="project-memo-head">
                <strong>{m.writtenDate}</strong>
                {m.actualProgressRate != null && (
                  <span className="project-cap">
                    실적 진척률 {m.actualProgressRate}%
                  </span>
                )}
              </div>
              {m.thisWeek && (
                <div>
                  <span className="project-cap">한 일</span> {m.thisWeek}
                </div>
              )}
              {m.nextWeek && (
                <div>
                  <span className="project-cap">할 일</span> {m.nextWeek}
                </div>
              )}
              {m.request && (
                <div>
                  <span className="project-cap">요청</span>{" "}
                  <span className="project-warn-text">{m.request}</span>
                </div>
              )}
            </div>
          ))}
        </div>
      ) : (
        <p className="project-sub project-empty">적은 주간 메모가 없습니다.</p>
      )}
    </>
  );
  const issues = (
    <>
      <p className="project-cap">기한이 지난 이슈는 빨간색으로 보입니다.</p>
      <div className="ls-twrap">
        <table className="ls-t" data-testid="project-issues">
          <thead>
            <tr>
              <th>중요도</th>
              <th>제목 · 조치 내용</th>
              <th>담당자</th>
              <th>기한</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {(p.issues ?? []).map((i, index) => {
              const overdue =
                !!i.dueDate && i.dueDate < today && i.statusCode !== "CLOSE";
              return (
                <tr
                  key={index}
                  data-testid={`project-issue-${index}`}
                  className={overdue ? "project-overdue" : undefined}
                >
                  <td>
                    <Badge
                      kind={
                        i.importanceCode === "HIGH"
                          ? "bad"
                          : i.importanceCode === "MID"
                            ? "warn"
                            : "neutral"
                      }
                    >
                      {i.importanceName}
                    </Badge>
                  </td>
                  <td className="project-wrap">
                    {i.issueTitle}
                    {i.action && <div className="project-cap">{i.action}</div>}
                  </td>
                  <td>{p.pmName || "–"}</td>
                  <td>{i.dueDate ? i.dueDate.slice(5) : "–"}</td>
                  <td>
                    <Badge
                      kind={
                        i.statusCode === "OPEN"
                          ? "info"
                          : i.statusCode === "ACT"
                            ? "warn"
                            : "lock"
                      }
                    >
                      {i.statusName}
                    </Badge>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {!p.issues?.length && (
          <p className="project-sub project-empty">등록된 이슈가 없습니다.</p>
        )}
      </div>
    </>
  );
  const plan = (
    <>
      <p className="project-cap">
        계획 MM은 프로젝트 전체 기간({p.startDate.slice(0, 7)} ~{" "}
        {p.endDate.slice(0, 7)}, {projectMonths}개월)의 직무별 합계입니다. 실제
        MM은 이 프로젝트 투입 배정의 월 MM을 모두 더한 값(예정 포함)이라
        자동으로 채워집니다.
      </p>
      <Banner kind="info" icon={infoIcon}>
        계획보다 모자라는 MM(미충원)은 인력 수요 예측의 필요 인력에 잡힙니다.
        더 투입하지 않으면 계획 MM을 실제에 맞게 줄여 주세요.
      </Banner>
      <div className="ls-twrap">
        <table className="ls-t" data-testid="project-plan">
          <thead>
            <tr>
              <th>직무</th>
              <th className="ls-right">계획 MM</th>
              <th className="ls-right">실제 MM</th>
              <th>차이</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((j, i) => {
              const d = j.differenceMm ?? j.actualMm - (j.plannedMm ?? 0);
              return (
                <tr
                  key={j.jobCode}
                  data-testid={`project-job-${j.jobCode.toLowerCase()}`}
                >
                  <td>{j.jobName}</td>
                  <td className="ls-right">
                    {canSavePlan ? (
                      <input
                        className="project-mm"
                        placeholder="입력"
                        aria-label={`${j.jobName} 계획 MM`}
                        data-testid={`project-job-mm-${j.jobCode.toLowerCase()}`}
                        inputMode="decimal"
                        value={planText[j.jobCode] ?? j.plannedMm ?? ""}
                        disabled={busy}
                        onChange={(e) => {
                          // 적은 글자는 그대로 두고 저장할 때 검사한다(시안). 숫자로 읽히면 차이를 바로 다시 센다.
                          const text = e.target.value;
                          const raw = text.trim();
                          setPlanText((t) => ({ ...t, [j.jobCode]: text }));
                          setJobs((all) =>
                            all.map((x, n) => {
                              if (n !== i) return x;
                              const v =
                                raw === ""
                                  ? null
                                  : Number.isFinite(Number(raw))
                                    ? Number(raw)
                                    : x.plannedMm;
                              return {
                                ...x,
                                plannedMm: v,
                                differenceMm: x.actualMm - (v ?? 0),
                              };
                            }),
                          );
                          setPlanDirty(true);
                        }}
                      />
                    ) : (
                      mm(j.plannedMm)
                    )}
                  </td>
                  <td className="ls-right">{mm(j.actualMm)}</td>
                  <td>
                    {d == null ? (
                      "–"
                    ) : d < 0 ? (
                      <Badge kind="bad">부족 {Math.abs(d).toFixed(2)}</Badge>
                    ) : d > 0 ? (
                      <Badge kind="warn">초과 {d.toFixed(2)}</Badge>
                    ) : (
                      <Badge kind="ok">맞음</Badge>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
          {jobs.length > 0 && (
            <tfoot>
              <tr>
                <td>합계</td>
                <td className="ls-right">
                  {mm(jobs.reduce((s, j) => s + (j.plannedMm ?? 0), 0))}
                </td>
                <td className="ls-right">
                  {mm(jobs.reduce((s, j) => s + j.actualMm, 0))}
                </td>
                <td />
              </tr>
            </tfoot>
          )}
        </table>
        {!jobs.length && (
          <p className="project-sub project-empty">
            투입 배정과 계획이 아직 없습니다. [직무 추가]로 계획을 먼저 넣을 수
            있습니다.
          </p>
        )}
      </div>
      {planMessage && (
        <p role="alert" className="project-error">
          {planMessage}
        </p>
      )}
      {canSavePlan && (
        <div className="project-row project-plan-foot">
          <select
            className="ls-in"
            aria-label="추가할 직무"
            data-testid="project-job-select"
            value={jobChoice}
            onChange={(e) => setNewJob(e.target.value)}
          >
            {jobChoices.map((j) => (
              <option key={j.code} value={j.code}>
                {j.codeName}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="project-job-add"
            disabled={!jobChoice}
            onClick={() => {
              const j = codes.jobs.find((x) => x.code === jobChoice)!;
              setJobs((all) => [
                ...all,
                {
                  jobCode: j.code,
                  jobName: j.codeName,
                  plannedMm: null,
                  actualMm: 0,
                  differenceMm: 0,
                },
              ]);
              setPlanDirty(true);
              setNewJob("");
            }}
          >
            {plusIcon} 직무 추가
          </button>
          <span className="project-spacer" />
          {planDirty && (
            <>
              <span className="project-cap">저장하지 않은 변경이 있습니다</span>
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="project-plan-cancel"
                onClick={() => void load()}
              >
                취소
              </button>
            </>
          )}
          <button
            type="button"
            className="ls-btn ls-btn-sm ls-btn-p"
            data-testid="project-plan-save"
            disabled={busy || !planDirty}
            onClick={() => void savePlan()}
          >
            저장
          </button>
        </div>
      )}
      <p className="project-note">
        처음에는 비어 있습니다. 사람을 보내기 전에 「부족」이 뜨는 것은
        정상입니다(아직 투입하지 않았다는 표시). 범위가 줄면 계획 MM을 고쳐
        맞춥니다. 차이는 허용 오차 없이 0일 때만 「맞음」입니다. 수정은 이
        프로젝트의 PM({p.pmName || "–"})과 관리자만 할 수 있습니다.
        {!canSavePlan && !done && (
          <strong> 지금은 보기만 할 수 있습니다.</strong>
        )}
      </p>
    </>
  );
  const people = (
    <div className="ls-twrap">
      <table className="ls-t" data-testid="project-assignments">
        <thead>
          <tr>
            <th>이름</th>
            <th>직무</th>
            <th>기간</th>
            <th className="ls-right">MM</th>
          </tr>
        </thead>
        <tbody>
          {assignments.map((a, i) => (
            <tr key={i} data-testid={`project-assignment-${i}`}>
              <td>
                <span className="project-pname">
                  {a.name}
                  {a.managementNumber && <small>{a.managementNumber}</small>}
                </span>
              </td>
              <td>{a.jobName}</td>
              <td>
                {a.startDate} ~ {a.endDate}
              </td>
              <td className="ls-right">{mm(a.mm)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!assignments.length && (
        <p className="project-sub project-empty">투입 인력이 없습니다.</p>
      )}
    </div>
  );
  const changes = (
    <>
      <div className="ls-twrap">
        <table className="ls-t" data-testid="project-history">
          <thead>
            <tr>
              <th>변경일</th>
              <th>항목</th>
              <th>변경 전</th>
              <th>변경 후</th>
              <th>처리자</th>
            </tr>
          </thead>
          <tbody>
            {history.map((h, i) => (
              <tr key={i} data-testid={`project-history-${i}`}>
                <td>{formatDateTime(h.changedAt).slice(0, 10)}</td>
                <td>{h.itemName}</td>
                <td>{h.beforeValue ?? ""}</td>
                <td>{h.afterValue ?? ""}</td>
                <td>{h.changedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="project-note">
        항목은 공통코드(UI-27)의 「변경 항목」 코드로 관리합니다. [수정]
        화면에서 고친 내용이 바뀐 항목마다 자동으로 쌓입니다.
      </p>
    </>
  );
  const tabs = [
    { key: "basic", label: "기본 정보", content: basic },
    ...(done && !renderIssues && !boardOperations
      ? []
      : [
          ...(!done || boardOperations
            ? [
                {
                  key: "progress",
                  label: "진행 현황",
                  content: boardOperations ? (
                    <>
                      {progressSummary}
                      <ProjectProgressPanel
                        projectId={p.projectId}
                        projectName={p.projectName}
                        contractTypeCode={p.contractTypeCode}
                        statusCode={p.statusCode}
                        startDate={p.startDate}
                        endDate={p.endDate}
                        plannedProgress={planRate}
                        actualProgress={actRate}

                        readOnly={readOnly}
                        onChanged={(message) => {
                          void load();
                          onChanged?.(message);
                        }}
                      />
                    </>
                  ) : (
                    progress
                  ),
                },
              ]
            : []),
          {
            key: "issues",
            label: `이슈 (${openIssues.length})`,
            content: renderIssues
              ? renderIssues(p, (message) => {
                  void load();
                  onChanged?.(message);
                })
              : issues,
          },
        ]),
    { key: "plan", label: "인원 계획", content: plan },
    {
      key: "people",
      label: `투입 인력 (${assignments.length})`,
      content: people,
    },
    { key: "history", label: "계약 변경 이력", content: changes },
  ];
  const actions = (
    <>
      {writable && p.canEdit && (
        <button
          type="button"
          className="ls-btn ls-btn-sm"
          data-testid="project-edit"
          onClick={() => onEdit?.(p)}
        >
          수정
        </button>
      )}
      {p.contractTypeCode === "TK" && (
        <button
          type="button"
          className="ls-btn ls-btn-sm"
          data-testid="project-distribution-open"
          onClick={() => distribution(p.projectId)}
        >
          배분표
        </button>
      )}
      {writable && p.statusCode === "PRE" && (p.canStart || p.canLose) && (
        <span className="project-menu-anchor">
          <button
            type="button"
            className="ls-ibtn"
            aria-label="더 보기"
            aria-expanded={menu}
            data-testid="project-more"
            onClick={() => setMenu((v) => !v)}
          >
            {moreIcon}
          </button>
          {menu && (
            <span className="project-menu" role="menu">
              {p.canStart && (
                <button
                  type="button"
                  role="menuitem"
                  data-testid="project-start"
                  onClick={() => {
                    setMenu(false);
                    setModalError("");
                    setStarting(true);
                  }}
                >
                  진행으로
                </button>
              )}
              {p.canLose && (
                <button
                  type="button"
                  role="menuitem"
                  data-testid="project-lose"
                  onClick={() => void open("lose")}
                >
                  실주 처리
                </button>
              )}
            </span>
          )}
        </span>
      )}
    </>
  );
  const current = parts.filter((s) => !s.scheduled).at(-1);
  const pending = parts.filter((s) => s.scheduled);
  const pendingText = pending
    .map((s) => `${s.effectiveMonth}부터 ${partName(s.salesPartId)}`)
    .join(", ");
  const modal = mode && (
    <Dialog
      title={
        {
          budget: "예산(목표 원가) 변경",
          part: "영업 파트 변경",
          expense: "직접 경비 추가",
          lose: "실주 처리",
        }[mode]
      }
      testId="project-command-dialog"
      closable
      onClose={() => !busy && setMode("")}
    >
      <form
        className="project-modal"
        data-testid="project-command-form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        {mode === "budget" && (
          <>
            <p>
              「{p.projectName}」의 예산(목표 원가)을 바꿉니다. 소진율·저마진
              경보 기준으로 바로 쓰입니다.
            </p>
            {fc(
              "project-budget-value",
              "새 예산",
              true,
              moneyInput("project-budget-value", "budgetAmount", 200),
              rule
                ? `기본값(계약금액 × (1 − 목표 마진율 ${Math.round(rule.targetMarginRate)}%))은 ${api.won(api.defaultBudget(p.contractAmount, rule))}원입니다.`
                : undefined,
            )}
          </>
        )}
        {mode === "part" && (
          <>
            <p>
              「{p.projectName}」의 영업 파트를 바꿉니다. 적용 시작 월부터 새
              파트로 잡히고, 마감한 달
              {closedMonth ? `(${closedMonth}까지)` : ""}의 실적·정산은 바뀌지
              않습니다.
            </p>
            {pending.length > 0 && (
              <div className="project-note-box">
                마감 안 된 달부터 잡힌 변경이 있습니다: <b>{pendingText}</b>.
                저장하면 이 변경은 지워지고 새로 넣은 내용으로 바뀝니다. 원래
                파트(
                {current ? partName(current.salesPartId) : "미지정"})를 고르면
                이 변경이 취소됩니다.
              </div>
            )}
            {fc(
              "project-part-value",
              "새 영업 파트",
              true,
              <select
                id="project-part-value"
                data-testid="project-command-part"
                value={values.salesPartId}
                onChange={(e) => set("salesPartId", e.target.value)}
              >
                {partChoices(current?.salesPartId).map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </select>,
            )}
            {fc(
              "project-part-month",
              "적용 시작 월",
              true,
              <input
                id="project-part-month"
                data-testid="project-command-effectivemonth"
                type="month"
                min={closedMonth ? firstOpen : undefined}
                value={values.effectiveMonth}
                onChange={(e) => set("effectiveMonth", e.target.value)}
              />,
              `마감 안 된 달(${firstOpen})부터 고를 수 있습니다.`,
            )}
            {fc(
              "project-part-reason",
              "사유",
              false,
              <input
                id="project-part-reason"
                data-testid="project-command-reason"
                maxLength={500}
                placeholder="예: 담당 파트 조정"
                value={values.reason}
                onChange={(e) => set("reason", e.target.value)}
              />,
            )}
          </>
        )}
        {mode === "expense" && (
          <>
            <p className="project-sub">{p.projectName}</p>
            {fc(
              "project-expense-month",
              "경비 월",
              true,
              <input
                id="project-expense-month"
                data-testid="project-command-yearmonth"
                type="month"
                style={{ width: 160 }}
                value={values.yearMonth}
                onChange={(e) => set("yearMonth", e.target.value)}
              />,
            )}
            {fc(
              "project-expense-item",
              "항목",
              true,
              <select
                id="project-expense-item"
                data-testid="project-command-itemcode"
                value={values.itemCode}
                onChange={(e) => set("itemCode", e.target.value)}
              >
                {codes.items.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>,
              "공통코드 「직접 경비 항목」에서 고릅니다.",
            )}
            {fc(
              "project-command-amount",
              "금액",
              true,
              moneyInput("project-command-amount", "amount", 180),
              "이 프로젝트에서 쓴 금액",
            )}
            {fc(
              "project-expense-memo",
              "메모",
              false,
              <input
                id="project-expense-memo"
                data-testid="project-command-memo"
                maxLength={500}
                placeholder="예: 고객사 지방 센터 점검"
                value={values.memo}
                onChange={(e) => set("memo", e.target.value)}
              />,
            )}
          </>
        )}
        {mode === "lose" && (
          <>
            <p>
              「{p.projectName}」를 실주로 바꿉니다. 영업 파이프라인은 바뀌지
              않습니다.
            </p>
            {fc(
              "project-lose-reason",
              "실주 사유",
              true,
              <select
                id="project-lose-reason"
                data-testid="project-command-reasoncode"
                value={values.reasonCode}
                onChange={(e) => set("reasonCode", e.target.value)}
              >
                <option value="">선택</option>
                {codes.reasons.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>,
            )}
            {fc(
              "project-lose-memo",
              "메모",
              false,
              <textarea
                id="project-lose-memo"
                data-testid="project-command-memo"
                maxLength={500}
                value={values.memo}
                onChange={(e) => set("memo", e.target.value)}
              />,
            )}
            {impact &&
              (impact.activePeopleCount > 0 ||
                impact.futurePeopleCount > 0) && (
                <Banner kind="warn" icon={warnIcon}>
                  {[
                    impact.activePeopleCount
                      ? `투입 중인 사람 ${impact.activePeopleCount}명은 오늘 날짜로 철수`
                      : "",
                    impact.futurePeopleCount
                      ? `시작 전 ${impact.futurePeopleCount}명은 배정 취소`
                      : "",
                  ]
                    .filter(Boolean)
                    .join(", ")}
                  됩니다.
                </Banner>
              )}
          </>
        )}
        {modalError && (
          <p
            role="alert"
            className="project-errmsg"
            data-testid="project-command-error"
          >
            {modalError}
          </p>
        )}
        <div className="app-actions">
          <button
            type="button"
            data-testid="project-command-cancel"
            disabled={busy}
            onClick={() => setMode("")}
          >
            취소
          </button>
          <button
            type="submit"
            className={mode === "lose" ? "app-danger" : "primary-button"}
            data-testid="project-command-save"
            disabled={busy || (mode === "lose" && !impact && !modalError)}
          >
            {
              {
                budget: "저장",
                part: "변경",
                expense: "추가",
                lose: "실주 처리",
              }[mode]
            }
          </button>
        </div>
      </form>
    </Dialog>
  );
  return (
    <>
      <DetailPanel
        title={p.projectName}
        sub={`${p.projectId} · ${p.customerName} · ${api.typeLabel(p)}`}
        actions={actions}
        tabs={tabs}
        activeTab={tab}
        onTab={setTab}
        onClose={close}
        testId="project-detail"
      />
      {modal}
      {starting && (
        <ConfirmDialog
          title={`「${p.projectName}」를 진행으로 바꿀까요?`}
          description="계약이 끝나 정식으로 진행하는 프로젝트로 바꿉니다. 진행에서 수주예정으로는 되돌릴 수 없습니다."
          proceedLabel="진행으로"
          testId="project-start-confirm"
          onCancel={() => setStarting(false)}
          onProceed={() =>
            void run(
              () => api.postCommonProjectStart(projectId),
              "진행으로 바꿨습니다.",
              true,
            )
          }
        />
      )}
      {remove && (
        <ConfirmDialog
          title={`${remove.yearMonth} ${remove.itemName} ${api.won(remove.amount)}원을 지울까요?`}
          description="마감 전 달만 지울 수 있습니다."
          destructive
          proceedLabel="삭제"
          testId="project-expense-delete-confirm"
          onCancel={() => setRemove(null)}
          onProceed={() =>
            void run(
              () =>
                api.postCommonProjectDirectExpenseDelete(
                  projectId,
                  remove.expenseId,
                ),
              "직접 경비를 지웠습니다.",
            )
          }
        />
      )}
      {closing && (
        <ConfirmDialog
          title="변경 내용을 버릴까요?"
          description="저장하지 않은 인원 계획이 사라집니다."
          proceedLabel="버리기"
          testId="project-close-confirm"
          onCancel={() => setClosing(false)}
          onProceed={onClose}
        />
      )}
      {hint && (
        <Toast
          message={hint}
          kind="info"
          onDismiss={() => setHint("")}
          testId="project-detail-toast"
        />
      )}
    </>
  );
}
