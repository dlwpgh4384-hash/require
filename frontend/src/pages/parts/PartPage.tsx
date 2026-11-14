import { useEffect, useMemo, useRef, useState } from "react";
import { Toast } from "../../shared/feedback/Dialog";
import {
  Banner,
  DetailPanel,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { useClientPartWorkspace } from "./ClientPartWorkspace";
import { PartCreateDialog } from "./PartCreateDialog";
import { PartStandardChangeDialog } from "./PartStandardChangeDialog";
import { PartRenameDialog } from "./PartRenameDialog";
import { PartStopDialog } from "./PartStopDialog";
import { PartStandardsPanel } from "./PartStandardsPanel";
import { PartChangeHistoryPanel } from "./PartChangeHistoryPanel";
import * as api from "./parts";
import "./parts.css";

const columns: ListColumn<api.Part>[] = [
  { key: "partName", label: "파트명", required: true },
  { key: "leaderName", label: "파트장" },
  {
    key: "distributionRate",
    label: "배분율",
    render: (row) => `${row.distributionRate}%`,
  },
  {
    key: "salesExpense",
    label: "공통관리비 (1인·월)",
    render: (row) => `${api.partMoney(row.salesExpense)}원`,
  },
  { key: "effectiveMonth", label: "적용 시작" },
  {
    key: "scheduledChange",
    label: "예정 변경",
    render: (row) => row.scheduledChange ?? "–",
  },
  { key: "personCount", label: "소속 인력" },
  { key: "projectCount", label: "진행 프로젝트" },
  {
    key: "useYn",
    label: "사용 여부",
    render: (row) => (
      <span
        className={`part-state part-state-${row.useYn === "Y" ? "current" : "past"}`}
      >
        {row.useYn === "Y" ? "사용" : "중지"}
      </span>
    ),
  },
];

/** UI-15 /screens/UI-15 영업 파트 탭. Shell registration owns the existing route. */
export function PartPage() {
  const workspace = useClientPartWorkspace();
  const presentation = useListPresentation();
  const cols = useListColumns("UI-15-parts", columns);
  const [data, setData] = useState<api.Part[] | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [highlight, setHighlight] = useState<string | null>(null);
  const [editing, setEditing] = useState<
    "standards" | "rename" | "stop" | null
  >(null);
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState("");
  const [tab, setTab] = useState("standards");
  const pendingDetail = useRef<string | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    api.getCommonParts(controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return;
        setData(result.items);
        if (pendingDetail.current) {
          setDetailId(pendingDetail.current);
          pendingDetail.current = null;
        }
      },
      (failure) => {
        if (!controller.signal.aborted) {
          setData(null);
          setError(api.partErrorMessage(failure));
        }
      },
    );
    return () => controller.abort();
  }, [revision]);
  useEffect(() => {
    setPage(1);
  }, [presentation.pageSize]);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(() => setHighlight(null), 6000);
    return () => window.clearTimeout(timer);
  }, [highlight]);
  const sorted = useMemo(() => {
    if (!data) return null;
    if (!sort) return data;
    const key = sort.key as keyof api.Part;
    return [...data].sort((left, right) => {
      const a = left[key];
      const b = right[key];
      const comparison =
        typeof a === "number" && typeof b === "number"
          ? a - b
          : String(a ?? "").localeCompare(String(b ?? ""), "ko");
      return sort.dir === "asc" ? comparison : -comparison;
    });
  }, [data, sort]);
  const selected = data?.find((row) => row.partId === detailId);
  const saved = (id?: string) => {
    setAdding(false);
    workspace.setAddingPart(false);
    setEditing(null);
    if (id) {
      setHighlight(id);
      pendingDetail.current = id;
    }
    setRevision((value) => value + 1);
    setPage(1);
    setNotice("파트 정보를 저장했습니다.");
  };
  const closeCreate = () => {
    setAdding(false);
    workspace.setAddingPart(false);
  };
  return (
    <section
      className="part-page"
      data-testid="part-page"
      data-screen-id="UI-15"
    >
      {!workspace.inShell && (
        <div className="part-heading">
          <h2>영업 파트</h2>
          <button
            type="button"
            className="primary-button"
            data-testid="part-add-button"
            onClick={() => setAdding(true)}
          >
            파트 추가
          </button>
        </div>
      )}
      <Banner kind="info">
        기준 변경은 적용 시작월부터 새 기준을 사용합니다. 지난 정산은 그대로
        유지됩니다.
      </Banner>
      <ListView
        screenId="UI-15-parts"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={
          sorted?.slice(
            (page - 1) * presentation.pageSize,
            page * presentation.pageSize,
          ) ?? null
        }
        rowKey={(row) => row.partId}
        total={sorted?.length ?? 0}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={presentation.setPageSize}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={(next) => {
          setSort(next);
          setPage(1);
        }}
        onRowClick={(row) => {
          setDetailId(row.partId);
          setTab("standards");
        }}
        highlightKey={highlight}
        error={error}
        onRetry={() => setRevision((value) => value + 1)}
        testId="part-list"
      />
      {selected && (
        <DetailPanel
          title={selected.partName}
          testId="part-detail-panel"
          onClose={() => setDetailId(null)}
          activeTab={tab}
          onTab={setTab}
          actions={
            <>
              <button
                type="button"
                data-testid="part-standard-change"
                onClick={() => setEditing("standards")}
              >
                기준 변경
              </button>
              <button
                type="button"
                data-testid="part-rename"
                onClick={() => setEditing("rename")}
              >
                파트명 수정
              </button>
              {selected.useYn === "Y" && (
                <button
                  type="button"
                  data-testid="part-stop"
                  onClick={() => setEditing("stop")}
                >
                  중지
                </button>
              )}
            </>
          }
          tabs={[
            {
              key: "standards",
              label: "기준 이력",
              content: (
                <PartStandardsPanel
                  partId={selected.partId}
                  revision={revision}
                />
              ),
            },
            {
              key: "history",
              label: "변경 기록",
              content: (
                <PartChangeHistoryPanel
                  partId={selected.partId}
                  revision={revision}
                />
              ),
            },
          ]}
        />
      )}
      {(adding || workspace.addingPart) && (
        <PartCreateDialog onClose={closeCreate} onSaved={saved} />
      )}
      {selected && editing === "standards" && (
        <PartStandardChangeDialog
          part={selected}
          onClose={() => setEditing(null)}
          onSaved={saved}
        />
      )}
      {selected && editing === "rename" && (
        <PartRenameDialog
          part={selected}
          onClose={() => setEditing(null)}
          onSaved={saved}
        />
      )}
      {selected && editing === "stop" && (
        <PartStopDialog
          part={selected}
          onClose={() => setEditing(null)}
          onSaved={saved}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          onDismiss={() => setNotice("")}
          testId="part-saved-toast"
        />
      )}
    </section>
  );
}
