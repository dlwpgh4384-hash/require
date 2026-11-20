import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Badge, Banner, DetailPanel, KeyValues } from "../../shared/list";
import { Dialog, type ToastKind } from "../../shared/feedback/Dialog";
import { focusFirstError } from "../../shared/feedback/messages";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { formatDateTime, koreanToday } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { getCommonDeveloper } from "../developers/developers";
import { ProjectLink } from "../projects/ProjectLink";
import { ProjectRequestError, getCommonProject, projectFailure, won, type Project } from "../projects/projects";
import {
  CHANGE_LABEL,
  CHANGE_TYPES,
  EXT_KIND,
  EXT_LABEL,
  HISTORY_CHIPS,
  KIND_CODES,
  KIND_LABEL,
  addDays,
  assignmentPriceEditable,
  getCommonAssignment,
  getCommonAssignmentHistory,
  mmf,
  postCommonAssignmentChange,
  postCommonAssignmentHistoryCorrection,
  type Assignment,
  type AssignmentChange,
  type AssignmentHistory,
} from "./assignments";
import "../projects/projects.css";
import "./assignments.css";

type Option = { code: string; codeName: string };
type Change = { type: string; date: string; to: string; reason: string; memo: string };
const digits = (value: string) => value.replace(/[^0-9]/g, "");
const MONTH_TYPES = ["UPRC", "RATE"];
/** 무상지원·대체투입은 투입 구분 변경으로 바꾸지 않는다(2026-10-26 결정). */
const KIND_LOCK = ["FREE", "SUB"];
const DATE_LABEL: Record<string, string> = {
  EXT: "연장 시작일",
  EARLY: "철수일",
  UPRC: "적용 시작월",
  RATE: "적용 시작월",
  EXTP: "바꾼 날",
  KIND: "바꾼 날",
};
const plusIcon = (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 5v14M5 12h14" />
  </svg>
);
const warnIcon = (
  <svg className="assignment-warn-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3l9 16H3zM12 10v4M12 17h.01" />
  </svg>
);
const typeKind = (code: string) => (code === "EARLY" ? "bad" : code === "EXT" ? "ok" : code === "CRCT" ? "lock" : "neutral");
const typeName = (h: AssignmentHistory) => CHANGE_LABEL[h.changeTypeCode] ?? h.changeTypeName;
const extBadge = (code?: string | null) =>
  code && EXT_LABEL[code] ? <Badge kind={EXT_KIND[code]}>{EXT_LABEL[code]}</Badge> : <span className="project-sub">–</span>;

