import { useEffect, useMemo, useRef, useState } from "react";
import { ConfirmDialog, Toast } from "../../shared/feedback/Dialog";
import {
  downloadFile,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { useCommonUserOptions } from "../commonops/userOptions";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { UserDetailPanel, roleBadges, stateBadge } from "./UserDetailPanel";
import { UserDialog } from "./UserDialog";
import { useUserWorkspace } from "./UserWorkspace";
import * as api from "./users";
import "./users.css";

const SCREEN = "UI-23";
const emptyFilters = { roleCd: "", userStatCd: "", keyword: "" };
const message = (failure: unknown) =>
  failure instanceof Error
    ? failure.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

const columns: ListColumn<api.UserListItem>[] = [
  { key: "userId", label: "아이디" },
  { key: "name", label: "이름", required: true },
  { key: "role", label: "역할", render: (row) => roleBadges(row.roleNames) },
  {
    key: "part",
    label: "담당 파트",
    sortable: false,
    render: (row) =>
      row.assignedParts.length ? row.assignedParts.join(", ") : "–",
  },
  { key: "state", label: "상태", render: (row) => stateBadge(row.userStatCd) },
  {
    key: "last",
    label: "마지막 접속",
    render: (row) => api.formatDateTime(row.lastAccessAt),
  },
];

/** 시스템 › 사용자(UI-23). 목록 틀은 shared/list(SFR-010·011), 화면 모양은 시안 v1.8. */
export function UserPage() {
  const workspace = useUserWorkspace();
  const { registering, setRegistering, includeStopped } = workspace;
  const { state: options, reload: reloadOptions } = useCommonUserOptions();
  const presentation = useListPresentation();
  const cols = useListColumns(SCREEN, columns);
  const [filters, setFilters] = useState(emptyFilters);
  const [query, setQuery] = useState(emptyFilters);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<api.UserPageResult<api.UserListItem> | null>(
    null,
  );
  const [failure, setFailure] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [allResults, setAllResults] = useState(false);
  const known = useRef(new Map<string, api.UserListItem>());
  const [detailId, setDetailId] = useState<string | null>(null);
  const [editing, setEditing] = useState<api.UserAccount | null>(null);
  const [stopTarget, setStopTarget] = useState<{
    users: api.UserListItem[];
    impact: api.UserImpact;
  } | null>(null);
  const [resumeTarget, setResumeTarget] = useState<api.UserListItem[] | null>(
    null,
  );
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [notice, setNotice] = useState("");
  const [highlight, setHighlight] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const root = useRef<HTMLElement>(null);
  const listQuery = useMemo<api.UserListQuery>(
    () => ({
      ...query,
      includeStopped,
      sort: sort?.key,
      dir: sort?.dir,
    }),
    [query, includeStopped, sort],
  );
  useEffect(() => {
    setPage(1);
  }, [presentation.pageSize, includeStopped]);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setFailure("");
    void api
      .getCommonUsers(
        { ...listQuery, page, pageSize: presentation.pageSize },
        controller.signal,
      )
      .then(
        (value) => {
          if (controller.signal.aborted) return;
          value.items.forEach((item) => known.current.set(item.userId, item));
          setData(value);
        },
        (error) => {
          if (!controller.signal.aborted) setFailure(message(error));
        },
      );
    return () => controller.abort();
  }, [listQuery, page, presentation.pageSize, revision]);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(() => setHighlight(null), 6000);
    return () => window.clearTimeout(timer);
  }, [highlight]);
  const clearSelection = () => {
    setSelected([]);
    setAllResults(false);
  };
  const search = (next = filters) => {
    setPage(1);
    clearSelection();
    setQuery({ ...next, keyword: next.keyword.trim() });
  };
  const reset = () => {
    setFilters(emptyFilters);
    search(emptyFilters);
  };
  const overlay = registering || !!detailId || !!editing || !!stopTarget || !!resumeTarget;
  usePreferenceBusinessShortcuts(
    {
      onSearch: () => {
        if (!root.current?.closest("[hidden]")) search();
      },
      onPreviousPage: () => {
        if (!root.current?.closest("[hidden]") && page > 1) setPage(page - 1);
      },
      onNextPage: () => {
        if (
          !root.current?.closest("[hidden]") &&
          data &&
          page * data.pageSize < data.total
        )
          setPage(page + 1);
      },
    },
    !overlay,
  );
  /** 선택한 계정(「조회된 N건 모두 선택」이면 같은 조건의 전체). */
  const selectedUsers = async () => {
    if (!allResults)
      return selected
        .map((key) => known.current.get(key))
        .filter((item): item is api.UserListItem => !!item);
    const all: api.UserListItem[] = [];
    for (let next = 1; ; next++) {
      const value = await api.getCommonUsers({
        ...listQuery,
        page: next,
        pageSize: 100,
      });
      all.push(...value.items);
      if (next * 100 >= value.total) break;
    }
    return all;
  };
  const refresh = (text: string) => {
    setNotice(text);
    setRevision((value) => value + 1);
  };
  const prepareStop = async (users: api.UserListItem[] | null) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setFailure("");
    try {
      const targets = (users ?? (await selectedUsers())).filter(
        (user) => user.userStatCd !== "STOP",
      );
      if (!targets.length) {
        setNotice("이미 사용 중지된 계정입니다.");
        return;
      }
      const impact = await api.getCommonUserChangeImpact(
        targets.map((item) => item.userId),
        "STOP",
      );
      const blocked = new Set(impact.blockedUsers.map((item) => item.userId));
      if (targets.every((item) => blocked.has(item.userId))) {
        setNotice(impact.blockedUsers.map((item) => item.reason).join(" "));
        return;
      }
      setStopTarget({ users: targets, impact });
    } catch (error) {
      setFailure(message(error));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const stop = async () => {
    if (!stopTarget || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      const blocked = new Set(
        stopTarget.impact.blockedUsers.map((item) => item.userId),
      );
      const value = await api.putCommonUserStop(
        stopTarget.users
          .filter((item) => !blocked.has(item.userId))
          .map(({ userId, modDttm }) => ({ userId, modDttm })),
        stopTarget.impact.projects.map((project) => project.projectId),
      );
      const skipped = stopTarget.impact.blockedUsers.length + value.unprocessedCount;
      const reasons = [
        ...stopTarget.impact.blockedUsers,
        ...value.unprocessed,
      ].map((item) => item.reason);
      clearSelection();
      setStopTarget(null);
      refresh(
        `${value.processedUserIds.length}건의 사용을 중지했습니다.${skipped ? ` ${skipped}건은 중지하지 못했습니다. ${[...new Set(reasons)].join(" ")}` : ""}`,
      );
    } catch (error) {
      setStopTarget(null);
      setFailure(message(error));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const prepareResume = async () => {
    if (busyRef.current) return;
    const targets = (await selectedUsers()).filter(
      (item) => item.userStatCd === "STOP",
    );
    if (!targets.length) {
      setNotice("중지된 계정만 다시 사용할 수 있습니다.");
      return;
    }
    setResumeTarget(targets);
  };
  const resume = async () => {
    if (!resumeTarget || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    let done = 0;
    const errors: string[] = [];
    for (const user of resumeTarget) {
      try {
        await api.putCommonUserResume(user.userId, user.modDttm);
        done++;
      } catch (error) {
        errors.push(`${user.name}: ${message(error)}`);
      }
    }
    clearSelection();
    setResumeTarget(null);
    busyRef.current = false;
    setBusy(false);
    refresh(
      `${done}건의 계정을 다시 사용합니다.${errors.length ? ` ${errors.length}건은 처리하지 못했습니다. ${errors.join(" ")}` : ""}`,
    );
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      await downloadFile(
        api.commonUsersExcelUrl(
          listQuery,
          cols.visible.map((column) => column.key),
        ),
        "사용자.xlsx",
      );
      setNotice(`엑셀 파일을 내려받았습니다. (${data?.total ?? 0}건)`);
    } catch (error) {
      setFailure(message(error));
    } finally {
      setDownloading(false);
    }
  };
  const ready = options.status === "ready";
  return (
    <section
      ref={root}
      data-testid="user-page"
      aria-label="사용자"
      className="users-page screen-body"
    >
      <ListFilters onSearch={() => search()} onReset={reset}>
        <ListFilter label="역할" htmlFor="users-role">
          <select
            id="users-role"
            className="ls-in"
            data-testid="user-role-filter"
            value={filters.roleCd}
            disabled={!ready}
            onChange={(event) =>
              setFilters({ ...filters, roleCd: event.target.value })
            }
          >
            <option value="">전체</option>
            {ready &&
              options.data.roles.map((role) => (
                <option key={role.roleCd} value={role.roleCd}>
                  {role.roleName}
                </option>
              ))}
          </select>
        </ListFilter>
        <ListFilter label="상태" htmlFor="users-state">
          <select
            id="users-state"
            className="ls-in"
            data-testid="user-state-filter"
            value={filters.userStatCd}
            disabled={!ready}
            onChange={(event) =>
              setFilters({ ...filters, userStatCd: event.target.value })
            }
          >
            <option value="">전체</option>
            {ready &&
              options.data.userStates.map((state) => (
                <option key={state.code} value={state.code}>
                  {state.name}
                </option>
              ))}
          </select>
        </ListFilter>
        <ListFilter label="이름" htmlFor="users-keyword">
          <input
            id="users-keyword"
            className="ls-in users-keyword"
            data-testid="user-keyword-filter"
            placeholder="이름 또는 아이디"
            value={filters.keyword}
            onChange={(event) =>
              setFilters({ ...filters, keyword: event.target.value })
            }
          />
        </ListFilter>
      </ListFilters>
      {options.status === "error" && (
        <div role="alert">
          선택 목록을 불러오지 못했습니다.{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="user-options-reload"
            onClick={reloadOptions}
          >
            다시 불러오기
          </button>
        </div>
      )}
      <ListView
        screenId={SCREEN}
        testId="user-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={data ? data.items : failure ? [] : null}
        rowKey={(row) => row.userId}
        total={data?.total ?? 0}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={presentation.setPageSize}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={(value) => {
          setSort(value);
          setPage(1);
        }}
        selectable
        selected={selected}
        onSelect={(keys) => {
          setSelected(keys);
          setAllResults(false);
        }}
        allResultsSelected={allResults}
        onSelectAllResults={() => setAllResults(true)}
        bulkActions={[
          {
            key: "stop",
            label: "사용 중지",
            danger: true,
            onClick: () => void prepareStop(null),
          },
          {
            key: "resume",
            label: "다시 사용",
            onClick: () => void prepareResume(),
          },
        ]}
        onRowClick={(row) => setDetailId(row.userId)}
        highlightKey={highlight}
        onDownload={() => void download()}
        downloading={downloading}
        onResetFilters={reset}
        error={failure && !data ? failure : undefined}
        onRetry={() => setRevision((value) => value + 1)}
      />
      {failure && data && (
        <p role="alert" className="field-error" data-testid="user-failure">
          {failure}
        </p>
      )}
      {notice && (
        <Toast
          message={notice}
          onDismiss={() => setNotice("")}
          testId="user-toast"
        />
      )}
      {detailId && (
        <UserDetailPanel
          userId={detailId}
          revision={revision}
          onClose={() => setDetailId(null)}
          onEdit={(account) => setEditing(account)}
          onStop={(account) =>
            void prepareStop([
              {
                userId: account.userId,
                name: account.name,
                roleCds: account.roleCds,
                roleNames: account.roleNames,
                assignedParts: account.assignedParts,
                userStatCd: account.userStatCd,
                lastAccessAt: account.lastAccessAt,
                modDttm: account.modDttm,
              },
            ])
          }
          onChanged={refresh}
        />
      )}
      {(registering || editing) &&
        (ready ? (
          <UserDialog
            key={editing ? editing.userId : "create"}
            account={editing}
            options={options.data}
            onClose={() => {
              setRegistering(false);
              setEditing(null);
            }}
            onSaved={(text, account) => {
              if (!editing) setHighlight(account.userId);
              setRegistering(false);
              setEditing(null);
              reloadOptions();
              refresh(text);
            }}
          />
        ) : (
          <div role="status">선택 목록을 불러오는 중입니다.</div>
        ))}
      {stopTarget && (
        <ConfirmDialog
          title={`${stopTarget.users.length === 1 ? `${stopTarget.users[0].name}님의` : `선택한 ${stopTarget.users.length}건의`} 사용을 중지할까요?`}
          testId="user-stop-confirm"
          destructive
          proceedLabel="사용 중지"
          description={`삭제가 아니며 기록은 그대로 남습니다. 다시 쓰려면 상세 창의 [다시 사용]을 누릅니다.${stopTarget.impact.blockedUsers.length ? ` ${stopTarget.impact.blockedUsers.length}건은 중지할 수 없습니다: ${stopTarget.impact.blockedUsers.map((item) => item.reason).join(" ")}` : ""}${stopTarget.impact.projectCount ? ` 담당 프로젝트 ${stopTarget.impact.projectCount}건의 PM이 비게 됩니다: ${stopTarget.impact.projects.map((item) => item.projectName).join(", ")}` : ""}`}
          onCancel={() => {
            if (!busyRef.current) setStopTarget(null);
          }}
          onProceed={() => void stop()}
        />
      )}
      {resumeTarget && (
        <ConfirmDialog
          title="다시 사용"
          testId="user-resume-bulk-confirm"
          proceedLabel="다시 사용"
          description={`선택한 ${resumeTarget.length}건의 계정을 다시 사용합니다. 상태가 「사용」으로 돌아가고 변경 이력에 남습니다.`}
          onCancel={() => {
            if (!busyRef.current) setResumeTarget(null);
          }}
          onProceed={() => void resume()}
        />
      )}
    </section>
  );
}
