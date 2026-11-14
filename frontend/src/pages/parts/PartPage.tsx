import { useEffect, useMemo, useRef, useState } from "react";
import { Toast } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  DetailPanel,
  KeyValues,
  ListView,
  downloadFile,
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
import * as api from "./parts";
import "./parts.css";

// 시안 UI-15 [영업 파트] 표: 열 이름·값 형식 그대로.
const columns: ListColumn<api.Part>[] = [
  { key: "partName", label: "파트명", required: true },
  { key: "leaderName", label: "파트장" },
  {
    key: "distributionRate",
    label: "이익 배분율",
    align: "right",
    render: (row) => `${row.distributionRate}%`,
  },
  {
    key: "salesExpense",
    label: "공통관리비(1인·월)",
    align: "right",
    render: (row) => api.partMoney(row.salesExpense),
  },
  { key: "effectiveMonth", label: "적용 시작" },
  {
    key: "scheduledChange",
    label: "예정된 변경",
    sortable: false,
    render: (row) => row.scheduledChange ?? "–",
  },
  { key: "personCount", label: "소속 인력", align: "right", render: (row) => `${row.personCount}명` },
  { key: "projectCount", label: "진행 프로젝트", align: "right", render: (row) => `${row.projectCount}건` },
  {
    key: "useYn",
    label: "사용 여부",
    render: (row) => (
      <Badge kind={row.useYn === "Y" ? "ok" : "lock"}>{row.useYn === "Y" ? "사용" : "중지"}</Badge>
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
  const [menu, setMenu] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [options, setOptions] = useState<api.PartOptions | null>(null);
  useEffect(() => {
    if (!detailId) return;
    const controller = new AbortController();
    api.getCommonPartOptions(controller.signal).then(
      (value) => setOptions(value),
      () => undefined,
    );
    return () => controller.abort();
  }, [detailId, revision]);
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      await downloadFile(api.partsExcelUrl(sort, cols.visible.map((column) => column.key)), "영업 파트.xlsx");
      setNotice(`엑셀 파일을 내려받았습니다. (${data?.length ?? 0}건)`);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "엑셀 파일을 받지 못했습니다.");
    } finally {
      setDownloading(false);
    }
  };
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
        이익 배분율·공통관리비를 바꾸면 적용 시작월부터 새 기준을 씁니다. 지난 달 정산은 바뀌지 않습니다.
        기준 변경은 시스템 관리자·재무 담당만 할 수 있습니다.
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
          setMenu(false);
        }}
        highlightKey={highlight}
        columnSettings={false}
        onDownload={() => void download()}
        downloading={downloading}
        error={error}
        onRetry={() => setRevision((value) => value + 1)}
        testId="part-list"
      />
      {selected && (
        <DetailPanel
          title={selected.partName}
          sub={`파트장 ${selected.leaderName}`}
          testId="part-detail-panel"
          onClose={() => setDetailId(null)}
          actions={
            <>
              <button
                type="button"
                className="ls-btn ls-btn-sm ls-btn-p"
                data-testid="part-standard-change"
                onClick={() => setEditing("standards")}
              >
                기준 변경
              </button>
              <div className="part-more">
                <button
                  type="button"
                  className="ls-ibtn"
                  aria-label="더보기"
                  aria-expanded={menu}
                  data-testid="part-more"
                  onClick={() => setMenu((value) => !value)}
                >
                  ⋯
                </button>
                {menu && (
                  <div className="part-more-menu" role="menu">
                    <button
                      type="button"
                      role="menuitem"
                      data-testid="part-rename"
                      onClick={() => {
                        setMenu(false);
                        setEditing("rename");
                      }}
                    >
                      파트명 수정
                    </button>
                    {selected.useYn === "Y" && (
                      <button
                        type="button"
                        role="menuitem"
                        className="part-more-danger"
                        data-testid="part-stop"
                        onClick={() => {
                          setMenu(false);
                          setEditing("stop");
                        }}
                      >
                        사용 중지
                      </button>
                    )}
                  </div>
                )}
              </div>
            </>
          }
        >
          <KeyValues
            items={[
              ["파트장", selected.leaderName],
              ["이익 배분율", `${selected.distributionRate}%`],
              ["공통관리비", `${api.partMoney(selected.salesExpense)} 원 (자사 인력 1인·월)`],
              ["적용 기간", `${selected.effectiveMonth} ~ ${selected.endMonth ?? "현재"}`],
              ["소속 인력", `${selected.personCount}명`],
              ["진행 프로젝트", `${selected.projectCount}건`],
              [
                "사용 여부",
                <Badge kind={selected.useYn === "Y" ? "ok" : "lock"}>{selected.useYn === "Y" ? "사용" : "중지"}</Badge>,
              ],
            ]}
          />
          <h3 className="part-section-title">기준 이력</h3>
          <PartStandardsPanel partId={selected.partId} revision={revision} />
          {options && (
            <p className="part-note" data-testid="part-period-note">
              {options.closedMonth
                ? `마감된 달(${options.closedMonth})까지의 정산에는 그때 기준이 그대로 쓰입니다. `
                : ""}
              새 기준은 {options.earliestMonth}부터 넣을 수 있습니다.
            </p>
          )}
        </DetailPanel>
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
