import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import {
  LIST_PAGE_SIZES,
  listMessages,
  type ListColumn,
  type ListSort,
} from "./listTypes";
import "./list.css";

/** SFR-010-01 조건 줄: 조건 칸들, [초기화], [조회](Enter로도 적용). */
export function ListFilters({
  children,
  onSearch,
  onReset,
  testId = "list-filters",
}: {
  children: ReactNode;
  onSearch: () => void;
  onReset: () => void;
  testId?: string;
}) {
  return (
    <form
      className="ls-filters"
      data-testid={testId}
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        onSearch();
      }}
    >
      <div className="ls-filter-fields">{children}</div>
      <div className="ls-filter-acts">
        <button
          type="button"
          className="ls-btn"
          data-testid="list-reset"
          onClick={onReset}
        >
          초기화
        </button>
        <button
          type="submit"
          className="ls-btn ls-btn-p"
          data-testid="list-search"
        >
          조회
        </button>
      </div>
    </form>
  );
}

/** 조건 칸 하나: 라벨 + 입력. */
export function ListFilter({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <div className="ls-filter" data-testid={`list-filter-${htmlFor}`}>
      <label htmlFor={htmlFor}>{label}</label>
      {children}
    </div>
  );
}

export interface BulkAction {
  key: string;
  label: string;
  danger?: boolean;
  onClick: () => void;
}

interface ListViewProps<T> {
  /** 화면 번호(열 설정 저장 키). */
  screenId: string;
  columns: ListColumn<T>[];
  visibleColumns: ListColumn<T>[];
  hiddenColumns: string[];
  onToggleColumn: (key: string) => void;
  onResetColumns: () => void;
  rows: T[] | null;
  rowKey: (row: T) => string;
  total: number;
  page: number;
  pageSize: number;
  onPage: (page: number) => void;
  onPageSize: (size: number) => void;
  dense: boolean;
  onDense: (dense: boolean) => void;
  sort: ListSort | null;
  onSort: (sort: ListSort) => void;
  /** 체크 칸을 둘지(일괄 처리가 있는 목록). */
  selectable?: boolean;
  selected?: string[];
  onSelect?: (keys: string[]) => void;
  /** 조회된 전체 결과 선택(SFR-010-02). 있으면 [조회된 N건 모두 선택]을 보인다. */
  allResultsSelected?: boolean;
  onSelectAllResults?: () => void;
  bulkActions?: BulkAction[];
  onRowClick?: (row: T) => void;
  highlightKey?: string | null;
  onDownload?: () => void;
  downloading?: boolean;
  onResetFilters?: () => void;
  /** 열 설정 단추를 보일지(기본 보임). 시안에 열 설정이 없는 목록은 false. */
  columnSettings?: boolean;
  error?: string;
  onRetry?: () => void;
  testId?: string;
}

