import { useEffect, useRef, useState } from "react";
import { Dialog, ConfirmDialog } from "../../shared/feedback/Dialog";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonParts } from "../parts/parts";
import { ProjectForm } from "./ProjectForm";
import * as api from "./projects";
import "./projects.css";

type Mode = "budget" | "part" | "expense" | "lose" | "start" | "";
const tabs = [
  "기본정보",
  "진행현황",
  "이슈",
  "인원계획",
  "투입인력",
  "계약변경이력",
];
export function ProjectDetail({
  projectId,
  onClose,
  readOnly = false,
  onChanged,
  onDistribution,
  onEdit,
}: {
  projectId: string;
  onClose: () => void;
  readOnly?: boolean;
  onChanged?: () => void;
  onDistribution?: (id: string) => void;
  onEdit?: (project: api.Project) => void;
}) {
  const [p, setP] = useState<api.Project | null>(null);
  const [tab, setTab] = useState("기본정보");
  const [expenses, setExpenses] = useState<api.ProjectExpense[]>([]);
  const [parts, setParts] = useState<api.ProjectPart[]>([]);
  const [history, setHistory] = useState<api.ProjectHistory[]>([]);
  const [jobs, setJobs] = useState<api.ProjectJob[]>([]);
  const [options, setOptions] = useState<
    Record<string, { code: string; codeName: string }[]>
  >({});
  const [partOptions, setPartOptions] = useState<
    { partId: string; partName: string }[]
  >([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const [edit, setEdit] = useState(false);
  const [mode, setMode] = useState<Mode>("");
  const [remove, setRemove] = useState<api.ProjectExpense | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [values, setValues] = useState<Record<string, string>>({});
  const [impact, setImpact] = useState<{
    activePeopleCount: number;
    futurePeopleCount: number;
  } | null>(null);
  const [planDirty, setPlanDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const [newJob, setNewJob] = useState("");
  const [loading, setLoading] = useState(true);
  const [planAllowed, setPlanAllowed] = useState(false);
  const loadVersion = useRef(0);
  const load = async () => {
    const version = ++loadVersion.current;
    setError("");
    setLoading(true);
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
      setPlanAllowed(m.canSave);
      setPlanDirty(false);
    } catch (reason) {
      if (version === loadVersion.current) setError(api.projectFailure(reason));
    } finally {
      if (version === loadVersion.current) setLoading(false);
    }
  };
  useEffect(() => {
    setP(null);
    setMode("");
    setEdit(false);
    setRemove(null);
    setTab("기본정보");
    setSuccess("");
    setFields({});
    setPlanDirty(false);
    setClosing(false);
    setNewJob("");
    void load();
    return () => {
      loadVersion.current++;
    };
  }, [projectId]);
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      getCommonCodes("JOB"),
      getCommonCodes("LOSE_RSN"),
      getCommonCodes("DRCT_EXPS_ITEM"),
      getCommonParts(),
    ])
      .then(([j, r, e, s]) => {
        if (!cancelled) {
          setOptions({
            jobCode: j.items,
            reasonCode: r.items,
            itemCode: e.items,
          });
          setPartOptions(s.items.filter((part) => part.useYn === "Y"));
        }
      })
      .catch((e) => !cancelled && setError(api.projectFailure(e)));
    return () => {
      cancelled = true;
    };
  }, []);
  const writable =
    !!p &&
    p.projectId === projectId &&
    !readOnly &&
    !loading &&
    p.statusCode !== "LOSE";
  const canSavePlan = writable && p?.canSaveManpowerPlan && planAllowed;
  const startMode = async (next: Mode) => {
    setMode(next);
    setFields({});
    setError("");
    setImpact(null);
    setValues({
      budgetAmount: p?.budgetAmount?.toString() ?? "",
      salesPartId: p?.salesPartId ?? "",
      effectiveMonth: api.projectToday().slice(0, 7),
      yearMonth: api.projectToday().slice(0, 7),
      reasonCode: "",
      itemCode: "",
      amount: "",
      memo: "",
      reason: "",
    });
    if (next === "lose")
      try {
        setImpact(await api.getCommonProjectLoseImpact(projectId));
      } catch (e) {
        setError(api.projectFailure(e));
      }
  };
  const execute = async (action: () => Promise<unknown>) => {
    if (busy) return;
    setBusy(true);
    setError("");
    setFields({});
    try {
      await action();
      setMode("");
      setRemove(null);
      setSuccess("저장했습니다.");
      await load();
      onChanged?.();
    } catch (e) {
      setError(api.projectFailure(e));
      if (e instanceof api.ProjectRequestError)
        setFields(
          Object.fromEntries(e.errors.map((f) => [f.field, f.message])),
        );
    } finally {
      setBusy(false);
    }
  };
  const save = () =>
    execute(() => {
      switch (mode) {
        case "budget":
          return api.putCommonProjectBudget(
            projectId,
            Number(values.budgetAmount),
          );
        case "part":
          return api.putCommonProjectSalesPart(projectId, {
            salesPartId: values.salesPartId,
            effectiveMonth: values.effectiveMonth,
            ...(values.reason ? { reason: values.reason } : {}),
          });
        case "expense":
          return api.postCommonProjectDirectExpense(projectId, {
            yearMonth: values.yearMonth,
            itemCode: values.itemCode,
            amount: Number(values.amount),
            ...(values.memo ? { memo: values.memo } : {}),
          });
        case "lose":
          return api.postCommonProjectLose(
            projectId,
            values.reasonCode,
            values.memo,
          );
        case "start":
          return api.postCommonProjectStart(projectId);
        default:
          return Promise.resolve();
      }
    });
  const close = () => (planDirty ? setClosing(true) : onClose());
  const control = (key: string, label: string, type = "text") => (
    <label>
      {label}
      <input
        required={key !== "reason" && key !== "memo"}
        data-testid={`project-command-${key.toLowerCase()}`}
        type={type}
        min={type === "number" ? 0 : undefined}
        step={type === "number" ? 1 : undefined}
        maxLength={500}
        value={values[key] ?? ""}
        disabled={busy}
        onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
      />
      {fields[key] && <small role="alert">{fields[key]}</small>}
    </label>
  );
  const select = (key: string, label: string) => (
    <label>
      {label}
      <select
        required
        data-testid={`project-command-${key.toLowerCase()}`}
        value={values[key] ?? ""}
        disabled={busy}
        onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
      >
        <option value="">선택</option>
        {options[key]?.map((c) => (
          <option key={c.code} value={c.code}>
            {c.codeName}
          </option>
        ))}
      </select>
      {fields[key] && <small role="alert">{fields[key]}</small>}
    </label>
  );
  if (edit && p && writable)
    return (
      <ProjectForm
        project={p}
        onClose={() => setEdit(false)}
        onSaved={() => {
          setEdit(false);
          setSuccess("저장했습니다.");
          void load();
          onChanged?.();
        }}
      />
    );
  return (
    <Dialog
      title={p?.projectName ?? "프로젝트 상세"}
      testId="project-detail-dialog"
      onClose={close}
      closable
    >
      <section className="project-detail" data-testid="project-detail">
        {error && (
          <p role="alert" data-testid="project-detail-error">
            {error}
            <button
              data-testid="project-detail-retry"
              onClick={() => void load()}
            >
              다시 조회
            </button>
          </p>
        )}
        {success && <p role="status">{success}</p>}
        {!p && !error && <p role="status">프로젝트를 불러오는 중…</p>}
        {p && (
          <>
            {readOnly && (
              <p className="project-banner">
                지난 해 화면은 읽기 전용입니다. 연속 프로젝트는 올해에서 수정해
                주세요.
              </p>
            )}
            {p.statusCode === "LOSE" && (
              <p className="project-banner">
                실주 처리일 {p.loseDate} · {p.loseReasonName} · {p.loseMemo}
              </p>
            )}
            {p.statusCode === "PRE" && p.preContractPeopleCount > 0 && (
              <p className="project-banner">
                계약 전에 {p.preContractPeopleCount}명이 먼저 투입
              </p>
            )}
            <div className="project-actions">
              {writable && p.canEdit && (
                <button
                  data-testid="project-edit"
                  onClick={() => (onEdit ? onEdit(p) : setEdit(true))}
                >
                  수정
                </button>
              )}
              {writable &&
                p.statusCode === "PRE" &&
                (p.canStart || p.canLose) && (
                  <details data-testid="project-more">
                    <summary>⋯</summary>
                    {p.canStart && (
                      <button
                        data-testid="project-start"
                        onClick={() => void startMode("start")}
                      >
                        진행으로
                      </button>
                    )}
                    {p.canLose && (
                      <button
                        data-testid="project-lose"
                        onClick={() => void startMode("lose")}
                      >
                        실주 처리
                      </button>
                    )}
                  </details>
                )}
              {p.contractTypeCode === "TK" && onDistribution && (
                <button
                  data-testid="project-distribution-open"
                  onClick={() => onDistribution(p.projectId)}
                >
                  배분표
                </button>
              )}
            </div>
            <nav role="tablist" aria-label="프로젝트 상세">
              {tabs.map((t) => (
                <button
                  key={t}
                  role="tab"
                  aria-selected={tab === t}
                  data-testid={`project-tab-${tabs.indexOf(t)}`}
                  onClick={() => setTab(t)}
                >
                  {t}
                </button>
              ))}
            </nav>
            <section role="tabpanel" data-testid="project-detail-tab-panel">
              {tab === "기본정보" && (
                <>
                  <dl className="project-facts">
                    {Object.entries({
                      프로젝트ID: p.projectId,
                      고객사: p.customerName,
                      계약유형: p.contractTypeName,
                      기간: `${p.startDate} ~ ${p.endDate}`,
                      근무지: p.workplace,
                      상태: p.statusName,
                      신호등: p.trafficLight ?? "산출 자료 없음",
                      계약금액:
                        p.contractTypeCode === "IN"
                          ? "–"
                          : api.won(p.contractAmount),
                      "견적 금액": api.won(p.estimateAmount),
                      "예산(목표원가)": api.won(p.budgetAmount),
                      "팀비(1인·월)": api.won(p.teamExpenseAmount),
                      "프로젝트경비(1인·월)": api.won(p.projectExpenseAmount),
                      "영업 파트": p.salesPartName ?? "미지정",
                      PM: p.pmName,
                      특징: p.feature,
                    }).map(([key, value]) => (
                      <div key={key}>
                        <dt>{key}</dt>
                        <dd>{value ?? "–"}</dd>
                      </div>
                    ))}
                  </dl>
                  {writable && p.canChangeBudget && (
                    <button
                      data-testid="project-budget-change"
                      onClick={() => void startMode("budget")}
                    >
                      예산 변경
                    </button>
                  )}
                  {writable && p.canChangeSalesPart && (
                    <button
                      data-testid="project-part-change"
                      onClick={() => void startMode("part")}
                    >
                      파트 변경
                    </button>
                  )}
                  <h3>영업 파트 이력</h3>
                  {parts.map((s, i) => (
                    <p
                      data-testid={`project-part-history-${i}`}
                      key={s.effectiveMonth}
                    >
                      {s.effectiveMonth} · {s.salesPartName}{" "}
                      {s.scheduled ? "예정" : ""}{" "}
                      <span
                        title={`${s.reason ?? "사유 없음"} · ${s.changedBy}`}
                      >
                        ⓘ
                      </span>
                    </p>
                  ))}
                  {p.contractTypeCode !== "IN" && (
                    <section data-testid="project-expenses">
                      <h3>추가 직접 경비</h3>
                      {writable && p.canEdit && (
                        <button
                          data-testid="project-expense-add"
                          onClick={() => void startMode("expense")}
                        >
                          경비추가
                        </button>
                      )}
                      <div className="project-expense-scroll">
                        <table>
                          <thead>
                            <tr>
                              <th>월</th>
                              <th>항목</th>
                              <th>금액</th>
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
                                <td>{api.won(e.amount)}</td>
                                <td>{e.memo}</td>
                                <td>
                                  {writable && p.canEdit && (
                                    <button
                                      data-testid={`project-expense-delete-${e.expenseId.replace(":", "-")}`}
                                      onClick={() => setRemove(e)}
                                    >
                                      삭제
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                        {!expenses.length && (
                          <p>등록된 추가 경비가 없습니다.</p>
                        )}
                      </div>
                    </section>
                  )}
                </>
              )}
              {tab === "진행현황" && (
                <>
                  <p>
                    계획 {p.progress?.plannedProgressRate ?? "–"}% / 실제{" "}
                    {p.progress?.actualProgressRate ?? "–"}%
                  </p>
                  {p.progress?.steps.map((s, i) => (
                    <p key={i} data-testid={`project-progress-${i}`}>
                      {s.stepName} · 가중치 {s.weightRate}% ·{" "}
                      {s.plannedStartDate} ~ {s.plannedEndDate} · 완료{" "}
                      {s.completedDate ?? "–"}
                    </p>
                  ))}
                  {!p.progress?.steps.length && <p>등록된 단계가 없습니다.</p>}
                </>
              )}
              {tab === "이슈" && (
                <>
                  {p.issues?.map((i, index) => (
                    <article key={index} data-testid={`project-issue-${index}`}>
                      <h3>{i.issueTitle}</h3>
                      <p>
                        {i.importanceName} · {i.dueDate} · {i.statusName}
                      </p>
                      <p>{i.action}</p>
                    </article>
                  ))}
                  {!p.issues?.length && <p>등록된 이슈가 없습니다.</p>}
                </>
              )}
              {tab === "투입인력" && (
                <>
                  {p.assignments?.map((a, i) => (
                    <p key={i} data-testid={`project-assignment-${i}`}>
                      {a.name} · {a.jobName} · {a.startDate} ~ {a.endDate} ·{" "}
                      {a.mm} MM
                    </p>
                  ))}
                  {!p.assignments?.length && <p>투입인력이 없습니다.</p>}
                </>
              )}
              {tab === "계약변경이력" && (
                <table>
                  <thead>
                    <tr>
                      <th>변경일</th>
                      <th>항목</th>
                      <th>이전</th>
                      <th>이후</th>
                      <th>처리자</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((h, i) => (
                      <tr key={i} data-testid={`project-history-${i}`}>
                        <td>{h.changedAt}</td>
                        <td>{h.itemName}</td>
                        <td>{h.beforeValue ?? "–"}</td>
                        <td>{h.afterValue ?? "–"}</td>
                        <td>{h.changedBy}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {tab === "인원계획" && (
                <>
                  <p>
                    필요인력: 계획만 있는 직무도 추가할 수 있습니다. 차이는
                    실제−계획입니다.
                  </p>
                  {(!p.canSaveManpowerPlan || !planAllowed) && (
                    <p>
                      해당 프로젝트 PM과 관리자만 계획을 수정할 수 있습니다.
                    </p>
                  )}
                  <table>
                    <thead>
                      <tr>
                        <th>직무</th>
                        <th>계획MM</th>
                        <th>실제MM</th>
                        <th>차이</th>
                      </tr>
                    </thead>
                    <tbody>
                      {jobs.map((j, i) => (
                        <tr
                          key={j.jobCode}
                          data-testid={`project-job-${j.jobCode.toLowerCase()}`}
                        >
                          <td>{j.jobName}</td>
                          <td>
                            <input
                              aria-label={`${j.jobName} 계획MM`}
                              data-testid={`project-job-mm-${j.jobCode.toLowerCase()}`}
                              type="number"
                              min={0}
                              max={9999.99}
                              step="0.01"
                              value={j.plannedMm ?? ""}
                              disabled={!canSavePlan || busy}
                              onChange={(e) => {
                                const v =
                                  e.target.value === ""
                                    ? null
                                    : Number(e.target.value);
                                setJobs((all) =>
                                  all.map((x, n) =>
                                    n === i
                                      ? {
                                          ...x,
                                          plannedMm: v,
                                          differenceMm:
                                            v == null ? null : x.actualMm - v,
                                        }
                                      : x,
                                  ),
                                );
                                setPlanDirty(true);
                              }}
                            />
                          </td>
                          <td>{j.actualMm}</td>
                          <td>
                            {j.differenceMm == null
                              ? "–"
                              : `${j.differenceMm} ${j.differenceMm === 0 ? "맞음" : j.differenceMm < 0 ? "부족" : "초과"}`}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {fields.jobs && <p role="alert">{fields.jobs}</p>}
                  {canSavePlan && (
                    <>
                      <select
                        aria-label="추가 직무"
                        data-testid="project-job-select"
                        value={newJob}
                        onChange={(e) => setNewJob(e.target.value)}
                      >
                        <option value="">직무 선택</option>
                        {options.jobCode
                          ?.filter(
                            (o) => !jobs.some((j) => j.jobCode === o.code),
                          )
                          .map((j) => (
                            <option key={j.code} value={j.code}>
                              {j.codeName}
                            </option>
                          ))}
                      </select>
                      <button
                        data-testid="project-job-add"
                        disabled={!newJob}
                        onClick={() => {
                          const j = options.jobCode.find(
                            (x) => x.code === newJob,
                          )!;
                          setJobs((all) => [
                            ...all,
                            {
                              jobCode: j.code,
                              jobName: j.codeName,
                              plannedMm: null,
                              actualMm: 0,
                              differenceMm: null,
                            },
                          ]);
                          setPlanDirty(true);
                          setNewJob("");
                        }}
                      >
                        직무 추가
                      </button>
                      <button
                        data-testid="project-plan-save"
                        disabled={busy}
                        onClick={() =>
                          void execute(() =>
                            api.putCommonProjectManpowerPlan(
                              projectId,
                              jobs.map((j) => ({
                                jobCode: j.jobCode,
                                plannedMm: j.plannedMm,
                              })),
                            ),
                          )
                        }
                      >
                        계획 저장
                      </button>
                    </>
                  )}
                </>
              )}
            </section>
          </>
        )}

        {mode && (
          <Dialog
            title={
              {
                budget: "예산 변경",
                part: "영업 파트 변경",
                expense: "경비추가",
                lose: "실주 처리",
                start: "진행으로 전환",
              }[mode]
            }
            testId="project-command-dialog"
            onClose={() => !busy && setMode("")}
            closable
          >
            <form
              className="project-form"
              data-testid="project-command-form"
              onSubmit={(e) => {
                e.preventDefault();
                void save();
              }}
            >
              {error && <p role="alert">{error}</p>}
              {mode === "budget" &&
                control("budgetAmount", "예산(목표원가)", "number")}
              {mode === "part" && (
                <>
                  {parts.slice(1).some((s) => s.scheduled) && (
                    <p>
                      미마감 예정 변경이 있습니다. 저장하면 예정 변경을 하나로
                      대체합니다. 원래 파트를 선택하면 취소합니다.
                    </p>
                  )}
                  <label>
                    새 파트
                    <select
                      required
                      data-testid="project-command-part"
                      value={values.salesPartId}
                      onChange={(e) =>
                        setValues((v) => ({
                          ...v,
                          salesPartId: e.target.value,
                        }))
                      }
                    >
                      <option value="">선택</option>
                      {partOptions.map((s) => (
                        <option key={s.partId} value={s.partId}>
                          {s.partName}
                        </option>
                      ))}
                    </select>
                  </label>
                  {control("effectiveMonth", "적용 시작월", "month")}
                  {control("reason", "사유(선택)")}
                </>
              )}
              {mode === "expense" && (
                <>
                  {control("yearMonth", "월", "month")}
                  {select("itemCode", "직접경비항목")}
                  {control("amount", "금액", "number")}
                  {control("memo", "메모(선택)")}
                </>
              )}
              {mode === "lose" && (
                <>
                  {impact ? (
                    <p>
                      오늘 투입중 {impact.activePeopleCount}명 조기철수 · 시작전{" "}
                      {impact.futurePeopleCount}명 배정취소
                    </p>
                  ) : (
                    <p>영향을 확인하는 중…</p>
                  )}
                  {select("reasonCode", "실주 사유")}
                  {control("memo", "메모(선택)")}
                </>
              )}
              {mode === "start" && (
                <p>수주예정 프로젝트를 진행으로 변경할까요?</p>
              )}
              <footer>
                <button
                  data-testid="project-command-cancel"
                  type="button"
                  disabled={busy}
                  onClick={() => setMode("")}
                >
                  취소
                </button>
                <button
                  data-testid="project-command-save"
                  disabled={busy || (mode === "lose" && !impact)}
                >
                  확인 저장
                </button>
              </footer>
            </form>
          </Dialog>
        )}
        {remove && (
          <ConfirmDialog
            title="추가 경비를 삭제할까요?"
            description={`${remove.yearMonth} ${remove.itemName} ${api.won(remove.amount)}원 경비를 삭제합니다. 마감된 달은 삭제할 수 없습니다.`}
            destructive
            proceedLabel="삭제"
            onCancel={() => setRemove(null)}
            onProceed={() =>
              void execute(() =>
                api.postCommonProjectDirectExpenseDelete(
                  projectId,
                  remove.expenseId,
                ),
              )
            }
          />
        )}
        {closing && (
          <ConfirmDialog
            title="변경 내용 버리기"
            description="저장하지 않은 인원 계획을 버릴까요?"
            proceedLabel="버리기"
            onCancel={() => setClosing(false)}
            onProceed={onClose}
          />
        )}
      </section>
    </Dialog>
  );
}
