import { useEffect, useRef, useState, type ReactNode } from "react";
import { ProjectLink } from "../projects/ProjectLink";
import { Badge, Banner, DetailPanel, KeyValues } from "../../shared/list";
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
  type DeveloperStatusList,
  type DeveloperChange,
} from "./developers";

type Action = {
  kind: "register" | "end" | "return" | "retire";
  row?: DeveloperStatus;
};
const statusKind = (name: string) =>
  name === "재직"
    ? "ok"
    : name === "퇴사"
      ? "bad"
      : name === "투입중"
        ? "info"
        : name === "철수예정"
          ? "warn"
          : name === "대기"
            ? "bad"
            : "lock";

/** 시안 개발자 상세: 제목=이름, 작은 제목 「관리번호 · 소속 · 직원구분」, [수정] [사용 중지] [×], 탭 4개. */
export function DeveloperDetail({
  developerId,
  onClose,
  onEdit,
  onStop,
  onNotice,
  onChanged,
}: {
  developerId: string;
  onClose: () => void;
  onEdit: (developer: Developer) => void;
  onStop: (developer: Developer) => void;
  /** 저장 없이 안내만 띄운다(시안 toast warn). */
  onNotice: (message: string) => void;
  /** 바뀐 뒤 목록을 다시 읽고 안내(message)를 띄운다. */
  onChanged: (message?: string) => void;
}) {
  const [developer, setDeveloper] = useState<Developer | null>(null);
  const [assignments, setAssignments] = useState<DeveloperAssignment[]>([]);
  const [statuses, setStatuses] = useState<DeveloperStatus[]>([]);
  const [permission, setPermission] = useState<
    Omit<DeveloperStatusList, "items">
  >({
    canManageStatuses: false,
    canCancelPastRetirement: false,
  });
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
          setPermission({
            canManageStatuses: !!s.canManageStatuses,
            canCancelPastRetirement: !!s.canCancelPastRetirement,
          });
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
  const empty = (message: string) => (
    <p className="developer-empty">{message}</p>
  );
  const d = developer;
  const today = koreanToday();
  const gone = d?.workStatusName === "퇴사";
  const cap = (text: string) => <span className="ls-cap">{text}</span>;
  /** 시안 statHtml act: 줄마다 할 수 있는 일 또는 안내 */
  const act = (s: DeveloperStatus) => {
    if (!s.statusId || !permission.canManageStatuses) return null;
    if (s.statusName === "퇴사") {
      if (s.startDate < today && !permission.canCancelPastRetirement)
        return cap("관리자만 취소");
      return (
        <button
          className="ls-lnk developer-lnk"
          data-testid={`developer-retire-${s.statusId}`}
          onClick={() =>
            s.startDate < today && s.monthClosed
              ? onNotice(
                  `퇴사한 달(${s.startDate.slice(0, 7)})이 마감되어 취소할 수 없습니다. 다시 쓰려면 새로 등록하세요.`,
                )
              : setAction({ kind: "retire", row: s })
          }
        >
          퇴사 취소
        </button>
      );
    }
    if (s.statusName === "휴직" && s.closedByReturn)
      return cap("재직으로 닫힘");
    if (s.statusName === "휴직" && (!s.endDate || s.endDate >= today))
      return (
        <button
          className="ls-lnk developer-lnk"
          data-testid={`developer-end-${s.statusId}`}
          onClick={() => setAction({ kind: "end", row: s })}
        >
          종료일 수정
        </button>
      );
    if (s.statusName === "재직")
      return s.monthClosed ? (
        cap("마감됨")
      ) : (
        <button
          className="ls-lnk developer-lnk"
          data-testid={`developer-return-${s.statusId}`}
          onClick={() => setAction({ kind: "return", row: s })}
        >
          재직 취소
        </button>
      );
    return cap("끝남");
  };
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
        sub={
          d
            ? `${d.managementNumber} · ${d.organizationName} · ${d.employeeTypeName}`
            : undefined
        }
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
                className={
                  d.useYn === "Y"
                    ? "ls-btn ls-btn-sm ls-btn-dt"
                    : "ls-btn ls-btn-sm"
                }
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
              <>
                {d.useYn === "N" && (
                  // 시안: 사용 중지된 개발자 안내 띠
                  <Banner kind="lock">
                    <svg
                      className="developer-banner-icon"
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.8"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <rect x="5" y="11" width="14" height="10" rx="2" />
                      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
                    </svg>
                    사용 중지된 개발자입니다. 배정 후보에서 빠지고 기록은 그대로
                    남습니다.
                  </Banner>
                )}
                <KeyValues
                  items={[
                    ["관리번호", d.managementNumber],
                    ["이름", d.name],
                    ["소속", d.organizationName],
                    [
                      "직원구분",
                      <Badge kind="neutral">{d.employeeTypeName}</Badge>,
                    ],
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
                        <Badge kind={statusKind(d.workStatusName)}>
                          {d.workStatusName}
                        </Badge>
                        {d.retirementScheduled && (
                          <Badge kind="warn">퇴사 예정</Badge>
                        )}
                      </>,
                    ],
                    [
                      "월 단가",
                      <span className="developer-num">
                        {d.monthlyRate == null
                          ? "–"
                          : `${won(d.monthlyRate)} 원`}
                      </span>,
                    ],
                    [
                      "월 급여·지급액",
                      <span className="developer-num">
                        {d.monthlyPay == null ? "–" : `${won(d.monthlyPay)} 원`}{" "}
                        <span className="ls-cap">
                          (민감 항목: 관리자·경영진·재무만 보임)
                        </span>
                      </span>,
                    ],
                    ["직위", d.positionName || "–"],
                    ["보조 직무", d.secondaryJobName || "–"],
                    ["직접/간접", directLabel(d.directClassName)],
                  ]}
                />
              </>
            ),
          },
          {
            key: "assignments",
            label: "투입 이력",
            content: assignments.length
              ? table(
                  ["프로젝트", "직무", "기간", "MM"],
                  assignments.map((a, i) => (
                    <tr
                      key={`${a.projectName}-${a.startDate}-${i}`}
                      data-testid={`developer-assignment-${i}`}
                    >
                      <td>
                        <ProjectLink
                          projectId={a.projectId}
                          name={a.projectName}
                          testId={`developer-history-project-${i}`}
                        />
                      </td>
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
                    {gone
                      ? "퇴사한 개발자입니다. 상태를 더 등록할 수 없습니다. 다시 쓰려면 개발자 풀에서 새로 등록하세요."
                      : "근무 상태는 투입·휴직 기록으로 매일 자동으로 정합니다. 여기서는 휴직·퇴사·재직(휴직 복귀)을 등록합니다(관리자·재무)."}
                  </span>
                  {!gone && permission.canManageStatuses && (
                    <button
                      type="button"
                      className="ls-btn ls-btn-sm ls-btn-p"
                      data-testid="developer-status-add"
                      onClick={() => setAction({ kind: "register" })}
                    >
                      <svg
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.8"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                      >
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
                        <tr
                          key={s.statusId || `hire-${i}`}
                          data-testid={`developer-status-${s.statusId || "hire"}`}
                        >
                          <td>
                            <Badge kind={statusKind(s.statusName)}>
                              {s.statusName}
                            </Badge>
                            {s.ongoing && <Badge kind="warn">진행 중</Badge>}
                            {s.retirementScheduled && (
                              <Badge kind="warn">예정</Badge>
                            )}
                          </td>
                          <td>{s.startDate}</td>
                          <td>
                            {s.endDate ||
                              (s.statusName === "휴직" && s.statusId ? (
                                <span className="developer-sub">
                                  복직일 미정
                                </span>
                              ) : (
                                "–"
                              ))}
                          </td>
                          <td>{s.reason || "–"}</td>
                          <td>{s.registeredBy || "–"}</td>
                          <td>{act(s)}</td>
                        </tr>
                      )),
                    )
                  : empty("근무 상태 이력이 없습니다.")}
                <p className="ls-note">
                  휴직 연장은 종료일을 고칩니다. 휴직에서 돌아오면 「재직」으로
                  등록합니다. 퇴사는 퇴사일 전까지 취소할 수 있고, 퇴사일이 지난
                  뒤에는 관리자만 퇴사한 달이 마감되기 전까지 취소할 수
                  있습니다(실수 바로잡기용).
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
                    <tr
                      key={`${c.changedAt}-${i}`}
                      data-testid={`developer-change-${i}`}
                    >
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
              <button
                data-testid="developer-detail-retry"
                onClick={() => setRevision((v) => v + 1)}
              >
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
          developerName={d?.name || ""}
          statuses={statuses}
          assignments={assignments}
          onClose={() => setAction(null)}
          onSaved={(message) => {
            setAction(null);
            setRevision((v) => v + 1);
            onChanged(message);
          }}
        />
      )}
    </>
  );
}

