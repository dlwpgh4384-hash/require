import { useEffect, useRef, useState } from "react";
import { DetailPanel, KeyValues } from "../../shared/list/DetailPanel";
import { Dialog } from "../../shared/feedback/Dialog";
import {
  DeveloperRequestError,
  developerFailure,
  getCommonDeveloper,
  getCommonDeveloperAssignments,
  getCommonDeveloperChangeHistory,
  getCommonDeveloperStatuses,
  postCommonDeveloperStatus,
  putCommonDeveloperStatusEndDate,
  postCommonDeveloperReturnCancel,
  postCommonDeveloperRetirementCancel,
  type Developer,
  type DeveloperAssignment,
  type DeveloperStatus,
  type DeveloperChange,
} from "./developers";

type Action = {
  kind: "register" | "end" | "return" | "retire";
  row?: DeveloperStatus;
};
export function DeveloperDetail({
  developerId,
  onClose,
  onEdit,
  onChanged,
}: {
  developerId: string;
  onClose: () => void;
  onEdit: (developer: Developer) => void;
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
  const empty = (message: string) => (
    <p className="developer-empty">{message}</p>
  );
  return (
    <>
      <DetailPanel
        title={developer?.name || "개발자 상세"}
        sub={developer?.managementNumber}
        testId="developer-detail"
        onClose={onClose}
        actions={
          <button
            type="button"
            data-testid="developer-edit"
            disabled={!developer || loading || !!error}
            onClick={() => developer && onEdit(developer)}
          >
            수정
          </button>
        }
        activeTab={tab}
        onTab={setTab}
        tabs={[
          {
            key: "basic",
            label: "기본 정보",
            content: developer && (
              <KeyValues
                items={[
                  ["이름", developer.name],
                  ["관리번호", developer.managementNumber],
                  ["소속", developer.organizationName],
                  ["직원구분", developer.employeeTypeName],
                  ["등급", developer.gradeName || "–"],
                  ["주 직무", developer.primaryJobName],
                  ["보조 직무", developer.secondaryJobCode || "–"],
                  ["직위", developer.positionCode || "–"],
                  ["직접/간접", developer.directClassCode],
                  ["영업 파트", developer.salesPartId],
                  ["입사일", developer.hireDate || "–"],
                  [
                    "상태",
                    `${developer.workStatusName}${developer.retirementScheduled ? " · 퇴사 예정" : ""}`,
                  ],
                  [
                    "월 단가",
                    developer.monthlyRate == null
                      ? "–"
                      : developer.monthlyRate.toLocaleString(),
                  ],
                  [
                    "월 급여·지급액",
                    developer.monthlyPay == null
                      ? "–"
                      : developer.monthlyPay.toLocaleString(),
                  ],
                  ["스킬", developer.skillCodes?.join(", ") || "–"],
                  ["거주지", developer.homeAddress || "–"],
                ]}
              />
            ),
          },
          {
            key: "assignments",
            label: "투입 이력",
            content: assignments.length ? (
              <div className="developer-table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>프로젝트</th>
                      <th>직무</th>
                      <th>시작일</th>
                      <th>종료일</th>
                      <th>MM</th>
                    </tr>
                  </thead>
                  <tbody>
                    {assignments.map((a, i) => (
                      <tr
                        key={`${a.projectName}-${a.startDate}-${i}`}
                        data-testid={`developer-assignment-${i}`}
                      >
                        <td>{a.projectName}</td>
                        <td>{a.jobName}</td>
                        <td>{a.startDate}</td>
                        <td>{a.endDate}</td>
                        <td>{a.mm}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              empty("투입 이력이 없습니다.")
            ),
          },
          {
            key: "statuses",
            label: "근무 상태 이력",
            content: (
              <>
                {developer?.workStatusName !== "퇴사" && (
                  <button
                    type="button"
                    data-testid="developer-status-add"
                    onClick={() => setAction({ kind: "register" })}
                  >
                    상태 등록
                  </button>
                )}
                {statuses.length ? (
                  <div className="developer-table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>상태</th>
                          <th>시작일</th>
                          <th>종료일</th>
                          <th>사유</th>
                          <th>등록자</th>
                          <th>관리</th>
                        </tr>
                      </thead>
                      <tbody>
                        {statuses.map((s, i) => (
                          <tr
                            key={s.statusId || `hire-${i}`}
                            data-testid={`developer-status-${s.statusId || "hire"}`}
                            className={
                              s.ongoing || s.retirementScheduled
                                ? "developer-state-highlight"
                                : ""
                            }
                          >
                            <td>
                              {s.statusName}
                              {s.retirementScheduled ? " · 퇴사 예정" : ""}
                            </td>
                            <td>{s.startDate}</td>
                            <td>{s.endDate || "미정"}</td>
                            <td>{s.reason || "–"}</td>
                            <td>{s.registeredBy}</td>
                            <td>
                              {s.statusName === "휴직" &&
                                s.ongoing &&
                                s.statusId && (
                                  <button
                                    data-testid={`developer-end-${s.statusId}`}
                                    onClick={() =>
                                      setAction({ kind: "end", row: s })
                                    }
                                  >
                                    종료일 수정
                                  </button>
                                )}
                              {s.statusName === "재직" && s.statusId && (
                                <button
                                  data-testid={`developer-return-${s.statusId}`}
                                  onClick={() =>
                                    setAction({ kind: "return", row: s })
                                  }
                                >
                                  재직 취소
                                </button>
                              )}
                              {s.statusName === "퇴사" && s.statusId && (
                                <button
                                  data-testid={`developer-retire-${s.statusId}`}
                                  onClick={() =>
                                    setAction({ kind: "retire", row: s })
                                  }
                                >
                                  퇴사 취소
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  empty("근무 상태 이력이 없습니다.")
                )}
              </>
            ),
          },
          {
            key: "changes",
            label: "변경 이력",
            content: changes.length ? (
              <div className="developer-table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>항목</th>
                      <th>이전</th>
                      <th>이후</th>
                      <th>시각</th>
                      <th>등록자</th>
                      <th>사유</th>
                    </tr>
                  </thead>
                  <tbody>
                    {changes.map((c, i) => (
                      <tr
                        key={`${c.changedAt}-${i}`}
                        data-testid={`developer-change-${i}`}
                      >
                        <td>{c.fieldName}</td>
                        <td>{c.beforeValue || "–"}</td>
                        <td>{c.afterValue || "–"}</td>
                        <td>{c.changedAt}</td>
                        <td>{c.changedBy}</td>
                        <td>{c.reason || "–"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              empty("변경 이력이 없습니다.")
            ),
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
                재시도
              </button>
            </p>
          ) : (
            t.content
          ),
        }))}
        footer={
          <button data-testid="developer-detail-confirm" onClick={onClose}>
            닫기
          </button>
        }
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
  const [date, setDate] = useState("");
  const [endDate, setEndDate] = useState(action.row?.endDate || "");
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
  const execute = async () => {
    if (pending.current) return;
    setError(null);
    if (action.kind === "retire" && !reason.trim()) {
      setError(
        new DeveloperRequestError(400, "취소 사유를 입력해 주세요.", [
          { field: "reason", message: "취소 사유를 입력해 주세요." },
        ]),
      );
      return;
    }
    if (action.kind === "register" && !date) {
      setError(
        new DeveloperRequestError(400, "날짜를 입력해 주세요.", [
          { field: "date", message: "날짜를 입력해 주세요." },
        ]),
      );
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
      if (action.kind === "end")
        await putCommonDeveloperStatusEndDate(
          developerId,
          action.row!.statusId!,
          endDate || null,
        );
      if (action.kind === "return")
        await postCommonDeveloperReturnCancel(
          developerId,
          action.row!.statusId!,
        );
      if (action.kind === "retire")
        await postCommonDeveloperRetirementCancel(
          developerId,
          action.row!.statusId!,
          reason,
        );
      onSaved();
    } catch (e) {
      setError(e);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog
      title={title}
      testId="developer-status-dialog"
      onClose={() => !pending.current && onClose()}
    >
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void execute();
        }}
      >
        {action.kind === "register" && (
          <>
            <label htmlFor="developer-status-name">상태 *</label>
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
            <label htmlFor="developer-status-date">
              {statusName === "퇴사"
                ? "퇴사일"
                : statusName === "재직"
                  ? "복귀일"
                  : "시작일"}{" "}
              *
            </label>
            <input
              id="developer-status-date"
              data-testid="developer-status-date"
              type="date"
              disabled={busy}
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </>
        )}
        {(action.kind === "end" ||
          (action.kind === "register" && statusName === "휴직")) && (
          <>
            <label htmlFor="developer-status-end">종료일 (비우면 미정)</label>
            <input
              id="developer-status-end"
              data-testid="developer-status-end"
              type="date"
              disabled={busy}
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </>
        )}
        {(action.kind === "retire" || action.kind === "register") && (
          <>
            <label htmlFor="developer-status-reason">
              {action.kind === "retire" ? "취소 사유 *" : "사유"}
            </label>
            <textarea
              id="developer-status-reason"
              data-testid="developer-status-reason"
              maxLength={300}
              disabled={busy}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </>
        )}
        {action.kind === "return" && (
          <p>취소하면 연결 휴직의 원래 종료일을 복원합니다.</p>
        )}
        {error != null && (
          <div role="alert">
            <p>{developerFailure(error)}</p>
            {error instanceof DeveloperRequestError &&
              error.errors.map((e) => (
                <p
                  key={e.field}
                  data-testid={`developer-status-error-${e.field}`}
                >
                  {e.message}
                </p>
              ))}
          </div>
        )}
        <footer className="app-actions">
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
            data-testid="developer-status-save"
            disabled={busy}
          >
            {busy
              ? "처리 중…"
              : action.kind === "return" || action.kind === "retire"
                ? "취소"
                : "저장"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
