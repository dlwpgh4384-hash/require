import { useEffect, useState } from "react";
import { ConfirmDialog } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  DetailPanel,
  KeyValues,
  ListPager,
} from "../../shared/list";
import * as api from "./users";

const message = (failure: unknown) =>
  failure instanceof Error
    ? failure.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

export const stateBadge = (code: string) => {
  const name = api.STATE_NAMES[code] ?? code;
  return (
    <Badge kind={code === "USE" ? "ok" : code === "LOCK" ? "warn" : "lock"}>
      {name}
    </Badge>
  );
};
export const roleBadges = (names: string[]) =>
  names.length ? (
    names.map((name) => (
      <Badge key={name} kind="info">
        {name}
      </Badge>
    ))
  ) : (
    <>–</>
  );

/** 시안 UI-23 상세 패널: [수정] [비밀번호 초기화] [사용 중지|다시 사용], 탭 계정 정보·접근 기록·변경 이력. */
export function UserDetailPanel({
  userId,
  revision,
  onClose,
  onEdit,
  onStop,
  onChanged,
}: {
  userId: string;
  /** 바뀌면 다시 불러온다(수정·중지 뒤). */
  revision: number;
  onClose: () => void;
  onEdit: (account: api.UserAccount) => void;
  onStop: (account: api.UserAccount) => void;
  onChanged: (notice: string) => void;
}) {
  const [tab, setTab] = useState("account");
  const [account, setAccount] = useState<api.UserAccount | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [changes, setChanges] = useState<api.UserChange[] | null>(null);
  const [logs, setLogs] = useState<api.UserPageResult<{
    occurredAt: string;
    action: string;
    target: string;
  }> | null>(null);
  const [logPage, setLogPage] = useState(1);
  const [logError, setLogError] = useState("");
  const [confirm, setConfirm] = useState<"reset" | "resume" | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    void Promise.all([
      api.getCommonUser(userId, controller.signal),
      api.getCommonUserChangeHistory(userId, controller.signal),
    ]).then(
      ([value, history]) => {
        if (controller.signal.aborted) return;
        setAccount(value);
        setChanges(history);
      },
      (failure) => {
        if (!controller.signal.aborted) setError(message(failure));
      },
    );
    return () => controller.abort();
  }, [userId, revision, attempt]);
  useEffect(() => {
    if (tab !== "logs") return;
    const controller = new AbortController();
    setLogs(null);
    setLogError("");
    void api
      .getCommonUserAccessLogs(userId, logPage, 20, controller.signal)
      .then(
        (value) => {
          if (!controller.signal.aborted) setLogs(value);
        },
        (failure) => {
          if (!controller.signal.aborted) setLogError(message(failure));
        },
      );
    return () => controller.abort();
  }, [userId, tab, logPage, revision]);
  const run = async (
    work: (value: api.UserAccount) => Promise<unknown>,
    notice: string,
  ) => {
    if (!account || busy) return;
    setBusy(true);
    setError("");
    try {
      await work(account);
      setConfirm(null);
      setAttempt((value) => value + 1);
      onChanged(notice);
    } catch (failure) {
      setConfirm(null);
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  };
  const stopped = account?.userStatCd === "STOP";
  const actions = account && (
    <>
      <button
        type="button"
        className="ls-btn ls-btn-sm"
        data-testid="user-edit"
        disabled={busy}
        onClick={() => onEdit(account)}
      >
        수정
      </button>
      <button
        type="button"
        className="ls-btn ls-btn-sm"
        data-testid="user-password-reset"
        disabled={busy}
        onClick={() => setConfirm("reset")}
      >
        비밀번호 초기화
      </button>
      {stopped ? (
        <button
          type="button"
          className="ls-btn ls-btn-sm"
          data-testid="user-resume"
          disabled={busy}
          onClick={() => setConfirm("resume")}
        >
          다시 사용
        </button>
      ) : (
        <button
          type="button"
          className="ls-btn ls-btn-sm ls-btn-dt"
          data-testid="user-stop-one"
          disabled={busy}
          onClick={() => onStop(account)}
        >
          사용 중지
        </button>
      )}
    </>
  );
  const accountTab = account ? (
    <>
      <KeyValues
        items={[
          ["아이디", account.userId],
          ["이름", account.name],
          ["이메일", account.email],
          [
            "역할",
            <>
              {roleBadges(account.roleNames)}
              {account.roleNames.length > 1 && (
                <div className="ls-cap" style={{ marginTop: 4 }}>
                  역할이 여러 개면 넓은 쪽(합집합) 권한을 따릅니다.
                </div>
              )}
            </>,
          ],
          [
            "담당 파트",
            account.assignedParts.length
              ? account.assignedParts.join(", ")
              : "–",
          ],
          [
            "담당 프로젝트",
            account.projects.length ? (
              <>
                {account.projects.map((project) => (
                  <span key={project.projectId} className="ls-chip">
                    {project.projectName}
                  </span>
                ))}
                {account.roleCds.includes("PM") && (
                  <div className="ls-cap" style={{ marginTop: 4 }}>
                    이 프로젝트들의 PM으로 표시됩니다.
                  </div>
                )}
              </>
            ) : (
              "–"
            ),
          ],
          [
            "상태",
            <>
              {stateBadge(account.userStatCd)}
              {account.temporaryPassword && (
                <Badge kind="warn">임시 비밀번호</Badge>
              )}
            </>,
          ],
          ["마지막 접속", api.formatDateTime(account.lastAccessAt)],
        ]}
      />
      {account.userStatCd === "LOCK" && (
        <Banner
          kind="warn"
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="user-unlock"
              disabled={busy}
              onClick={() =>
                void run(
                  (value) => api.putCommonUserUnlock(value.userId, value.modDttm),
                  "잠금을 풀었습니다. 틀린 횟수를 0으로 되돌렸습니다.",
                )
              }
            >
              잠금 해제
            </button>
          }
        >
          비밀번호를 5번 틀려 잠겼습니다.
        </Banner>
      )}
      {stopped && (
        <Banner kind="lock">
          사용 중지된 계정입니다. 기록은 그대로 남아 있습니다.
        </Banner>
      )}
    </>
  ) : null;
  const logsTab = (
    <>
      {logError ? (
        <p role="alert">{logError}</p>
      ) : !logs ? (
        <p role="status">접근 기록을 불러오는 중입니다.</p>
      ) : (
        <>
          <div className="ls-twrap">
            <table className="ls-t" data-testid="user-logs">
              <thead>
                <tr>
                  <th>일시</th>
                  <th>작업</th>
                  <th>대상</th>
                </tr>
              </thead>
              <tbody>
                {logs.items.length ? (
                  logs.items.map((log, index) => (
                    <tr key={`${log.occurredAt}-${index}`}>
                      <td>{api.formatDateTime(log.occurredAt)}</td>
                      <td>{log.action}</td>
                      <td>{log.target || "–"}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={3}>아직 접근 기록이 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          {logs.total > logs.pageSize && (
            <ListPager
              page={logPage}
              pageSize={logs.pageSize}
              total={logs.total}
              onPage={setLogPage}
              onPageSize={() => undefined}
            />
          )}
        </>
      )}
      <p className="ls-note">
        로그인·엑셀 다운로드·마감·마감 해제·권한 변경·사용자 등록·급여·개인별
        원가 화면 조회를 남깁니다. 비밀번호 초기화·잠금 해제는 남기지 않습니다.
      </p>
    </>
  );
  const historyTab =
    changes && changes.length ? (
      <div className="ls-twrap">
        <table className="ls-t" data-testid="user-changes">
          <thead>
            <tr>
              <th>일시</th>
              <th>항목</th>
              <th>바꾸기 전</th>
              <th>바꾼 뒤</th>
              <th>처리자</th>
            </tr>
          </thead>
          <tbody>
            {changes.map((change, index) => (
              <tr key={`${change.changedAt}-${index}`}>
                <td>{api.formatDateTime(change.changedAt)}</td>
                <td>{change.item}</td>
                <td>{change.before ?? "–"}</td>
                <td>{change.after ?? "–"}</td>
                <td>{change.changedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    ) : (
      <p className="ls-cap">아직 바꾼 기록이 없습니다.</p>
    );
  return (
    <>
      <DetailPanel
        title={account?.name ?? userId}
        sub={userId}
        actions={actions}
        activeTab={tab}
        onTab={setTab}
        onClose={onClose}
        testId="user-detail"
        tabs={
          account
            ? [
                { key: "account", label: "계정 정보", content: accountTab },
                { key: "logs", label: "접근 기록", content: logsTab },
                {
                  key: "history",
                  label: `변경 이력 (${changes?.length ?? 0})`,
                  content: historyTab,
                },
              ]
            : undefined
        }
      >
        {error ? (
          <div role="alert">
            <p>{error}</p>
            <button
              type="button"
              className="ls-btn"
              onClick={() => setAttempt((value) => value + 1)}
            >
              다시 시도
            </button>
          </div>
        ) : (
          <p role="status">계정 정보를 불러오는 중입니다.</p>
        )}
      </DetailPanel>
      {error && account && (
        <p role="alert" className="app-toast users-error-toast">
          {error}
        </p>
      )}
      {confirm === "reset" && account && (
        <ConfirmDialog
          title="비밀번호를 초기화할까요?"
          description={`${account.email}로 임시 비밀번호를 보내고, 다음 로그인 때 바꾸게 합니다.`}
          proceedLabel="초기화"
          testId="user-reset-confirm"
          onCancel={() => setConfirm(null)}
          onProceed={() =>
            void run(
              (value) =>
                api.putCommonUserPasswordReset(value.userId, value.modDttm),
              "임시 비밀번호를 보냈습니다.",
            )
          }
        />
      )}
      {confirm === "resume" && account && (
        <ConfirmDialog
          title="다시 사용"
          description={`${account.name}님의 계정을 다시 사용합니다. 상태가 「사용」으로 돌아가고 변경 이력에 남습니다.`}
          proceedLabel="다시 사용"
          testId="user-resume-confirm"
          onCancel={() => setConfirm(null)}
          onProceed={() =>
            void run(
              (value) => api.putCommonUserResume(value.userId, value.modDttm),
              `${account.name}님의 계정을 다시 사용합니다.`,
            )
          }
        />
      )}
    </>
  );
}
