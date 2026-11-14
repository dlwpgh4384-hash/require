import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListFilter,
  ListFilters,
  ListView,
  downloadFile,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { ClientDetail } from "./ClientDetail";
import { ClientForm } from "./ClientForm";
import { UploadActions } from "../uploads/UploadActions";
import {
  ClientRequestError,
  clientsExcelUrl,
  getClientCodes,
  getCommonClients,
  listAccount,
  putCommonClientUseStatus,
  type Client,
  type ClientCode,
} from "./clients";
import "./clients.css";

const SCREEN = "UI-15";
const emptyFilter = { clientTypeCode: "", useYn: "Y", keyword: "" };
type Codes = Record<string, ClientCode[]>;
const codeName = (codes: Codes, group: string, code: string) =>
  codes[group]?.find((item) => item.code === code)?.codeName || code;
const dash = (value: string | null | undefined) => (value ? value : "–");

const plusIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
    <path d="M12 5v14M5 12h14" />
  </svg>
);

/** 기준정보 › 거래처·영업 파트(UI-15) [거래처] 탭. 목록 틀은 공통 부품(SFR-010·011), 모양·문구는 시안 v1.8. */
export function ClientPage({
  createSignal = 0,
  renderActions,
}: {
  createSignal?: number;
  renderActions?: (actions: ReactNode) => ReactNode;
}) {
  const [rows, setRows] = useState<Client[] | null>(null);
  const [codes, setCodes] = useState<Codes>({});
  const [filter, setFilter] = useState(emptyFilter);
  const [applied, setApplied] = useState(emptyFilter);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [form, setForm] = useState<{ client: Client | null } | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [highlight, setHighlight] = useState<string | null>(null);
  const [detail, setDetail] = useState<{ id: string; tab: string } | null>(null);
  const [pending, setPending] = useState<{ items: Client[]; useYn: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [page, setPage] = useState(1);
  const presentation = useListPresentation();

  const columns = useMemo<ListColumn<Client>[]>(
    () => [
      {
        key: "type",
        label: "구분",
        render: (row) => (
          <Badge kind={row.clientTypeCode === "VNDR" ? "neutral" : "info"}>
            {codeName(codes, "CLNT_TYPE", row.clientTypeCode)}
          </Badge>
        ),
      },
      { key: "name", label: "상호", required: true, render: (row) => row.clientName },
      { key: "businessNumber", label: "사업자번호", render: (row) => row.businessNumber },
      { key: "representative", label: "대표자", render: (row) => dash(row.representativeName) },
      {
        key: "industry",
        label: "업종",
        render: (row) => (row.industryCode ? codeName(codes, "INDUTY", row.industryCode) : "–"),
      },
      { key: "contact", label: "담당자", render: (row) => dash(row.contactName) },
      { key: "phone", label: "연락처", render: (row) => dash(row.contactPhone) },
      { key: "taxEmail", label: "세금계산서 이메일", render: (row) => dash(row.taxEmail) },
      { key: "account", label: "지급 계좌", render: (row) => listAccount(row.paymentAccountSummary) },
      {
        key: "projects",
        label: "진행 프로젝트",
        align: "right",
        calc: "오늘 투입 중인 내역이 있는 진행 프로젝트 수",
        render: (row) => (
          <button
            type="button"
            className="ls-lnk"
            data-testid={`client-projects-${row.clientId}`}
            onClick={(event) => {
              event.stopPropagation();
              setDetail({ id: row.clientId, tab: "assignments" });
            }}
          >
            {row.projectCount}건
          </button>
        ),
      },
      {
        key: "people",
        label: "투입 인력",
        align: "right",
        calc: "오늘 투입 중인 사람 수(한 사람은 한 번만)",
        render: (row) => (
          <button
            type="button"
            className="ls-lnk"
            data-testid={`client-people-${row.clientId}`}
            onClick={(event) => {
              event.stopPropagation();
              setDetail({ id: row.clientId, tab: "assignments" });
            }}
          >
            {row.personCount}명
          </button>
        ),
      },
      {
        key: "use",
        label: "사용 여부",
        render: (row) => (
          <Badge kind={row.useYn === "Y" ? "ok" : "lock"}>{row.useYn === "Y" ? "사용" : "중지"}</Badge>
        ),
      },
    ],
    [codes],
  );
  const cols = useListColumns(SCREEN, columns);

  useEffect(() => {
    if (createSignal) setForm({ client: null });
  }, [createSignal]);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    Promise.all([
      getCommonClients(
        { ...applied, useYn: applied.useYn || "ALL" },
        controller.signal,
      ),
      ...["CLNT_TYPE", "INDUTY", "BANK"].map((group) => getClientCodes(group, controller.signal)),
    ])
      .then(([clients, types, industries, banks]) => {
        if (controller.signal.aborted) return;
        setRows(clients.items as Client[]);
        setCodes({
          CLNT_TYPE: types.items as ClientCode[],
          INDUTY: industries.items as ClientCode[],
          BANK: banks.items as ClientCode[],
        });
        setSelected([]);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason.message);
      });
    return () => controller.abort();
  }, [applied, revision]);

  const sorted = useMemo(() => {
    const list = [...(rows ?? [])];
    if (!sort) return list;
    const text = (row: Client) => {
      switch (sort.key) {
        case "type": return codeName(codes, "CLNT_TYPE", row.clientTypeCode);
        case "name": return row.clientName;
        case "businessNumber": return row.businessNumber;
        case "representative": return row.representativeName ?? "";
        case "industry": return codeName(codes, "INDUTY", row.industryCode ?? "");
        case "contact": return row.contactName ?? "";
        case "phone": return row.contactPhone ?? "";
        case "taxEmail": return row.taxEmail ?? "";
        case "account": return row.paymentAccountSummary ?? "";
        case "use": return row.useYn;
        default: return "";
      }
    };
    list.sort((a, b) => {
      const order =
        sort.key === "projects"
          ? a.projectCount - b.projectCount
          : sort.key === "people"
            ? a.personCount - b.personCount
            : text(a).localeCompare(text(b), "ko");
      return sort.dir === "desc" ? -order : order;
    });
    return list;
  }, [rows, sort, codes]);
  const shown = sorted.slice((page - 1) * presentation.pageSize, page * presentation.pageSize);

  const saved = (client: Client, warnings: string[]) => {
    // 시안: 수정은 저장 뒤 그 거래처 상세로 돌아가고, 등록은 목록으로 간다.
    setDetail(form?.client ? { id: client.clientId, tab: "basic" } : null);
    setForm(null);
    setHighlight(client.clientId);
    setNotice(["저장했습니다.", ...warnings].join(" "));
    setRevision((value) => value + 1);
  };
  const blocked =
    pending?.useYn === "N"
      ? pending.items.filter((item) => item.projectCount > 0 || item.personCount > 0)
      : [];
  const changeStatus = async () => {
    if (!pending || busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await putCommonClientUseStatus(
        pending.items.map(({ clientId, modifiedAt }) => ({ clientId, modifiedAt })),
        pending.useYn,
      );
      const verb = pending.useYn === "N" ? "사용 중지" : "다시 사용";
      setNotice(
        `${result.changed.length}건을 ${verb}했습니다.${result.failed.length ? ` ${result.failed.length}건은 처리하지 못했습니다. ${result.failed.map((item) => item.reason).join(" ")}` : ""}`,
      );
      setPending(null);
      setDetail(null);
      setRevision((value) => value + 1);
    } catch (reason) {
      setError(reason instanceof ClientRequestError ? reason.message : "변경하지 못했습니다.");
      setPending(null);
    } finally {
      setBusy(false);
    }
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      await downloadFile(
        clientsExcelUrl({ ...applied, useYn: applied.useYn || "ALL" }, sort, cols.visible.map((column) => column.key)),
        "거래처.xlsx",
      );
      setNotice(`엑셀 파일을 내려받았습니다. (${rows?.length ?? 0}건)`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "엑셀 파일을 받지 못했습니다.");
    } finally {
      setDownloading(false);
    }
  };
  const reset = () => {
    setFilter(emptyFilter);
    setApplied(emptyFilter);
    setPage(1);
  };

  const ready = rows !== null && !error;
  const actions = (
    <div className="client-title-actions">
      <UploadActions
        disabled={!ready}
        onApplied={() => {
          setRevision((value) => value + 1);
          setNotice("엑셀 거래처를 반영했습니다.");
        }}
      />
      <button
        type="button"
        className="primary-button client-create-button"
        data-testid="client-create"
        disabled={!ready}
        onClick={() => setForm({ client: null })}
      >
        {plusIcon}
        등록
      </button>
    </div>
  );

  if (form && !form.client)
    return (
      <ClientForm codes={codes} onCancel={() => setForm(null)} onSaved={saved} />
    );

  return (
    <section className="client-page" data-testid="client-page" data-screen-id={SCREEN}>
      {renderActions ? renderActions(actions) : actions}
      <ListFilters
        onSearch={() => {
          setApplied({ ...filter });
          setPage(1);
        }}
        onReset={reset}
      >
        <ListFilter label="구분" htmlFor="client-type">
          <select
            id="client-type"
            className="ls-in"
            data-testid="client-type-filter"
            value={filter.clientTypeCode}
            onChange={(event) => setFilter({ ...filter, clientTypeCode: event.target.value })}
          >
            <option value="">전체</option>
            {(codes.CLNT_TYPE || []).map((code) => (
              <option key={code.code} value={code.code}>
                {code.codeName}
              </option>
            ))}
          </select>
        </ListFilter>
        <ListFilter label="사용 여부" htmlFor="client-use">
          <select
            id="client-use"
            className="ls-in"
            data-testid="client-use-filter"
            value={filter.useYn}
            onChange={(event) => setFilter({ ...filter, useYn: event.target.value })}
          >
            <option value="">전체</option>
            <option value="Y">사용</option>
            <option value="N">중지</option>
          </select>
        </ListFilter>
        <ListFilter label="상호" htmlFor="client-keyword">
          <input
            id="client-keyword"
            className="ls-in client-keyword"
            data-testid="client-search"
            placeholder="상호 또는 사업자번호"
            value={filter.keyword}
            onChange={(event) => setFilter({ ...filter, keyword: event.target.value })}
          />
        </ListFilter>
      </ListFilters>
      <ListView
        screenId={SCREEN}
        testId="client-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={error ? [] : rows ? shown : null}
        rowKey={(row) => row.clientId}
        total={rows?.length ?? 0}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={(size) => {
          presentation.setPageSize(size);
          setPage(1);
        }}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={(value) => {
          setSort(value);
          setPage(1);
        }}
        selectable
        selected={selected}
        onSelect={setSelected}
        bulkActions={[
          {
            key: "stop",
            label: "사용 중지",
            danger: true,
            onClick: () =>
              setPending({ items: (rows ?? []).filter((row) => selected.includes(row.clientId)), useYn: "N" }),
          },
          {
            key: "resume",
            label: "다시 사용",
            onClick: () =>
              setPending({ items: (rows ?? []).filter((row) => selected.includes(row.clientId)), useYn: "Y" }),
          },
        ]}
        onRowClick={(row) => setDetail({ id: row.clientId, tab: "basic" })}
        highlightKey={highlight}
        onDownload={() => void download()}
        downloading={downloading}
        onResetFilters={reset}
        error={error && !rows ? error : undefined}
        onRetry={() => setRevision((value) => value + 1)}
      />
      {error && rows && (
        <p role="alert" className="field-error" data-testid="client-failure">
          {error}
        </p>
      )}
      {notice && <Toast message={notice} onDismiss={() => setNotice("")} testId="client-toast" />}
      {form?.client && (
        <ClientForm
          key={form.client.clientId}
          client={form.client}
          codes={codes}
          onCancel={() => {
            setDetail({ id: form.client!.clientId, tab: "basic" });
            setForm(null);
          }}
          onSaved={saved}
        />
      )}
      {detail && (
        <ClientDetail
          key={detail.id}
          clientId={detail.id}
          initialTab={detail.tab}
          codes={codes}
          onClose={() => setDetail(null)}
          onEdit={(client) => {
            setDetail(null);
            setForm({ client });
          }}
          onStatus={(client) => setPending({ items: [client], useYn: client.useYn === "Y" ? "N" : "Y" })}
        />
      )}
      {pending && blocked.length > 0 && (
        // 시안: 거래가 남은 거래처가 있으면 중지하지 않고 목록으로 알린다.
        <Dialog
          title="사용을 중지할 수 없습니다"
          testId="client-status-blocked"
          closable
          onClose={() => setPending(null)}
        >
          <p>아래 거래처는 거래가 남아 있어 중지할 수 없습니다.</p>
          <ul className="client-blocked-list">
            {blocked.map((item) => (
              <li key={item.clientId}>
                {item.clientName} —{" "}
                {[
                  item.projectCount > 0 ? `진행 프로젝트 ${item.projectCount}건` : "",
                  item.personCount > 0 ? `투입 인력 ${item.personCount}명` : "",
                ]
                  .filter(Boolean)
                  .join(", ")}
              </li>
            ))}
          </ul>
          <div className="app-actions">
            <button type="button" className="primary-button" data-testid="client-status-blocked-ok" onClick={() => setPending(null)}>
              확인
            </button>
          </div>
        </Dialog>
      )}
      {pending && blocked.length === 0 && (
        <Dialog
          title={
            pending.useYn === "N"
              ? `${pending.items.length}건의 사용을 중지할까요?`
              : `${pending.items.length}건을 다시 사용할까요?`
          }
          testId="client-status-confirm"
          closable
          onClose={() => !busy && setPending(null)}
        >
          <p>
            {pending.items.length === 1
              ? pending.items[0].clientName
              : `${pending.items[0].clientName} 외 ${pending.items.length - 1}건`}{" "}
            —{" "}
            {pending.useYn === "N"
              ? "중지하면 새 프로젝트에서 고를 수 없습니다. 기록은 그대로 남습니다."
              : "다시 사용하면 새 프로젝트에서 고를 수 있습니다."}
          </p>
          <div className="app-actions">
            <button type="button" data-testid="client-status-cancel" disabled={busy} onClick={() => setPending(null)}>
              취소
            </button>
            <button
              type="button"
              className={pending.useYn === "N" ? "app-danger" : "primary-button"}
              data-testid="client-status-save"
              disabled={busy}
              onClick={() => void changeStatus()}
            >
              {pending.useYn === "N" ? "사용 중지" : "다시 사용"}
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
