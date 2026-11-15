import { useEffect, useRef, useState, type ReactNode } from "react";
import { Badge, DetailPanel, KeyValues } from "../../shared/list";
import { Dialog } from "../../shared/feedback/Dialog";
import { formatDateTime, koreanToday } from "../../shared/time";
import {
  DeveloperRequestError,
  developerFailure,
  directLabel,
  getCommonDeveloper,
  getCommonDeveloperAssignments,
  getCommonDeveloperChangeHistory,
  getCommonDeveloperStatuses,
  postCommonDeveloperStatus,
  putCommonDeveloperStatusEndDate,
  postCommonDeveloperReturnCancel,
  postCommonDeveloperRetirementCancel,
  won,
  type Developer,
  type DeveloperAssignment,
  type DeveloperStatus,
  type DeveloperChange,
} from "./developers";

type Action = {
  kind: "register" | "end" | "return" | "retire";
  row?: DeveloperStatus;
};
const statusKind = (name: string) =>
  name === "재직" ? "ok" : name === "퇴사" ? "bad" : name === "투입중" ? "info" : name === "철수예정" ? "warn" : name === "대기" ? "bad" : "lock";

/** 시안 개발자 상세: 제목=이름, 작은 제목 「관리번호 · 소속 · 직원구분」, [수정] [사용 중지] [×], 탭 4개. */
export function DeveloperDetail({
  developerId,
  onClose,
  onEdit,
  onStop,
  onChanged,
}: {
  developerId: string;
  onClose: () => void;
  onEdit: (developer: Developer) => void;
  onStop: (developer: Developer) => void;
  onChanged: () => void;
}) {
  const [developer, setDeveloper] = useState<Developer | null>(null);
  const [assignments, setAssignments] = useState<DeveloperAssignment[]>([]);
  const [statuses, setStatuses] = useState<DeveloperStatus[]>([]);
  const [changes, setChanges] = useState<DeveloperChange[]>([]);
  const [tab, setTab] = useState("basic");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [action, setAction] = useState<Action | null>(null);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    Promise.all([
      getCommonDeveloper(developerId),
      getCommonDeveloperAssignments(developerId),
      getCommonDeveloperStatuses(developerId),
      getCommonDeveloperChangeHistory(developerId),
    ])
      .then(([d, a, s, c]) => {
        if (active) {
          setDeveloper(d);
          setAssignments(a.items);
          setStatuses(s.items);
          setChanges(c.items);
        }
      })
      .catch((reason) => {
        if (active) setError(developerFailure(reason));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [developerId, revision]);
  const empty = (message: string) => <p className="developer-empty">{message}</p>;
  const d = developer;
  const table = (head: string[], body: ReactNode) => (
    <div className="ls-twrap developer-free">
      <table className="ls-t">
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i} className={h === "MM" ? "ls-right" : undefined}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{body}</tbody>
      </table>
    </div>
  );
  return (
    <>
      <DetailPanel
        title={d?.name || "개발자 상세"}
        sub={d ? `${d.managementNumber} · ${d.organizationName} · ${d.employeeTypeName}` : undefined}
        testId="developer-detail"
        onClose={onClose}
        actions={
          <>
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="developer-edit"
              disabled={!d || loading || !!error}
              onClick={() => d && onEdit(d)}
            >
              수정
            </button>
            {d && (
              <button
                type="button"
                className="ls-btn ls-btn-sm ls-btn-dt"
                data-testid="developer-stop"
                disabled={loading || !!error}
                onClick={() => onStop(d)}
              >
                {d.useYn === "Y" ? "사용 중지" : "다시 사용"}
              </button>
            )}
          </>
        }
        activeTab={tab}
        onTab={setTab}
        tabs={[
          {
            key: "basic",
            label: "기본 정보",
            content: d && (
              <KeyValues
                items={[
                  ["관리번호", d.managementNumber],
                  ["이름", d.name],
                  ["소속", d.organizationName],
                  ["직원구분", <Badge kind="neutral">{d.employeeTypeName}</Badge>],
                  ["등급", d.gradeName || "–"],
                  ["주 직무", d.primaryJobName],
                  [
                    "보유 스킬",
                    d.skillNames?.length ? (
                      <span className="developer-chips">
                        {d.skillNames.map((s) => (
                          <span className="ls-chip" key={s}>
                            {s}
                          </span>
                        ))}
                      </span>
                    ) : (
                      "–"
                    ),
                  ],
                  ["거주지", d.homeAddress || "–"],
                  ["영업 파트", d.salesPartName || "–"],
                  ["입사일", d.hireDate || "–"],
                  [
                    "상태",
                    <>
                      <Badge kind={statusKind(d.workStatusName)}>{d.workStatusName}</Badge>
                      {d.retirementScheduled && <Badge kind="warn">퇴사 예정</Badge>}
                    </>,
                  ],
                  ["월 단가", <span className="developer-num">{d.monthlyRate == null ? "–" : `${won(d.monthlyRate)} 원`}</span>],
                  [
                    "월 급여·지급액",
                    <span className="developer-num">
                      {d.monthlyPay == null ? "–" : `${won(d.monthlyPay)} 원`}{" "}
                      <span className="ls-cap">(민감 항목: 관리자·경영진·재무만 보임)</span>
                    </span>,
                  ],
                  ["직위", d.positionName || "–"],
                  ["보조 직무", d.secondaryJobName || "–"],
                  ["직접/간접", directLabel(d.directClassName)],
                ]}
              />
            ),
          },
          {
            key: "assignments",
            label: "투입 이력",
            content: assignments.length
              ? table(
                  ["프로젝트", "직무", "기간", "MM"],
                  assignments.map((a, i) => (
                    <tr key={`${a.projectName}-${a.startDate}-${i}`} data-testid={`developer-assignment-${i}`}>
                      <td>{a.projectName}</td>
                      <td>{a.jobName}</td>
                      <td>
                        {a.startDate} ~ {a.endDate}
                      </td>
                      <td className="ls-right">{Number(a.mm).toFixed(2)}</td>
                    </tr>
                  )),
                )
              : empty("투입 이력이 없습니다."),
          },
          {
            key: "statuses",
            label: "근무 상태 이력",
            content: (
              <>
                <div className="developer-status-head">
                  <span className="ls-cap">
                    근무 상태는 투입·휴직 기록으로 매일 자동으로 정합니다. 여기서는 휴직·퇴사·재직(휴직 복귀)을 등록합니다(관리자·재무).
                  </span>
                  {d?.workStatusName !== "퇴사" && (
                    <button
                      type="button"
                      className="ls-btn ls-btn-sm ls-btn-p"
                      data-testid="developer-status-add"
                      onClick={() => setAction({ kind: "register" })}
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M12 5v14M5 12h14" />
                      </svg>
                      상태 등록
                    </button>
                  )}
                </div>
                {statuses.length
                  ? table(
                      ["상태", "시작일", "종료일", "사유", "등록자", ""],
                      statuses.map((s, i) => (
                        <tr key={s.statusId || `hire-${i}`} data-testid={`developer-status-${s.statusId || "hire"}`}>
                          <td>
                            <Badge kind={statusKind(s.statusName)}>{s.statusName}</Badge>
                            {s.ongoing && <Badge kind="warn">진행 중</Badge>}
                            {s.retirementScheduled && <Badge kind="warn">퇴사 예정</Badge>}
                          </td>
                          <td>{s.startDate}</td>
                          <td>{s.endDate || "–"}</td>
                          <td>{s.reason || "–"}</td>
                          <td>{s.registeredBy || "–"}</td>
                          <td>
                            {s.statusName === "휴직" && s.ongoing && s.statusId && (
                              <button className="ls-lnk developer-lnk" data-testid={`developer-end-${s.statusId}`} onClick={() => setAction({ kind: "end", row: s })}>
                                종료일 수정
                              </button>
                            )}
                            {s.statusName === "재직" && s.statusId && (
                              <button className="ls-lnk developer-lnk" data-testid={`developer-return-${s.statusId}`} onClick={() => setAction({ kind: "return", row: s })}>
                                재직 취소
                              </button>
                            )}
                            {s.statusName === "퇴사" && s.statusId && (
                              <button className="ls-lnk developer-lnk" data-testid={`developer-retire-${s.statusId}`} onClick={() => setAction({ kind: "retire", row: s })}>
                                퇴사 취소
                              </button>
                            )}
                          </td>
                        </tr>
                      )),
                    )
                  : empty("근무 상태 이력이 없습니다.")}
                <p className="ls-note">
                  휴직 연장은 종료일을 고칩니다. 휴직에서 돌아오면 「재직」으로 등록합니다. 퇴사는 퇴사일 전까지 취소할 수 있고, 퇴사일이 지난 뒤에는 관리자만 퇴사한 달이 마감되기 전까지 취소할 수 있습니다(실수 바로잡기용).
                </p>
              </>
            ),
          },
          {
            key: "changes",
            label: `변경 이력 (${changes.length})`,
            content: changes.length
              ? table(
                  ["변경일", "항목", "변경 전", "변경 후", "처리자"],
                  changes.map((c, i) => (
                    <tr key={`${c.changedAt}-${i}`} data-testid={`developer-change-${i}`}>
                      <td>{formatDateTime(c.changedAt).slice(0, 10)}</td>
                      <td>{c.fieldName}</td>
                      <td>{c.beforeValue || "–"}</td>
                      <td>{c.afterValue || "–"}</td>
                      <td>{c.changedBy}</td>
                    </tr>
                  )),
                )
              : empty("변경 이력이 없습니다."),
          },
        ].map((t) => ({
          ...t,
          content: loading ? (
            <p role="status">읽는 중…</p>
          ) : error ? (
            <p role="alert">
              {error}
              <button data-testid="developer-detail-retry" onClick={() => setRevision((v) => v + 1)}>
                다시 시도
              </button>
            </p>
          ) : (
            t.content
          ),
        }))}
      />
      {action && (
        <DeveloperStatusDialog
          action={action}
          developerId={developerId}
          onClose={() => setAction(null)}
          onSaved={() => {
            setAction(null);
            setRevision((v) => v + 1);
            onChanged();
          }}
        />
      )}
    </>
  );
}