/** 시안 근무 상태 등록·휴직 종료일 수정·재직 취소·퇴사 취소 창. */
function DeveloperStatusDialog({
  action,
  developerId,
  developerName,
  statuses,
  assignments,
  onClose,
  onSaved,
}: {
  action: Action;
  developerId: string;
  developerName: string;
  statuses: DeveloperStatus[];
  assignments: DeveloperAssignment[];
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const today = koreanToday();
  const [statusName, setStatusName] = useState("휴직");
  const [date, setDate] = useState(today);
  // 시안: 종료일 수정 창은 지금 종료일(없으면 오늘)로 시작
  const [endDate, setEndDate] = useState(
    action.kind === "end" ? action.row?.endDate || today : "",
  );
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const title = {
    register: "근무 상태 등록",
    end: "휴직 종료일 수정",
    return: "재직 취소",
    retire: "퇴사 취소",
  }[action.kind];
  const dateLabel =
    statusName === "퇴사"
      ? "퇴사일"
      : statusName === "재직"
        ? "복귀일"
        : "시작일";
  const stop = (field: string, message: string) =>
    setError(new DeveloperRequestError(400, message, [{ field, message }]));
  const execute = async () => {
    if (pending.current) return;
    setError(null);
    if (action.kind === "retire" && !reason.trim())
      return stop("reason", "취소 사유를 넣어 주세요.");
    if (action.kind === "register" && !date)
      return stop("date", `${dateLabel}을 넣어 주세요.`);
    if (action.kind === "end" && (!endDate || endDate < action.row!.startDate))
      return stop("endDate", "시작일 이후 날짜를 넣어 주세요.");
    pending.current = true;
    setBusy(true);
    try {
      let message = "";
      if (action.kind === "register") {
        await postCommonDeveloperStatus(developerId, {
          statusName,
          date,
          ...(statusName === "휴직" ? { endDate: endDate || null } : {}),
          reason,
        });
        // 시안 statSave 안내
        if (statusName === "휴직") {
          const n = assignments.filter(
            (a) =>
              a.startDate <= (endDate || "9999-12-31") && a.endDate >= date,
          ).length;
          message = n
            ? `휴직을 등록했습니다. 그 기간에 투입 배정이 ${n}건 있으니 확인해 주세요.`
            : "휴직을 등록했습니다.";
        } else if (statusName === "재직")
          message = "재직(휴직 복귀)을 등록했습니다.";
        else {
          const closedLeave = statuses.some(
            (s) =>
              s.statusName === "휴직" &&
              s.statusId &&
              s.startDate < date &&
              (!s.endDate || s.endDate >= date),
          );
          message =
            date < today
              ? "퇴사를 등록했습니다. 퇴사일이 지나 사용 중지되었습니다."
              : `퇴사를 등록했습니다. ${date} 다음 날 자동으로 사용 중지됩니다.` +
                (closedLeave
                  ? " 진행 중이던 휴직은 퇴사일 전날로 닫았습니다."
                  : "");
        }
      }
      if (action.kind === "end") {
        await putCommonDeveloperStatusEndDate(
          developerId,
          action.row!.statusId!,
          endDate,
        );
        message = "휴직 종료일을 바꿨습니다.";
      }
      if (action.kind === "return") {
        await postCommonDeveloperReturnCancel(
          developerId,
          action.row!.statusId!,
        );
        message = "재직을 취소했습니다. 휴직 종료일이 원래대로 돌아갔습니다.";
      }
      if (action.kind === "retire") {
        await postCommonDeveloperRetirementCancel(
          developerId,
          action.row!.statusId!,
          reason,
        );
        message = "퇴사를 취소했습니다. 변경 이력에 남았습니다.";
      }
      onSaved(message);
    } catch (e) {
      setError(e);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const fieldError = error instanceof DeveloperRequestError ? error.errors : [];
  return (
    <Dialog
      title={title}
      closable
      testId="developer-status-dialog"
      onClose={() => !pending.current && onClose()}
    >
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
              {action.row.startDate} ~ {action.row.endDate || "복직일 미정"} ·{" "}
              {action.row.reason || "휴직"}
            </p>
          )}
          {action.kind === "return" && action.row && (
            <p>
              {action.row.startDate} 재직(휴직 복귀)을 취소합니다. 휴직 종료일이
              원래대로({action.row.restoreEndDate || "복직일 미정"}) 돌아갑니다.
              복귀일을 바꾸려면 취소한 뒤 다시 등록하세요.
            </p>
          )}
          {action.kind === "retire" && action.row && (
            <p>
              {developerName}님의 퇴사({action.row.startDate})를 취소합니다.
              실수로 등록한 경우에만 씁니다. 퇴사가 없던 일이 되고
              {action.row.startDate < today ? " 사용 중지도 풀립니다" : ""}.
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
                    setError(null);
                  }}
                >
                  <option>휴직</option>
                  <option>퇴사</option>
                  <option>재직</option>
                </select>
                <span className="developer-help">
                  투입중·철수예정·대기는 자동으로 정해집니다. 재직은 휴직에서
                  돌아올 때만 등록합니다.
                </span>
              </label>
              <label className="developer-fc">
                <span className="developer-req">{dateLabel}</span>
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
          {(action.kind === "end" ||
            (action.kind === "register" && statusName === "휴직")) && (
            <label className="developer-fc">
              <span
                className={action.kind === "end" ? "developer-req" : undefined}
              >
                종료일
              </span>
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
              <span
                className={
                  action.kind === "retire" ? "developer-req" : undefined
                }
              >
                {action.kind === "retire" ? "취소 사유" : "사유"}
              </span>
              <input
                id="developer-status-reason"
                data-testid="developer-status-reason"
                maxLength={100}
                placeholder={
                  action.kind === "retire"
                    ? "예: 다른 사람으로 잘못 등록"
                    : statusName === "휴직"
                      ? "예: 육아휴직"
                      : statusName === "퇴사"
                        ? "예: 개인 사정"
                        : "예: 육아휴직 복귀"
                }
                disabled={busy}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          )}
          {/* 시안 .errmsg: 오류가 없어도 빈 줄 자리를 둔다 */}
          <div
            role="alert"
            className="field-error developer-errmsg"
            hidden={action.kind === "return" && error == null}
          >
            {error != null &&
              (fieldError.length ? (
                fieldError.map((e) => (
                  <p
                    key={e.field}
                    data-testid={`developer-status-error-${e.field}`}
                  >
                    {e.message}
                  </p>
                ))
              ) : (
                <p>{developerFailure(error)}</p>
              ))}
          </div>
        </fieldset>
        <div className="app-actions">
          <button
            type="button"
            data-testid="developer-status-close"
            disabled={busy}
            onClick={onClose}
          >
            {action.kind === "return" || action.kind === "retire"
              ? "닫기"
              : "취소"}
          </button>
          <button
            type="submit"
            className="primary-button"
            data-testid="developer-status-save"
            disabled={busy}
          >
            {busy
              ? "처리 중…"
              : action.kind === "register"
                ? "등록"
                : action.kind === "end"
                  ? "저장"
                  : action.kind === "return"
                    ? "재직 취소"
                    : "퇴사 취소"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
