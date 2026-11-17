import { useEffect, useState } from "react";
import { DetailPanel, KeyValues, Banner } from "../../shared/list/DetailPanel";
import { Dialog } from "../../shared/feedback/Dialog";
import { formatDateTime, koreanToday } from "../../shared/time";
import { ProjectLink } from "../projects/ProjectLink";
import {
  getCommonProject,
  ProjectRequestError,
  type Project,
} from "../projects/projects";
import { getCommonCodes } from "../codegroups/codeGroups";
import {
  assignmentPriceEditable,
  getCommonAssignment,
  getCommonAssignmentHistory,
  postCommonAssignmentChange,
  postCommonAssignmentHistoryCorrection,
  type Assignment,
  type AssignmentChange,
  type AssignmentHistory,
} from "./assignments";

type Option = { code: string; codeName: string };
export function AssignmentDetail({
  id,
  readOnly,
  roles,
  onClose,
  onChanged,
}: {
  id: string;
  readOnly: boolean;
  roles: readonly string[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [row, setRow] = useState<Assignment | null>(null),
    [project, setProject] = useState<Project | null>(null);
  const [history, setHistory] = useState<AssignmentHistory[]>([]),
    [filter, setFilter] = useState("");
  const [types, setTypes] = useState<Option[]>([]),
    [reasons, setReasons] = useState<Option[]>([]),
    [extensions, setExtensions] = useState<Option[]>([]),
    [inputs, setInputs] = useState<Option[]>([]);
  const [error, setError] = useState(""),
    [revision, setRevision] = useState(0);
  const [changing, setChanging] = useState(false),
    [correcting, setCorrecting] = useState<AssignmentHistory | null>(null);
  const [busy, setBusy] = useState(false),
    [fields, setFields] = useState<Record<string, string>>({});
  const [type, setType] = useState(""),
    [date, setDate] = useState(koreanToday()),
    [end, setEnd] = useState("");
  const [price, setPrice] = useState(""),
    [mm, setMm] = useState(""),
    [extension, setExtension] = useState("");
  const [input, setInput] = useState(""),
    [reason, setReason] = useState(""),
    [memo, setMemo] = useState("");
  const [value, setValue] = useState(""),
    [correctionReason, setCorrectionReason] = useState("");
  useEffect(() => {
    let active = true;
    setError("");
    Promise.all([
      getCommonAssignment(id),
      getCommonAssignmentHistory(id),
      getCommonCodes("ASGN_CHG_TYPE"),
      getCommonCodes("ASGN_CHG_RSN"),
      getCommonCodes("EXT_PSBL"),
      getCommonCodes("INPUT_TYPE"),
    ])
      .then(async ([r, h, t, re, ex, i]) => {
        const p = await getCommonProject(r.projectId);
        if (active) {
          setRow(r);
          setProject(p);
          setHistory(h.items);
          setTypes(t.items);
          setReasons(re.items);
          setExtensions(ex.items);
          setInputs(i.items);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [id, revision]);
  useEffect(() => {
    let active = true;
    getCommonAssignmentHistory(id, filter)
      .then((h) => {
        if (active) setHistory(h.items);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [id, filter, revision]);
  const fail = (e: unknown) => {
    setError((e as Error).message);
    if (e instanceof ProjectRequestError)
      setFields(Object.fromEntries(e.errors.map((f) => [f.field, f.message])));
  };
  const saved = () => {
    setChanging(false);
    setCorrecting(null);
    setRevision((r) => r + 1);
    onChanged();
  };
  const saveChange = async () => {
    if (!row) return;
    setBusy(true);
    setFields({});
    setError("");
    const b: AssignmentChange = {
      modifiedAt: row.modifiedAt,
      changeTypeCode: type,
      effectiveDate: ["UPRC", "RATE"].includes(type)
        ? date.slice(0, 7) + "-01"
        : date,
    };
    if (["EXT", "EARLY"].includes(type)) b.newEndDate = end;
    if (type === "UPRC") b.newBillingPrice = Number(price);
    if (type === "RATE") b.newMonthlyMm = Number(mm);
    if (type === "EXTP") b.newExtensionPossibilityCode = extension;
    if (type === "KIND") b.newInputTypeCode = input;
    if (reason) b.reasonCode = reason;
    if (memo) b.memo = memo;
    try {
      await postCommonAssignmentChange(id, b);
      saved();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  };
  const saveCorrection = async () => {
    if (!correcting) return;
    setBusy(true);
    setError("");
    setFields({});
    try {
      await postCommonAssignmentHistoryCorrection(
        id,
        correcting,
        value,
        correctionReason,
      );
      saved();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  };
  const nextDay = (day: string) => {
    const d = new Date(day + "T00:00:00Z");
    d.setUTCDate(d.getUTCDate() + 1);
    return d.toISOString().slice(0, 10);
  };
  const selectType = (code: string) => {
    setType(code);
    setDate(code === "EXT" && row ? nextDay(row.endDate) : koreanToday());
    setEnd(row?.endDate ?? "");
    setPrice(String(row?.billingPrice ?? ""));
    setMm(String(row?.monthlyMm ?? ""));
    setExtension(row?.extensionPossibilityCode ?? "");
    setInput(row?.inputTypeCode ?? "");
  };
  const fieldError = (key: string) =>
    fields[key] && <small role="alert">{fields[key]}</small>;
  const priceAllowed =
    assignmentPriceEditable(roles) &&
    ["MO", "DP"].includes(project?.contractTypeCode ?? "") &&
    row?.inputTypeCode !== "FREE";
  return (
    <>
      <DetailPanel
        title="투입 상세"
        testId="assignment-detail"
        onClose={onClose}
        actions={
          !readOnly &&
          row && (
            <button
              data-testid="assignment-change-open"
              onClick={() => {
                setChanging(true);
                setFields({});
                setType("");
                setReason("");
                setMemo("");
              }}
            >
              변경 기록
            </button>
          )
        }
      >
        {error && (
          <p role="alert">
            {error}
            <button
              data-testid="assignment-detail-retry"
              onClick={() => setRevision((r) => r + 1)}
            >
              다시 시도
            </button>
          </p>
        )}
        {!row && !error && <p>불러오는 중…</p>}
        {row && (
          <>
            <KeyValues
              items={[
                [
                  "프로젝트",
                  <ProjectLink
                    key="project"
                    projectId={row.projectId}
                    name={row.projectName}
                    testId="assignment-detail-project"
                  />,
                ],
                ["개발자", row.name],
                ["직무", row.jobName],
                ["투입일", row.startDate],
                ["철수일", row.endDate],
                ...(assignmentPriceEditable(roles)
                  ? [
                      [
                        "월 단가",
                        project?.contractTypeCode === "TK"
                          ? "배분표"
                          : (row.billingPrice?.toLocaleString("ko-KR") ?? "–"),
                      ] as [string, string],
                    ]
                  : []),
                ["월 MM", row.monthlyMm],
                ["투입 구분", row.inputTypeName],
                ["연장 가능성", row.extensionPossibilityName],
                ["연장 횟수", row.extensionCount],
              ]}
            />
            {row.warnings.map((w) => (
              <Banner key={w} kind="warn">
                {w}
              </Banner>
            ))}
            <h3>변경 이력 ({row.historyCount})</h3>
            <div className="assignment-history-filters">
              <button
                data-testid="assignment-history-all"
                onClick={() => setFilter("")}
              >
                전체
              </button>
              {types.map((t) => (
                <button
                  key={t.code}
                  data-testid={`assignment-history-filter-${t.code.toLowerCase()}`}
                  aria-pressed={filter === t.code}
                  onClick={() => setFilter(t.code)}
                >
                  {t.codeName}
                </button>
              ))}
            </div>
            {history.length === 0 && <p>변경 이력이 없습니다.</p>}
            {history.map((h) => (
              <article
                className="assignment-history"
                key={h.historyId}
                data-testid={`assignment-history-${h.historyId}`}
              >
                <p>
                  {h.effectiveDate} · {h.changedBy} ·{" "}
                  {formatDateTime(h.changedAt)}
                </p>
                <strong>{h.changeTypeName}</strong>
                <p>
                  {h.beforeValue} → {h.afterValue}
                </p>
                <p>
                  {h.reason} {h.memo}
                </p>
                {h.retroactiveYn === "Y" && <span>소급 입력</span>}
                {h.correctedYn === "Y" && <span>정정됨</span>}
                {!readOnly &&
                  h.correctedYn !== "Y" &&
                  !["INPUT", "CRCT"].includes(h.changeTypeCode) && (
                    <button
                      data-testid={`assignment-history-correct-${h.historyId}`}
                      onClick={() => {
                        setCorrecting(h);
                        setValue(h.afterValue);
                        setCorrectionReason("");
                        setFields({});
                      }}
                    >
                      정정
                    </button>
                  )}
              </article>
            ))}
          </>
        )}
      </DetailPanel>
      {!readOnly && changing && (
        <Dialog
          title="변경 기록"
          testId="assignment-change-dialog"
          onClose={() => setChanging(false)}
        >
          {error && <p role="alert">{error}</p>}
          <label>
            변경 종류
            <select
              data-testid="assignment-change-type"
              value={type}
              onChange={(e) => selectType(e.target.value)}
            >
              <option value="">선택</option>
              {types
                .filter((t) =>
                  [
                    "EXT",
                    "EARLY",
                    "RATE",
                    "EXTP",
                    "KIND",
                    ...(priceAllowed ? ["UPRC"] : []),
                  ].includes(t.code),
                )
                .map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.codeName}
                  </option>
                ))}
            </select>
            {fieldError("changeTypeCode")}
          </label>
          <label>
            적용일
            <input
              data-testid="assignment-change-date"
              type={["UPRC", "RATE"].includes(type) ? "month" : "date"}
              value={["UPRC", "RATE"].includes(type) ? date.slice(0, 7) : date}
              onChange={(e) => setDate(e.target.value)}
            />
            {fieldError("effectiveDate")}
          </label>
          {["EXT", "EARLY"].includes(type) && (
            <label>
              새 종료일
              <input
                data-testid="assignment-change-end"
                type="date"
                value={end}
                onChange={(e) => setEnd(e.target.value)}
              />
              {fieldError("newEndDate")}
            </label>
          )}
          {type === "UPRC" && (
            <label>
              새 단가
              <input
                data-testid="assignment-change-price"
                type="number"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
              {fieldError("newBillingPrice")}
            </label>
          )}
          {type === "RATE" && (
            <label>
              새 월 MM
              <input
                data-testid="assignment-change-mm"
                type="number"
                step="0.01"
                value={mm}
                onChange={(e) => setMm(e.target.value)}
              />
              {fieldError("newMonthlyMm")}
            </label>
          )}
          {type === "EXTP" && (
            <label>
              새 연장 가능성
              <select
                data-testid="assignment-change-extension"
                value={extension}
                onChange={(e) => setExtension(e.target.value)}
              >
                <option value="">미정</option>
                {extensions.map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.codeName}
                  </option>
                ))}
              </select>
              {fieldError("newExtensionPossibilityCode")}
            </label>
          )}
          {type === "KIND" && (
            <label>
              새 투입 구분
              <select
                data-testid="assignment-change-input"
                value={input}
                onChange={(e) => setInput(e.target.value)}
              >
                {inputs
                  .filter((t) => ["NEW", "ADD"].includes(t.code))
                  .map((t) => (
                    <option key={t.code} value={t.code}>
                      {t.codeName}
                    </option>
                  ))}
              </select>
              {fieldError("newInputTypeCode")}
            </label>
          )}
          <label>
            사유
            <select
              data-testid="assignment-change-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            >
              <option value="">선택</option>
              {reasons.map((t) => (
                <option key={t.code} value={t.code}>
                  {t.codeName}
                </option>
              ))}
            </select>
            {fieldError("reasonCode")}
          </label>
          <label>
            메모
            <textarea
              data-testid="assignment-change-memo"
              maxLength={200}
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
            />
            {fieldError("memo")}
          </label>
          {project?.closedMonth && date.slice(0, 7) <= project.closedMonth && (
            <Banner kind="warn">
              마감월 금액은 유지됩니다. 전월 조정은 UI-10에서 직접 입력해
              주세요.
            </Banner>
          )}
          <div className="app-actions">
            <button
              data-testid="assignment-change-cancel"
              disabled={busy}
              onClick={() => setChanging(false)}
            >
              취소
            </button>
            <button
              data-testid="assignment-change-save"
              disabled={busy}
              onClick={() => void saveChange()}
            >
              저장
            </button>
          </div>
        </Dialog>
      )}
      {!readOnly && correcting && (
        <Dialog
          title="이력 정정"
          testId="assignment-correction-dialog"
          onClose={() => setCorrecting(null)}
        >
          {error && <p role="alert">{error}</p>}
          <p>정정은 현재 배정을 바꾸지 않습니다.</p>
          <label>
            바른 내용
            <input
              data-testid="assignment-correction-value"
              maxLength={200}
              value={value}
              onChange={(e) => setValue(e.target.value)}
            />
            {fieldError("correctedValue")}
          </label>
          <label>
            정정 사유
            <input
              data-testid="assignment-correction-reason"
              maxLength={200}
              value={correctionReason}
              onChange={(e) => setCorrectionReason(e.target.value)}
            />
            {fieldError("reason")}
          </label>
          <div className="app-actions">
            <button
              data-testid="assignment-correction-cancel"
              disabled={busy}
              onClick={() => setCorrecting(null)}
            >
              취소
            </button>
            <button
              data-testid="assignment-correction-save"
              disabled={busy}
              onClick={() => void saveCorrection()}
            >
              저장
            </button>
          </div>
        </Dialog>
      )}
    </>
  );
}