/** 시안 근무 상태 등록·휴직 종료일 수정 창. 재직·퇴사 취소는 확인 창. */
function DeveloperStatusDialog({
  action,
  developerId,
  onClose,
  onSaved,
}: {
  action: Action;
  developerId: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [statusName, setStatusName] = useState("휴직");
  const [date, setDate] = useState(() => koreanToday());
  const [endDate, setEndDate] = useState(action.row?.endDate || "");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const title = {
    register: "근무 상태 등록",
    end: "휴직 종료일 수정",
    return: "재직을 취소할까요?",
    retire: "퇴사를 취소할까요?",
  }[action.kind];
  const execute = async () => {
    if (pending.current) return;
    setError(null);
    if (action.kind === "retire" && !reason.trim()) {
      setError(new DeveloperRequestError(400, "취소 사유를 입력해 주세요.", [{ field: "reason", message: "취소 사유를 입력해 주세요." }]));
      return;
    }
    if (action.kind === "register" && !date) {
      setError(new DeveloperRequestError(400, "날짜를 입력해 주세요.", [{ field: "date", message: "날짜를 입력해 주세요." }]));
      return;
    }
    pending.current = true;
    setBusy(true);
    try {
      if (action.kind === "register")
        await postCommonDeveloperStatus(developerId, {
          statusName,
          date,
          ...(statusName === "휴직" ? { endDate: endDate || null } : {}),
          reason,
        });
      if (action.kind === "end") await putCommonDeveloperStatusEndDate(developerId, action.row!.statusId!, endDate || null);
      if (action.kind === "return") await postCommonDeveloperReturnCancel(developerId, action.row!.statusId!);
      if (action.kind === "retire") await postCommonDeveloperRetirementCancel(developerId, action.row!.statusId!, reason);
      onSaved();
    } catch (e) {
      setError(e);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const fieldError = error instanceof DeveloperRequestError ? error.errors : [];
  return (
    <Dialog title={title} closable testId="developer-status-dialog" onClose={() => !pending.current && onClose()}>
      <form
        noValidate
        className="developer-status-form"
        onSubmit={(e) => {
          e.preventDefault();
          void execute();
        }}
      >
        <fieldset>
          {action.kind === "end" && action.row && (
            <p className="ls-cap">
              {action.row.startDate} ~ {action.row.endDate || "미정"}
              {action.row.reason ? ` · ${action.row.reason}` : ""}
            </p>
          )}
          {action.kind === "register" && (
            <>
              <label className="developer-fc">
                <span className="developer-req">상태</span>
                <select
                  id="developer-status-name"
                  data-testid="developer-status-name"
                  disabled={busy}
                  value={statusName}
                  onChange={(e) => {
                    setStatusName(e.target.value);
                    setEndDate("");
                  }}
                >
                  <option>휴직</option>
                  <option>퇴사</option>
                  <option>재직</option>
                </select>
                <span className="developer-help">투입중·철수예정·대기는 자동으로 정해집니다. 재직은 휴직에서 돌아올 때만 등록합니다.</span>
              </label>
              <label className="developer-fc">
                <span className="developer-req">{statusName === "퇴사" ? "퇴사일" : statusName === "재직" ? "복귀일" : "시작일"}</span>
                <input
                  id="developer-status-date"
                  data-testid="developer-status-date"
                  type="date"
                  disabled={busy}
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                />
              </label>
            </>
          )}
          {(action.kind === "end" || (action.kind === "register" && statusName === "휴직")) && (
            <label className="developer-fc">
              <span className={action.kind === "end" ? "developer-req" : undefined}>종료일</span>
              <input
                id="developer-status-end"
                data-testid="developer-status-end"
                type="date"
                disabled={busy}
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
              <span className="developer-help">
                {action.kind === "end"
                  ? "휴직을 연장하려면 더 뒤 날짜를 넣습니다. 휴직에서 돌아올 때는 [상태 등록]에서 「재직」으로 등록합니다."
                  : "비우면 복직일 미정"}
              </span>
            </label>
          )}
          {(action.kind === "retire" || action.kind === "register") && (
            <label className="developer-fc">
              <span className={action.kind === "retire" ? "developer-req" : undefined}>{action.kind === "retire" ? "취소 사유" : "사유"}</span>
              <input
                id="developer-status-reason"
                data-testid="developer-status-reason"
                maxLength={action.kind === "retire" ? 300 : 100}
                placeholder={action.kind === "register" ? "예: 육아휴직" : undefined}
                disabled={busy}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          )}
          {action.kind === "return" && <p>취소하면 연결된 휴직의 원래 종료일로 되돌립니다.</p>}
          {error != null && (
            <div role="alert" className="field-error">
              <p>{developerFailure(error)}</p>
              {fieldError.map((e) => (
                <p key={e.field} data-testid={`developer-status-error-${e.field}`}>
                  {e.message}
                </p>
              ))}
            </div>
          )}
        </fieldset>
        <div className="app-actions">
          <button type="button" data-testid="developer-status-close" disabled={busy} onClick={onClose}>
            {action.kind === "return" || action.kind === "retire" ? "닫기" : "취소"}
          </button>
          <button type="submit" className="primary-button" data-testid="developer-status-save" disabled={busy}>
            {busy ? "처리 중…" : action.kind === "register" ? "등록" : action.kind === "end" ? "저장" : "취소하기"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