/** SFR-010 목록 공통: 도구 줄, 열 설정, 좁게 보기, 엑셀, 정렬, 선택, 페이지. */
export function ListView<T>(props: ListViewProps<T>) {
  const {
    columns,
    visibleColumns,
    hiddenColumns,
    rows,
    rowKey,
    total,
    selectable = false,
    selected = [],
    onSelect = () => undefined,
    bulkActions = [],
    dense,
    testId = "list-view",
  } = props;
  const [columnsOpen, setColumnsOpen] = useState(false);
  const columnAnchor = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!columnsOpen) return;
    const close = (event: MouseEvent) => {
      if (!columnAnchor.current?.contains(event.target as Node))
        setColumnsOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setColumnsOpen(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", escape);
    };
  }, [columnsOpen]);
  const pageKeys = (rows ?? []).map(rowKey);
  const pageAllOn =
    pageKeys.length > 0 && pageKeys.every((key) => selected.includes(key));
  const count = props.allResultsSelected ? total : selected.length;
  const sortBy = (column: ListColumn<T>) => {
    if (column.sortable === false) return;
    const dir =
      props.sort?.key === column.key && props.sort.dir === "asc"
        ? "desc"
        : "asc";
    props.onSort({ key: column.key, dir });
  };
  return (
    <div className="ls-root" data-testid={testId}>
      <div className="ls-tbar">
        <div className="ls-tl" data-testid="list-count">
          {count ? (
            <>
              <strong>{count.toLocaleString("ko-KR")}건 선택</strong>
              {pageAllOn &&
                !props.allResultsSelected &&
                total > selected.length &&
                props.onSelectAllResults && (
                  <button
                    type="button"
                    className="ls-btn ls-btn-t"
                    data-testid="list-select-all-results"
                    onClick={props.onSelectAllResults}
                  >
                    조회된 {total.toLocaleString("ko-KR")}건 모두 선택
                  </button>
                )}
              <button
                type="button"
                className="ls-btn ls-btn-t"
                data-testid="list-select-none"
                onClick={() => onSelect([])}
              >
                선택 해제
              </button>
              {bulkActions.map((action) => (
                <button
                  key={action.key}
                  type="button"
                  className={`ls-btn ls-btn-sm${action.danger ? " ls-btn-dt" : ""}`}
                  data-testid={`list-bulk-${action.key}`}
                  onClick={action.onClick}
                >
                  {action.label}
                </button>
              ))}
            </>
          ) : (
            <span>
              총 <strong>{total.toLocaleString("ko-KR")}</strong>건
            </span>
          )}
        </div>
        <div className="ls-tr">
          {props.columnSettings !== false && (
          <div className="ls-pop-anchor" ref={columnAnchor}>
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="list-columns"
              aria-expanded={columnsOpen}
              onClick={() => setColumnsOpen(!columnsOpen)}
            >
              <ColumnsIcon /> 열 설정
            </button>
            {columnsOpen && (
              <div
                className="ls-pop"
                role="dialog"
                aria-label="열 설정"
                data-testid="list-columns-pop"
              >
                <div className="ls-pop-h">보일 열</div>
                <div className="ls-pop-b">
                  {columns.map((column) => (
                    <label
                      key={column.key}
                      data-testid={`list-column-label-${column.key}`}
                    >
                      <input
                        type="checkbox"
                        data-testid={`list-column-${column.key}`}
                        checked={!hiddenColumns.includes(column.key)}
                        disabled={column.required}
                        onChange={() => props.onToggleColumn(column.key)}
                      />
                      {column.label}
                      {column.required && (
                        <span className="ls-cap"> (필수)</span>
                      )}
                    </label>
                  ))}
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm"
                    data-testid="list-columns-default"
                    onClick={props.onResetColumns}
                  >
                    기본값으로
                  </button>
                </div>
              </div>
            )}
          </div>
          )}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="list-density"
            aria-pressed={dense}
            onClick={() => props.onDense(!dense)}
          >
            <DensityIcon /> {dense ? "넓게 보기" : "좁게 보기"}
          </button>
          {props.onDownload && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="list-download"
              disabled={props.downloading}
              onClick={props.onDownload}
            >
              <DownloadIcon /> 엑셀 다운로드
            </button>
          )}
        </div>
      </div>
      {props.error ? (
        <div className="ls-empty" role="alert" data-testid="list-error">
          <p>{props.error}</p>
          {props.onRetry && (
            <button
              type="button"
              className="ls-btn"
              data-testid="list-retry"
              onClick={props.onRetry}
            >
              다시 시도
            </button>
          )}
        </div>
      ) : (
        <div className="ls-twrap">
          <table className={`ls-t${dense ? " ls-dense" : ""}`}>
            <thead>
              <tr>
                {selectable && (
                  <th className="ls-chk">
                    <input
                      type="checkbox"
                      aria-label="이 페이지 모두 선택"
                      data-testid="list-select-page"
                      checked={pageAllOn}
                      onChange={() =>
                        onSelect(
                          pageAllOn
                            ? selected.filter((key) => !pageKeys.includes(key))
                            : [...new Set([...selected, ...pageKeys])],
                        )
                      }
                    />
                  </th>
                )}
                {visibleColumns.map((column) => {
                  const active = props.sort?.key === column.key;
                  const sortable = column.sortable !== false;
                  return (
                    <th
                      key={column.key}
                      className={`ls-${column.align ?? "left"}${sortable ? " ls-sortable" : ""}`}
                      data-testid={`list-head-${column.key}`}
                      aria-sort={
                        active
                          ? props.sort!.dir === "asc"
                            ? "ascending"
                            : "descending"
                          : sortable
                            ? "none"
                            : undefined
                      }
                      tabIndex={sortable ? 0 : undefined}
                      onClick={() => sortBy(column)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          event.stopPropagation();
                          sortBy(column);
                        }
                      }}
                    >
                      {column.label}
                      {column.calc && (
                        <span className="ls-cap" title={column.calc}>
                          {" "}
                          ⓘ
                        </span>
                      )}
                      {active && (props.sort!.dir === "asc" ? " ▲" : " ▼")}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {rows === null ? (
                <tr>
                  <td colSpan={visibleColumns.length + (selectable ? 1 : 0)}>
                    <div className="ls-empty" role="status">
                      {listMessages.loading}
                    </div>
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={visibleColumns.length + (selectable ? 1 : 0)}>
                    <div className="ls-empty" data-testid="list-empty">
                      <p>{listMessages.empty}</p>
                      {props.onResetFilters && (
                        <button
                          type="button"
                          className="ls-btn"
                          data-testid="list-empty-reset"
                          onClick={props.onResetFilters}
                        >
                          조건 초기화
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                rows.map((row) => {
                  const key = rowKey(row);
                  const on = props.allResultsSelected || selected.includes(key);
                  return (
                    <tr
                      key={key}
                      data-testid={`list-row-${key}`}
                      className={`${on ? "ls-sel" : ""}${props.highlightKey === key ? " ls-hl" : ""}`}
                      tabIndex={props.onRowClick ? 0 : undefined}
                      onClick={() => props.onRowClick?.(row)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" && props.onRowClick) {
                          event.preventDefault();
                          event.stopPropagation();
                          props.onRowClick(row);
                        }
                      }}
                    >
                      {selectable && (
                        <td
                          className="ls-chk"
                          data-testid={`list-row-check-cell-${key}`}
                          onClick={(event) => event.stopPropagation()}
                        >
                          <input
                            type="checkbox"
                            aria-label="선택"
                            data-testid={`list-select-${key}`}
                            checked={on}
                            onChange={() =>
                              onSelect(
                                selected.includes(key)
                                  ? selected.filter((item) => item !== key)
                                  : [...selected, key],
                              )
                            }
                          />
                        </td>
                      )}
                      {visibleColumns.map((column) => (
                        <td
                          key={column.key}
                          className={`ls-${column.align ?? "left"}`}
                          data-testid={`list-cell-${key}-${column.key}`}
                        >
                          {column.render
                            ? column.render(row)
                            : String(
                                (row as Record<string, unknown>)[column.key] ??
                                  "–",
                              )}
                        </td>
                      ))}
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      )}
      <ListPager
        page={props.page}
        pageSize={props.pageSize}
        total={total}
        onPage={props.onPage}
        onPageSize={props.onPageSize}
      />
    </div>
  );
}

/** SFR-010-04 페이지: 번호 10개씩, « ‹ › », “41-50 / 120건”, 한 화면 건수 20/50/100. */
export function ListPager({
  page,
  pageSize,
  total,
  onPage,
  onPageSize,
}: {
  page: number;
  pageSize: number;
  total: number;
  onPage: (page: number) => void;
  onPageSize: (size: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const from = total ? (page - 1) * pageSize + 1 : 0;
  const to = Math.min(total, page * pageSize);
  const start = Math.floor((page - 1) / 10) * 10 + 1;
  const end = Math.min(pages, start + 9);
  const numbers = [];
  for (let value = start; value <= end; value++) numbers.push(value);
  const nav = (target: number, label: string, text: string, disabled: boolean) => (
    <button
      type="button"
      className="ls-pg"
      aria-label={label}
      data-testid={`list-page-${label}`}
      disabled={disabled}
      onClick={() => onPage(target)}
    >
      {text}
    </button>
  );
  return (
    <div className="ls-pager" data-testid="list-pager">
      <span className="ls-range" data-testid="list-range">
        {from.toLocaleString("ko-KR")}-{to.toLocaleString("ko-KR")} /{" "}
        {total.toLocaleString("ko-KR")}건
      </span>
      <div className="ls-pages">
        {nav(1, "첫 페이지", "«", page <= 1)}
        {nav(page - 1, "이전 페이지", "‹", page <= 1)}
        {numbers.map((value) => (
          <button
            key={value}
            type="button"
            className={`ls-pg${value === page ? " ls-on" : ""}`}
            data-testid={`list-page-${value}`}
            aria-current={value === page ? "page" : undefined}
            onClick={() => onPage(value)}
          >
            {value}
          </button>
        ))}
        {nav(page + 1, "다음 페이지", "›", page >= pages)}
        {nav(pages, "마지막 페이지", "»", page >= pages)}
      </div>
      <label className="ls-size">
        <span className="ls-sr">한 화면 건수</span>
        <select
          className="ls-in ls-in-sm"
          data-testid="list-page-size"
          value={pageSize}
          onChange={(event) => onPageSize(Number(event.target.value))}
        >
          {LIST_PAGE_SIZES.map((size) => (
            <option key={size} value={size} data-testid={`list-size-${size}`}>
              {size}건씩
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

/** 상태·역할 표시용 작은 배지. kind는 ok·warn·bad·info·lock·neutral. */
export function Badge({
  children,
  kind = "neutral",
}: {
  children: ReactNode;
  kind?: "ok" | "warn" | "bad" | "info" | "lock" | "neutral";
}) {
  return <span className={`ls-b ls-b-${kind}`}>{children}</span>;
}

const svg = {
  width: 16,
  height: 16,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const;
function ColumnsIcon() {
  return (
    <svg {...svg}>
      <path d="M4 4h16v16H4zM10 4v16M16 4v16" />
    </svg>
  );
}
function DensityIcon() {
  return (
    <svg {...svg}>
      <path d="M4 6h16M4 10h16M4 14h16M4 18h16" />
    </svg>
  );
}
function DownloadIcon() {
  return (
    <svg {...svg}>
      <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
    </svg>
  );
}