/** 시안 투입 상세(오른쪽 창): 제목 「이름 · 프로젝트명」, 탭 [투입 정보][변경 이력 (n)], [변경 기록]·[정정] 작은 창. */
export function AssignmentDetail({
  id,
  initialTab,
  readOnly,
  roles,
  onClose,
  onChanged,
}: {
  id: string;
  initialTab?: string;
  readOnly: boolean;
  roles: readonly string[];
  onClose: () => void;
  /** 저장 뒤 목록을 다시 읽고 안내(message)를 띄운다. */
  onChanged: (message?: string, kind?: ToastKind) => void;
}) {
  const navigate = useNavigate();
  const canRate = assignmentPriceEditable(roles);
  const [row, setRow] = useState<Assignment | null>(null);
  const [project, setProject] = useState<Project | null>(null);
  const [retirementDate, setRetirementDate] = useState<string | null>(null);
  const [history, setHistory] = useState<AssignmentHistory[]>([]);
  const [chip, setChip] = useState("");
  const [tab, setTab] = useState(initialTab ?? "info");
  const [reasons, setReasons] = useState<Option[]>([]);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [person, setPerson] = useState(false);
  const [change, setChange] = useState<Change | null>(null);
  const [fix, setFix] = useState<{ history: AssignmentHistory; to: string; reason: string } | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [modalError, setModalError] = useState("");
  const [busy, setBusy] = useState(false);
  const [modalTouched, setModalTouched] = useState(false);
  const setDirty = useScreenDirty();
  useEffect(() => {
    setDirty("UI-12", modalTouched);
    return () => setDirty("UI-12", false);
  }, [modalTouched, setDirty]);
  useEffect(() => {
    let active = true;
    setError("");
    Promise.all([getCommonAssignment(id), getCommonAssignmentHistory(id), getCommonCodes("ASGN_CHG_RSN")])
      .then(async ([r, h, re]) => {
        const p = await getCommonProject(r.projectId);
        if (!active) return;
        setRow(r);
        setProject(p);
        setHistory(h.items ?? []);
        setReasons(re.items ?? []);
        getCommonDeveloper(r.developerId)
          .then((d) => active && setRetirementDate(d?.retirementDate ?? null))
          .catch(() => undefined);
      })
      .catch((e) => active && setError(projectFailure(e)));
    return () => {
      active = false;
    };
  }, [id, revision]);
  const today = koreanToday();
  const closedMonth = project?.closedMonth ?? null;
  const priceTypes = !!project && !["TK", "IN"].includes(project.contractTypeCode) && row?.inputTypeCode !== "FREE" && canRate;
  const types = CHANGE_TYPES.filter((t) => t !== "UPRC" || priceTypes);
  /** 시안 hmDefault: 종류를 바꾸면 사유·메모까지 모두 처음 값으로. 월 MM 변경은 다음 달 1일(투입일이 더 늦으면 투입일). */
  const defaults = (type: string): Change => {
    const nextMonth = addDays(`${today.slice(0, 7)}-01`, 31).slice(0, 7) + "-01";
    return {
      type,
      date:
        type === "EXT" && row
          ? addDays(row.endDate, 1)
          : type === "RATE"
            ? row && nextMonth < row.startDate
              ? row.startDate
              : nextMonth
            : today,
      to: type === "EXTP" ? row?.extensionPossibilityCode ?? "" : type === "KIND" ? row?.inputTypeCode ?? "" : "",
      reason: "",
      memo: "",
    };
  };
  const modalBody = useRef<HTMLFormElement>(null);
  const showErrors = (e: Record<string, string>) => {
    setErrors(e);
    setModalError("");
    if (!Object.keys(e).length) return false;
    // 공통 기능: 저장할 때 첫 오류 칸으로 커서를 옮긴다.
    window.setTimeout(() => focusFirstError(modalBody.current), 0);
    return true;
  };
  const closeModal = () => {
    setChange(null);
    setFix(null);
    setErrors({});
    setModalError("");
    setModalTouched(false);
  };
  const done = (message: string) => {
    closeModal();
    setTab("history");
    setRevision((r) => r + 1);
    onChanged(message);
  };
  const fail = (e: unknown) => {
    setModalError(projectFailure(e));
    if (e instanceof ProjectRequestError) setErrors(Object.fromEntries(e.errors.map((f) => [f.field, f.message])));
  };
  const monthType = (t: string) => MONTH_TYPES.includes(t);
  /** 시안 U(a,m)·rateAt(a,m): 고른 달(YYYY-MM)에 적용되는 청구 단가·계약 월 MM. 그 달 값이 없으면 지금 값. */
  const monthAt = (ym: string) => row?.months?.find((m) => m.month === ym.replace("-", ""));
  const priceAt = (ym: string) => monthAt(ym)?.billingPrice ?? row?.billingPrice;
  const mmAt = (ym: string) => monthAt(ym)?.monthlyMm ?? row?.monthlyMm;
  const retro = (c: Change) => (monthType(c.type) ? c.date.slice(0, 7) < today.slice(0, 7) : c.date < today);
  const crosses = (c: Change) => !!closedMonth && !!c.date && c.date.slice(0, 7) <= closedMonth;
  const needReason = (c: Change) => c.type === "EARLY" || retro(c) || crosses(c);
  const saveChange = async () => {
    if (!row || !change || busy) return;
    const c = { ...change, date: monthType(change.type) && change.date ? change.date.slice(0, 7) + "-01" : change.date };
    const e: Record<string, string> = {};
    const ym = c.date.slice(0, 7);
    if (!c.date) e.date = "날짜를 넣어 주세요.";
    else if (monthType(c.type) ? ym < row.startDate.slice(0, 7) : c.date < row.startDate)
      e.date = monthType(c.type)
        ? `투입 시작월(${row.startDate.slice(0, 7)})보다 앞선 달은 고를 수 없습니다.`
        : `투입일(${row.startDate})보다 앞선 날짜는 넣을 수 없습니다.`;
    if (c.type === "EXT") {
      if (!c.to) e.to = "새 종료일을 넣어 주세요.";
      else if (c.to <= row.endDate) e.to = `새 종료일은 현재 종료일(${row.endDate})보다 늦어야 합니다.`;
      else if (retirementDate && c.to > retirementDate) e.to = `새 종료일이 ${row.name}님의 퇴사일(${retirementDate})보다 늦습니다.`;
      else if (project?.endDate && c.to > project.endDate)
        e.to = `새 종료일이 프로젝트 계약 종료일(${project.endDate})보다 늦습니다. 「프로젝트·투입 › 프로젝트」에서 계약 기간을 먼저 연장해 주세요.`;
    }
    if (c.type === "EARLY" && c.date && c.date > row.endDate) e.date = `철수일은 종료일(${row.endDate}) 이전이어야 합니다.`;
    if (c.type === "UPRC") {
      if (!canRate) e.type = "단가 변경은 파트장·재무만 할 수 있습니다.";
      else if (!priceTypes) e.type = "턴키·내부·무상지원 배정은 청구 단가가 없어 단가 변경을 기록할 수 없습니다.";
      else {
        const n = Number(digits(c.to));
        if (!(n > 0)) e.to = "단가를 숫자로 넣어 주세요.";
        else if (n === priceAt(ym)) e.to = `${ym} 단가와 같습니다.`;
        if (c.date && ym > row.endDate.slice(0, 7)) e.date = `철수일(${row.endDate}) 뒤로는 넣을 수 없습니다.`;
      }
    }
    if (c.type === "RATE") {
      const n = Number(c.to);
      if (!(n > 0 && n <= 1)) e.to = "0보다 크고 1.00 이하로 넣어 주세요.";
      else if (Math.round(n * 100) / 100 === mmAt(ym)) e.to = "현재 월 MM과 같습니다.";
      if (c.date && ym > row.endDate.slice(0, 7)) e.date = `철수일(${row.endDate}) 뒤로는 넣을 수 없습니다.`;
    }
    if (c.type === "EXTP" && c.to === (row.extensionPossibilityCode ?? "")) e.to = "현재 값과 같습니다.";
    if (c.type === "KIND") {
      if (c.to === row.inputTypeCode) e.to = "현재 투입 구분과 같습니다.";
      else if (KIND_LOCK.includes(c.to) || KIND_LOCK.includes(row.inputTypeCode ?? ""))
        e.to = "무상지원·대체투입으로 바꾸거나 무상지원·대체투입에서 바꿀 수 없습니다. 조기 철수(또는 배정 취소) 후 새로 배정하세요.";
    }
    if (needReason(c) && !c.reason) e.reason = "사유를 골라 주세요.";
    if (showErrors(e)) return;
    const body: AssignmentChange = { modifiedAt: row.modifiedAt, changeTypeCode: c.type, effectiveDate: c.date };
    if (c.type === "EXT") body.newEndDate = c.to;
    if (c.type === "EARLY") body.newEndDate = c.date;
    if (c.type === "UPRC") body.newBillingPrice = Number(digits(c.to));
    if (c.type === "RATE") body.newMonthlyMm = Number(c.to);
    if (c.type === "EXTP") body.newExtensionPossibilityCode = c.to;
    if (c.type === "KIND") body.newInputTypeCode = c.to;
    if (c.reason) body.reasonCode = c.reason;
    if (c.memo) body.memo = c.memo;
    setBusy(true);
    try {
      await postCommonAssignmentChange(id, body);
      done(`변경 이력에 '${CHANGE_LABEL[c.type]}' 기록을 남겼습니다.`);
    } catch (reason) {
      fail(reason);
    } finally {
      setBusy(false);
    }
  };
  const saveFix = async () => {
    if (!fix || busy) return;
    const e: Record<string, string> = {};
    if (!fix.to.trim()) e.to = "바른 내용을 적어 주세요.";
    if (!fix.reason) e.reason = "사유를 골라 주세요.";
    if (showErrors(e)) return;
    setBusy(true);
    try {
      const reasonName = reasons.find((r) => r.code === fix.reason)?.codeName ?? fix.reason;
      await postCommonAssignmentHistoryCorrection(id, fix.history, fix.to.trim(), reasonName);
      done("정정 기록을 남겼습니다.");
    } catch (reason) {
      fail(reason);
    } finally {
      setBusy(false);
    }
  };
  const saveRef = useRef<() => void>(() => undefined);
  saveRef.current = () => (change ? void saveChange() : fix ? void saveFix() : undefined);
  useEffect(() => {
    // 공통 기능: Ctrl+S로 작은 창 저장
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        saveRef.current();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
  if (person && row)
    return (
      <DeveloperDetail
        developerId={row.developerId}
        onClose={onClose}
        onEdit={() => navigate("/screens/UI-02")}
        onStop={() => navigate("/screens/UI-02")}
        onNotice={(message) => onChanged(message, "warn")}
        onChanged={(message) => onChanged(message)}
      />
    );
  const fieldError = (key: string) =>
    errors[key] && (
      <small role="alert" className="field-error assignment-err">
        {errors[key]}
      </small>
    );
  const invalid = (key: string) => (errors[key] ? { "aria-invalid": true as const } : {});
  // 시안 작은 창 칸 줄(.fld): 이름표 왼쪽 120px 오른쪽 맞춤, 칸은 오른쪽(휴대폰은 위아래)
  const fc = (htmlFor: string, key: string, label: string, required: boolean, control: ReactNode, help?: ReactNode) => (
    <div className="assignment-fld" data-testid={`assignment-change-field-${key}`}>
      <label className="assignment-fl" htmlFor={htmlFor}>
        {label}
        {required && <span className="project-bad"> *</span>}
      </label>
      <div className="project-fc assignment-fv">
        {control}
        {help && <span className="project-help">{help}</span>}
        {fieldError(key)}
      </div>
    </div>
  );
  const setC = (patch: Partial<Change>) => {
    setModalTouched(true);
    setChange((c) => c && { ...c, ...patch });
  };
  const sortedHistory = [...history].sort(
    (x, y) => y.effectiveDate.localeCompare(x.effectiveDate) || y.historyId.localeCompare(x.historyId, undefined, { numeric: true }),
  );
  const shownHistory = sortedHistory.filter((h) => !chip || h.changeTypeCode === chip);
  const info = row && (
    <KeyValues
      items={[
        ["프로젝트", <ProjectLink key="p" projectId={row.projectId} name={row.projectName} testId="assignment-detail-project" />],
        [
          "개발자",
          <span key="d">
            <button type="button" className="project-lnk" data-testid="assignment-detail-person" onClick={() => setPerson(true)}>
              {row.name}
            </button>{" "}
            <span className="project-cap">{row.managementNumber}</span>
          </span>,
        ],
        ["직무", row.jobName],
        ["투입일", row.startDate],
        ["철수일", row.endDate],
        ...(canRate
          ? [
              [
                "단가",
                <span key="r" className="assignment-tnum">
                  {project?.contractTypeCode === "TK"
                    ? "배분표"
                    : row.inputTypeCode === "FREE"
                      ? "없음 (무상지원)"
                      : `${won(row.billingPrice)} 원 / 월`}
                </span>,
              ] as [string, ReactNode],
            ]
          : []),
        ["월 MM", <span key="m" className="assignment-tnum">{mmf(row.monthlyMm)}</span>],
        ["투입 구분", <Badge key="k">{row.inputTypeName}</Badge>],
        ["연장 가능성", extBadge(row.extensionPossibilityCode)],
        ["연장 횟수", `${row.extensionCount}회`],
      ]}
    />
  );
  const historyTab = (
    <>
      <div className="assignment-htool">
        <div className="assignment-chips" role="group" aria-label="변경 종류">
          {["", ...HISTORY_CHIPS].map((code) => (
            <button
              key={code || "all"}
              type="button"
              className={`assignment-chip${chip === code ? " on" : ""}`}
              data-testid={code ? `assignment-history-filter-${code.toLowerCase()}` : "assignment-history-all"}
              aria-pressed={chip === code}
              onClick={() => setChip(code)}
            >
              {code ? CHANGE_LABEL[code] : "전체"}
            </button>
          ))}
        </div>
        {!readOnly && row && (
          <button
            type="button"
            className="ls-btn ls-btn-sm ls-btn-p"
            data-testid="assignment-change-open"
            onClick={() => {
              setErrors({});
              setModalError("");
              setModalTouched(false);
              setChange(defaults("EXT"));
            }}
          >
            {plusIcon} 변경 기록
          </button>
        )}
      </div>
      <p className="project-cap">지나간 기록은 고치거나 지울 수 없습니다. 틀렸으면 [정정]으로 바로잡은 내용을 남깁니다.</p>
      {shownHistory.length ? (
        <div className="ls-twrap">
          <table className="ls-t">
            <thead>
              <tr>
                <th>적용일 · 입력</th>
                <th>변경 종류</th>
                <th>변경 전 → 후</th>
                <th>사유</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {shownHistory.map((h) => (
                <tr key={h.historyId} className={h.correctedYn === "Y" ? "assignment-muted" : undefined} data-testid={`assignment-history-${h.historyId}`}>
                  <td className="assignment-tnum">
                    {h.effectiveDate}
                    <br />
                    <span className="project-cap">
                      {h.changedBy} · {formatDateTime(h.changedAt).slice(5)}
                    </span>
                  </td>
                  <td>
                    <Badge kind={typeKind(h.changeTypeCode)}>{typeName(h)}</Badge>
                    {h.retroactiveYn === "Y" && (
                      <>
                        {" "}
                        <Badge kind="warn">소급 입력</Badge>
                      </>
                    )}
                    {h.correctedYn === "Y" && (
                      <>
                        {" "}
                        <Badge kind="lock">정정됨</Badge>
                      </>
                    )}
                  </td>
                  <td>
                    {h.beforeValue ? `${h.beforeValue} → ` : ""}
                    {h.afterValue}
                  </td>
                  <td>{[h.reason, h.memo].filter(Boolean).join(" · ") || <span className="project-sub">–</span>}</td>
                  <td>
                    {!readOnly && h.correctedYn !== "Y" && !["INPUT", "CRCT"].includes(h.changeTypeCode) && (
                      <button
                        type="button"
                        className="project-lnk"
                        data-testid={`assignment-history-correct-${h.historyId}`}
                        onClick={() => {
                          setErrors({});
                          setModalError("");
                          setModalTouched(false);
                          setFix({ history: h, to: "", reason: "" });
                        }}
                      >
                        정정
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="project-sub" data-testid="assignment-history-empty">
          이 종류의 기록이 없습니다.
        </p>
      )}
    </>
  );
  const modalBand = change && (
    <>
      {(retro(change) || crosses(change)) && (
        <Banner kind="warn" icon={warnIcon}>
          <span data-testid="assignment-change-warning">
            {[
              retro(change) ? "지난 날짜로 기록합니다. '소급 입력'으로 표시되고 사유가 필요합니다." : "",
              crosses(change)
                ? `마감한 달(${change.date.slice(0, 7)})에 걸칩니다. 마감한 달의 금액은 그대로 두고 마감하지 않은 달부터 반영합니다.`
                : "",
            ]
              .filter(Boolean)
              .join(" ")}
          </span>
        </Banner>
      )}
    </>
  );
  return (
    <>
      <DetailPanel
        title={row ? `${row.name} · ${row.projectName}` : "투입 상세"}
        sub={row ? `${row.assignmentId} · ${row.jobName} · ${row.startDate} ~ ${row.endDate}` : undefined}
        testId="assignment-detail"
        onClose={onClose}
        activeTab={tab}
        onTab={setTab}
        tabs={
          row
            ? [
                { key: "info", label: "투입 정보", content: info },
                { key: "history", label: `변경 이력 (${history.length})`, content: historyTab },
              ]
            : undefined
        }
      >
        {error ? (
          <div role="alert">
            <p>{error}</p>
            <button type="button" className="ls-btn" data-testid="assignment-detail-retry" onClick={() => setRevision((r) => r + 1)}>
              다시 시도
            </button>
          </div>
        ) : (
          <p className="project-sub">불러오는 중…</p>
        )}
      </DetailPanel>
      {!readOnly && change && row && (
        <Dialog title="변경 기록" testId="assignment-change-dialog" closable onClose={() => !busy && closeModal()}>
          <form
            ref={modalBody}
            className="project-modal"
            noValidate
            onSubmit={(e) => {
              e.preventDefault();
              void saveChange();
            }}
          >
            {modalBand}
            {modalError && (
              <p role="alert" className="field-error">
                {modalError}
              </p>
            )}
            {fc(
              "assignment-change-type",
              "type",
              "변경 종류",
              true,
              <select
                id="assignment-change-type"
                data-testid="assignment-change-type"
                value={change.type}
                {...invalid("type")}
                onChange={(e) => {
                  setModalTouched(true);
                  setErrors({});
                  setChange(defaults(e.target.value));
                }}
              >
                {types.map((t) => (
                  <option key={t} value={t}>
                    {CHANGE_LABEL[t]}
                  </option>
                ))}
              </select>,
            )}
            {fc(
              "assignment-change-date",
              "date",
              DATE_LABEL[change.type],
              true,
              monthType(change.type) ? (
                <input
                  id="assignment-change-date"
                  data-testid="assignment-change-date"
                  type="month"
                  value={change.date.slice(0, 7)}
                  {...invalid("date")}
                  onChange={(e) => setC({ date: e.target.value ? `${e.target.value}-01` : "" })}
                />
              ) : (
                <input
                  id="assignment-change-date"
                  data-testid="assignment-change-date"
                  type="date"
                  value={change.date}
                  {...invalid("date")}
                  onChange={(e) => setC({ date: e.target.value })}
                />
              ),
            )}
            {change.type === "EXT" &&
              fc(
                "assignment-change-end",
                "to",
                "새 종료일",
                true,
                <input
                  id="assignment-change-end"
                  data-testid="assignment-change-end"
                  type="date"
                  value={change.to}
                  onChange={(e) => setC({ to: e.target.value })}
                  {...invalid("to")}
                />,
              )}
            {change.type === "UPRC" &&
              fc(
                "assignment-change-price",
                "to",
                "새 월 단가(원)",
                true,
                <input
                  id="assignment-change-price"
                  data-testid="assignment-change-price"
                  className="project-money"
                  inputMode="numeric"
                  value={change.to}
                  onChange={(e) => {
                    // 공통 기능: 금액 칸은 숫자만 받고 천 단위 쉼표로 보인다.
                    const n = digits(e.target.value);
                    setC({ to: n ? won(Number(n)) : "" });
                  }}
                  {...invalid("to")}
                />,
                `${(change.date || today).slice(0, 7)} 지금 월 ${won(priceAt((change.date || today).slice(0, 7)))}원. 적용 시작월부터 청구·매출을 새 단가로 계산하고 앞선 달은 그대로 둡니다.`,
              )}
            {change.type === "RATE" &&
              fc(
                "assignment-change-mm",
                "to",
                "새 월 MM",
                true,
                <input
                  id="assignment-change-mm"
                  data-testid="assignment-change-mm"
                  type="number"
                  min={0.01}
                  max={1}
                  step={0.01}
                  inputMode="decimal"
                  value={change.to}
                  onChange={(e) => setC({ to: e.target.value.replace(/[^0-9.]/g, "") })}
                  {...invalid("to")}
                />,
                `지금 ${mmf(mmAt((change.date || today).slice(0, 7)))}. 적용 시작월부터 월 MM·원가·매출을 다시 계산하고 앞선 달은 그대로 둡니다.`,
              )}
            {change.type === "EXTP" &&
              fc(
                "assignment-change-extension",
                "to",
                "연장 가능성",
                true,
                <select
                  id="assignment-change-extension"
                  data-testid="assignment-change-extension"
                  value={change.to}
                  {...invalid("to")}
                  onChange={(e) => setC({ to: e.target.value })}
                >
                  {["", "H", "M", "L"].map((code) => (
                    <option key={code} value={code}>
                      {EXT_LABEL[code] ?? "미정"}
                    </option>
                  ))}
                </select>,
              )}
            {change.type === "KIND" &&
              fc(
                "assignment-change-input",
                "to",
                "투입 구분",
                true,
                <select
                  id="assignment-change-input"
                  data-testid="assignment-change-input"
                  value={change.to}
                  onChange={(e) => setC({ to: e.target.value })}
                  {...invalid("to")}
                >
                  {KIND_CODES.filter((k) => !KIND_LOCK.includes(k) || k === row.inputTypeCode).map((k) => (
                    <option key={k} value={k}>
                      {KIND_LABEL[k]}
                    </option>
                  ))}
                </select>,
                "투입 구분은 배정 전체에 적용됩니다. 일부 기간만 바꾸려면 조기 철수 후 새로 배정하세요. 종료일은 연장·조기 철수로 바꿉니다. 무상지원·대체투입으로 바꾸거나 무상지원·대체투입에서 바꾸려면 조기 철수(또는 배정 취소) 후 새로 배정하세요.",
              )}
            {fc(
              "assignment-change-reason",
              "reason",
              "사유",
              needReason({ ...change, date: change.date }),
              <select
                id="assignment-change-reason"
                data-testid="assignment-change-reason"
                value={change.reason}
                onChange={(e) => setC({ reason: e.target.value })}
                {...invalid("reason")}
              >
                <option value="">선택</option>
                {reasons.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.codeName}
                  </option>
                ))}
              </select>,
            )}
            {fc(
              "assignment-change-memo",
              "memo",
              "메모",
              false,
              <textarea id="assignment-change-memo" data-testid="assignment-change-memo" rows={2} maxLength={200} value={change.memo} onChange={(e) => setC({ memo: e.target.value })} />,
            )}
            <div className="app-actions">
              <button type="button" data-testid="assignment-change-cancel" disabled={busy} onClick={closeModal}>
                취소
              </button>
              <button type="submit" className="primary-button" data-testid="assignment-change-save" disabled={busy}>
                기록 저장
              </button>
            </div>
          </form>
        </Dialog>
      )}
      {!readOnly && fix && (
        <Dialog title="기록 정정" testId="assignment-correction-dialog" closable onClose={() => !busy && closeModal()}>
          <form
            ref={modalBody}
            className="project-modal"
            noValidate
            onSubmit={(e) => {
              e.preventDefault();
              void saveFix();
            }}
          >
            <p className="project-cap" data-testid="assignment-correction-target">
              {fix.history.effectiveDate} · {typeName(fix.history)} · {fix.history.beforeValue ? `${fix.history.beforeValue} → ` : ""}
              {fix.history.afterValue}
            </p>
            <p className="project-cap">원래 기록은 그대로 남고 '정정됨'으로 표시됩니다. 지금 값을 바꾸려면 [변경 기록]을 따로 넣으세요.</p>
            {modalError && (
              <p role="alert" className="field-error">
                {modalError}
              </p>
            )}
            {fc(
              "assignment-correction-value",
              "to",
              "바른 내용",
              true,
              <input
                id="assignment-correction-value"
                data-testid="assignment-correction-value"
                maxLength={200}
                value={fix.to}
                {...invalid("to")}
                onChange={(e) => {
                  setModalTouched(true);
                  setFix((f) => f && { ...f, to: e.target.value });
                }}
              />,
            )}
            {errors.correctedValue && fieldError("correctedValue")}
            {fc(
              "assignment-correction-reason",
              "reason",
              "사유",
              true,
              <select
                id="assignment-correction-reason"
                data-testid="assignment-correction-reason"
                value={fix.reason}
                {...invalid("reason")}
                onChange={(e) => {
                  setModalTouched(true);
                  setFix((f) => f && { ...f, reason: e.target.value });
                }}
              >
                <option value="">선택</option>
                {reasons.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.codeName}
                  </option>
                ))}
              </select>,
            )}
            <div className="app-actions">
              <button type="button" data-testid="assignment-correction-cancel" disabled={busy} onClick={closeModal}>
                취소
              </button>
              <button type="submit" className="primary-button" data-testid="assignment-correction-save" disabled={busy}>
                정정 저장
              </button>
            </div>
          </form>
        </Dialog>
      )}
    </>
  );
}
