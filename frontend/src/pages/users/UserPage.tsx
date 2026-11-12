import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Toast } from "../../shared/feedback/Dialog";
import { useCommonUserOptions } from "../commonops/userOptions";
import {
  useOptionalUserPreferences,
  useScreenPresentation,
} from "../userpreferences/UserPreferencesProvider";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { UserDialog } from "./UserDialog";
import { useUserWorkspace } from "./UserWorkspace";
import * as api from "./users";
import "./users.css";

// SCR-UI-23 / UI-23 — /screens/UI-23. The existing shell owns the title, tabs and PC-only guard.
export function UserPage() {
  const preferences = useOptionalUserPreferences();
  return preferences ? <UserPresentationPage /> : <UserList />;
}
function UserPresentationPage() {
  const { pageSize, dense } = useScreenPresentation();
  return <UserList pageSize={pageSize} dense={dense} />;
}
function UserList({ pageSize, dense }: { pageSize?: number; dense?: boolean }) {
  const { registering, setRegistering } = useUserWorkspace();
  const { state: options, reload: reloadOptions } = useCommonUserOptions();
  const [filters, setFilters] = useState({
    roleCd: "",
    userStatCd: "",
    keyword: "",
  });
  const [query, setQuery] = useState(filters);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<api.UserPageResult<api.UserListItem> | null>(
    null,
  );
  const [failure, setFailure] = useState("");
  const [selected, setSelected] = useState<api.UserListItem[]>([]);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [impact, setImpact] = useState<api.UserImpact | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [notice, setNotice] = useState("");
  const [result, setResult] = useState<{
    unprocessedCount: number;
    unprocessed: { userId: string; reason: string }[];
  } | null>(null);
  const root = useRef<HTMLElement>(null);
  useEffect(() => {
    setPage(1);
  }, [pageSize]);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setFailure("");
    setSelected([]);
    void api
      .getCommonUsers({ ...query, page, pageSize }, controller.signal)
      .then(
        (value) => {
          if (!controller.signal.aborted) setData(value);
        },
        (error) => {
          if (!controller.signal.aborted)
            setFailure(
              error instanceof Error
                ? error.message
                : "사용자 목록을 불러오지 못했습니다.",
            );
        },
      );
    return () => controller.abort();
  }, [query, page, pageSize, revision]);
  const search = () => {
    setPage(1);
    setQuery({ ...filters });
  };
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
    !registering && !detailId && !impact,
  );
  const showError = (error: unknown) =>
    setFailure(
      error instanceof Error
        ? error.message
        : "처리하지 못했습니다. 다시 시도해 주세요.",
    );
  const prepareStop = async () => {
    if (busyRef.current || !selected.length) return;
    busyRef.current = true;
    setBusy(true);
    setFailure("");
    try {
      setImpact(
        await api.getCommonUserChangeImpact(
          selected.map((item) => item.userId),
          "STOP",
        ),
      );
    } catch (error) {
      showError(error);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const stop = async () => {
    if (!impact || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      const value = await api.putCommonUserStop(
        selected.map(({ userId, modDttm }) => ({ userId, modDttm })),
        impact.projects.map((project) => project.projectId),
      );
      setResult(value);
      setNotice(`${value.processedUserIds.length}건 처리했습니다.`);
      setRevision((value) => value + 1);
      setImpact(null);
    } catch (error) {
      setImpact(null);
      showError(error);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  return (
    <section
      ref={root}
      data-testid="user-page"
      aria-label="사용자"
      className={`users-page${dense ? " users-dense" : ""}`}
    >
      <form
        className="users-search"
        onSubmit={(event) => {
          event.preventDefault();
          search();
        }}
      >
        <label>
          역할
          <select
            data-testid="user-role-filter"
            value={filters.roleCd}
            disabled={options.status !== "ready"}
            onChange={(event) =>
              setFilters({ ...filters, roleCd: event.target.value })
            }
          >
            <option value="">전체</option>
            {options.status === "ready" &&
              options.data.roles.map((role) => (
                <option key={role.roleCd} value={role.roleCd}>
                  {role.roleName}
                </option>
              ))}
          </select>
        </label>
        <label>
          상태
          <select
            data-testid="user-state-filter"
            value={filters.userStatCd}
            disabled={options.status !== "ready"}
            onChange={(event) =>
              setFilters({ ...filters, userStatCd: event.target.value })
            }
          >
            <option value="">전체</option>
            {options.status === "ready" &&
              options.data.userStates.map((state) => (
                <option key={state.code} value={state.code}>
                  {state.name}
                </option>
              ))}
          </select>
        </label>
        <label>
          이름·아이디
          <input
            data-testid="user-keyword-filter"
            value={filters.keyword}
            onChange={(event) =>
              setFilters({ ...filters, keyword: event.target.value })
            }
          />
        </label>
        <button type="submit" data-testid="user-search">
          조회
        </button>
      </form>
      {options.status === "error" && (
        <div role="alert">
          선택 목록을 불러오지 못했습니다.
          <button
            type="button"
            data-testid="user-options-reload"
            onClick={reloadOptions}
          >
            다시 불러오기
          </button>
        </div>
      )}
      {failure && (
        <div role="alert">
          <p>{failure}</p>
          <button
            type="button"
            data-testid="user-list-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            재시도
          </button>
        </div>
      )}
      <button
        type="button"
        className="app-danger"
        data-testid="user-stop-open"
        disabled={busy || !selected.length}
        onClick={() => void prepareStop()}
      >
        사용 중지
      </button>
      {!data && !failure ? (
        <p role="status">목록을 불러오는 중입니다.</p>
      ) : data && !data.items.length ? (
        <p>조회된 사용자가 없습니다.</p>
      ) : (
        data && (
          <div className="users-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>선택</th>
                  <th>아이디</th>
                  <th>이름</th>
                  <th>역할</th>
                  <th>담당 파트</th>
                  <th>상태</th>
                  <th>마지막 접속</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.userId} data-testid={`user-row-${item.userId}`}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={`${item.userId} 선택`}
                        data-testid={`user-select-${item.userId}`}
                        disabled={busy}
                        checked={selected.some(
                          (value) => value.userId === item.userId,
                        )}
                        onChange={(event) =>
                          setSelected((current) =>
                            event.target.checked
                              ? [...current, item]
                              : current.filter(
                                  (value) => value.userId !== item.userId,
                                ),
                          )
                        }
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        data-testid={`user-detail-${item.userId}`}
                        disabled={options.status !== "ready"}
                        onClick={() => setDetailId(item.userId)}
                      >
                        {item.userId}
                      </button>
                    </td>
                    <td>{item.name}</td>
                    <td>{item.roleNames.join(", ")}</td>
                    <td>{item.assignedParts.join(", ") || "–"}</td>
                    <td>
                      {options.status === "ready"
                        ? (options.data.userStates.find(
                            (state) => state.code === item.userStatCd,
                          )?.name ?? item.userStatCd)
                        : item.userStatCd}
                    </td>
                    <td>{item.lastAccessAt ?? "–"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
      <div className="app-actions">
        <button
          type="button"
          data-testid="user-list-previous"
          disabled={!data || page <= 1}
          onClick={() => setPage(page - 1)}
        >
          이전
        </button>
        {data && (
          <span>
            {page} 페이지 / 총 {data.total}건
          </span>
        )}
        <button
          type="button"
          data-testid="user-list-next"
          disabled={!data || page * data.pageSize >= data.total}
          onClick={() => setPage(page + 1)}
        >
          다음
        </button>
      </div>
      {result && (
        <section data-testid="user-stop-result">
          <p>미처리 {result.unprocessedCount}건</p>
          <ul>
            {result.unprocessed.map((item) => (
              <li
                key={item.userId}
                data-testid={`user-rejected-${item.userId}`}
              >
                {item.userId}: {item.reason}
              </li>
            ))}
          </ul>
        </section>
      )}
      {notice && (
        <Toast
          message={notice}
          onDismiss={() => setNotice("")}
          testId="user-toast"
        />
      )}
      {(registering || detailId) &&
        (options.status === "ready" ? (
          <UserDialog
            key={registering ? "create" : detailId!}
            userId={registering ? null : detailId}
            options={options.data}
            pageSize={pageSize}
            onClose={() => {
              setRegistering(false);
              setDetailId(null);
            }}
            onSaved={(value) => {
              setNotice(value);
              setRevision((current) => current + 1);
            }}
          />
        ) : (
          <div role="status">등록 선택 목록을 불러와 주세요.</div>
        ))}
      {impact && (
        <ConfirmDialog
          title="선택한 계정의 사용을 중지할까요?"
          testId="user-stop-confirm"
          destructive
          proceedLabel="계속"
          description={`선택 ${selected.length}명, 처리 불가 ${impact.blockedUsers.length}명. ${impact.blockedUsers.map((item) => `${item.userId}: ${item.reason}`).join(" / ")}${impact.projectCount ? ` 담당 프로젝트 ${impact.projectCount}건의 PM이 비게 됩니다: ${impact.projects.map((item) => item.projectName).join(", ")}` : ""}`}
          onCancel={() => {
            if (!busyRef.current) setImpact(null);
          }}
          onProceed={() => void stop()}
        />
      )}
    </section>
  );
}
