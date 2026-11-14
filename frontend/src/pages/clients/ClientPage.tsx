import { useEffect, useState, type ReactNode } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { useListPresentation } from "../../shared/list/useListPresentation";
import { ClientDetail } from "./ClientDetail";
import { ClientForm } from "./ClientForm";
import { UploadActions } from "../uploads/UploadActions";
import {
  ClientRequestError,
  getClientCodes,
  getCommonClients,
  putCommonClientUseStatus,
  type Client,
  type ClientCode,
} from "./clients";
import "./clients.css";

export function ClientPage({
  createSignal = 0,
  renderActions,
}: {
  createSignal?: number;
  renderActions?: (actions: ReactNode) => ReactNode;
}) {
  const [rows, setRows] = useState<Client[]>([]);
  const [codes, setCodes] = useState<Record<string, ClientCode[]>>({});
  const [filter, setFilter] = useState({
    clientTypeCode: "",
    useYn: "Y",
    keyword: "",
  });
  const [applied, setApplied] = useState(filter);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [highlight, setHighlight] = useState("");
  const [detail, setDetail] = useState<{
    id: string;
    tab: "basic" | "assignments";
  } | null>(null);
  const [pending, setPending] = useState<{
    items: Client[];
    useYn: string;
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const [page, setPage] = useState(0);
  const presentation = useListPresentation();
  useEffect(() => {
    if (createSignal) setCreating(true);
  }, [createSignal]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    Promise.all([
      getCommonClients(applied, controller.signal),
      ...["CLNT_TYPE", "INDUTY", "BANK"].map((group) =>
        getClientCodes(group, controller.signal),
      ),
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
        setPage(0);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [applied, revision]);
  const saved = (client: Client, warnings: string[]) => {
    setCreating(false);
    setHighlight(client.clientId);
    setNotice(["저장했습니다.", ...warnings].join(" "));
    setApplied({ clientTypeCode: "", useYn: "Y", keyword: "" });
    setRevision((value) => value + 1);
  };
  const changeStatus = async () => {
    if (!pending || busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await putCommonClientUseStatus(
        pending.items.map(({ clientId, modifiedAt }) => ({
          clientId,
          modifiedAt,
        })),
        pending.useYn,
      );
      setNotice(
        `${result.changed.length}건 변경, ${result.failed.length}건 실패. ${result.failed.map((item) => `${item.clientId}: ${item.reason}`).join(" ")}`,
      );
      setPending(null);
      setDetail(null);
      setRevision((value) => value + 1);
    } catch (reason) {
      setError(
        reason instanceof ClientRequestError
          ? reason.message
          : "변경하지 못했습니다.",
      );
      setPending(null);
    } finally {
      setBusy(false);
    }
  };
  const label = (group: string, code: string) =>
    codes[group]?.find((item) => item.code === code)?.codeName || code;
  if (creating)
    return (
      <ClientForm
        codes={codes}
        onCancel={() => setCreating(false)}
        onSaved={saved}
      />
    );
  const shown = rows.slice(
    page * presentation.pageSize,
    (page + 1) * presentation.pageSize,
  );
  const actions = (
    <div className="client-toolbar">
      <UploadActions
        disabled={loading || !!error}
        onApplied={() => {
          setRevision((value) => value + 1);
          setNotice("엑셀 거래처를 반영했습니다.");
        }}
      />
      <button
        className="primary-button"
        data-testid="client-create"
        disabled={loading || !!error}
        onClick={() => setCreating(true)}
      >
        거래처 등록
      </button>
    </div>
  );
  return (
    <section
      className={`client-page ${presentation.dense ? "client-dense" : ""}`}
      data-testid="client-page"
      data-screen-id="UI-15"
    >
      {renderActions ? renderActions(actions) : actions}
      <form
        className="client-filters"
        onSubmit={(event) => {
          event.preventDefault();
          setApplied({ ...filter });
        }}
      >
        <label>
          구분
          <select
            data-testid="client-type-filter"
            value={filter.clientTypeCode}
            onChange={(event) =>
              setFilter({ ...filter, clientTypeCode: event.target.value })
            }
          >
            <option value="">전체</option>
            {(codes.CLNT_TYPE || []).map((code) => (
              <option key={code.code} value={code.code}>
                {code.codeName}
              </option>
            ))}
          </select>
        </label>
        <label>
          사용 여부
          <select
            data-testid="client-use-filter"
            value={filter.useYn}
            onChange={(event) =>
              setFilter({ ...filter, useYn: event.target.value })
            }
          >
            <option value="Y">사용</option>
            <option value="N">중지</option>
          </select>
        </label>
        <label>
          상호 또는 사업자번호
          <input
            data-testid="client-search"
            value={filter.keyword}
            onChange={(event) =>
              setFilter({ ...filter, keyword: event.target.value })
            }
          />
        </label>
        <button data-testid="client-query" type="submit">
          조회
        </button>
      </form>
      <div className="client-toolbar">
        <span>{rows.length}건</span>
        <button
          data-testid="client-stop-selected"
          disabled={!selected.length || loading}
          onClick={() =>
            setPending({
              items: rows.filter((row) => selected.includes(row.clientId)),
              useYn: "N",
            })
          }
        >
          사용 중지
        </button>
        <button
          data-testid="client-resume-selected"
          disabled={!selected.length || loading}
          onClick={() =>
            setPending({
              items: rows.filter((row) => selected.includes(row.clientId)),
              useYn: "Y",
            })
          }
        >
          다시 사용
        </button>
        <label>
          한 화면 건수
          <select
            data-testid="client-page-size"
            value={presentation.pageSize}
            onChange={(event) => {
              presentation.setPageSize(Number(event.target.value));
              setPage(0);
            }}
          >
            {[20, 50, 100].map((size) => (
              <option key={size}>{size}</option>
            ))}
          </select>
        </label>
        <label>
          <input
            data-testid="client-density"
            type="checkbox"
            checked={presentation.dense}
            onChange={(event) => presentation.setDense(event.target.checked)}
          />
          좁게 보기
        </label>
      </div>
      {notice && (
        <p role="status" className="client-banner">
          {notice}
        </p>
      )}
      {error && (
        <div role="alert" className="client-error">
          {error}
          <button
            data-testid="client-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            다시 불러오기
          </button>
        </div>
      )}
      {loading ? (
        <p role="status">거래처 조회 중…</p>
      ) : (
        !error && (
          <div className="client-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>
                    <input
                      data-testid="client-select-all"
                      aria-label="현재 페이지 전체 선택"
                      type="checkbox"
                      checked={
                        !!shown.length &&
                        shown.every((row) => selected.includes(row.clientId))
                      }
                      onChange={(event) =>
                        setSelected(
                          event.target.checked
                            ? shown.map((row) => row.clientId)
                            : [],
                        )
                      }
                    />
                  </th>
                  {[
                    "구분",
                    "상호",
                    "사업자번호",
                    "대표자",
                    "업종",
                    "담당자",
                    "연락처",
                    "세금계산서 이메일",
                    "지급 계좌",
                    "진행 프로젝트",
                    "투입 인력",
                    "사용 여부",
                  ].map((text) => (
                    <th key={text}>{text}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {shown.map((row) => (
                  <tr
                    key={row.clientId}
                    data-testid={`client-row-${row.clientId}`}
                    className={
                      row.clientId === highlight ? "client-highlight" : ""
                    }
                  >
                    <td>
                      <input
                        data-testid={`client-select-${row.clientId}`}
                        aria-label={`${row.clientName} 선택`}
                        type="checkbox"
                        checked={selected.includes(row.clientId)}
                        onChange={(event) =>
                          setSelected((value) =>
                            event.target.checked
                              ? [...value, row.clientId]
                              : value.filter((id) => id !== row.clientId),
                          )
                        }
                      />
                    </td>
                    <td>
                      <span className="client-badge">
                        {label("CLNT_TYPE", row.clientTypeCode)}
                      </span>
                    </td>
                    <td>
                      <button
                        className="client-link"
                        data-testid={`client-open-${row.clientId}`}
                        onClick={() =>
                          setDetail({ id: row.clientId, tab: "basic" })
                        }
                      >
                        {row.clientName}
                      </button>
                    </td>
                    <td>{row.businessNumber}</td>
                    <td>{row.representativeName}</td>
                    <td>{label("INDUTY", row.industryCode)}</td>
                    <td>{row.contactName}</td>
                    <td>{row.contactPhone}</td>
                    <td>{row.taxEmail}</td>
                    <td>{row.paymentAccountSummary}</td>
                    <td>
                      <button
                        data-testid={`client-projects-${row.clientId}`}
                        className="client-link"
                        onClick={() =>
                          setDetail({ id: row.clientId, tab: "assignments" })
                        }
                      >
                        {row.projectCount}
                      </button>
                    </td>
                    <td>
                      <button
                        data-testid={`client-people-${row.clientId}`}
                        className="client-link"
                        onClick={() =>
                          setDetail({ id: row.clientId, tab: "assignments" })
                        }
                      >
                        {row.personCount}
                      </button>
                    </td>
                    <td>
                      <span
                        className={`client-badge ${row.useYn === "Y" ? "client-active" : ""}`}
                      >
                        {row.useYn === "Y" ? "사용" : "중지"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!rows.length && <p>등록된 거래처가 없습니다.</p>}
          </div>
        )
      )}
      <footer>
        <button
          data-testid="client-previous"
          disabled={page === 0}
          onClick={() => setPage((value) => value - 1)}
        >
          이전
        </button>
        <span>{page + 1}</span>
        <button
          data-testid="client-next"
          disabled={(page + 1) * presentation.pageSize >= rows.length}
          onClick={() => setPage((value) => value + 1)}
        >
          다음
        </button>
      </footer>
      {detail && (
        <ClientDetail
          key={detail.id}
          clientId={detail.id}
          initialTab={detail.tab}
          codes={codes}
          onClose={() => setDetail(null)}
          onSaved={saved}
          onStatus={(client) =>
            setPending({
              items: [client],
              useYn: client.useYn === "Y" ? "N" : "Y",
            })
          }
        />
      )}
      {pending && (
        <Dialog
          title={
            pending.useYn === "N" ? "거래처 사용 중지" : "거래처 다시 사용"
          }
          testId="client-status-confirm"
          closable
          onClose={() => !busy && setPending(null)}
        >
          <p>
            {pending.items.length}건을{" "}
            {pending.useYn === "N" ? "중지" : "다시 사용"}하시겠습니까?
          </p>
          <p>진행 프로젝트 또는 소속 인력이 있으면 중지할 수 없습니다.</p>
          <footer>
            <button
              data-testid="client-status-cancel"
              disabled={busy}
              onClick={() => setPending(null)}
            >
              취소
            </button>
            <button
              data-testid="client-status-save"
              disabled={busy}
              onClick={() => void changeStatus()}
            >
              확인
            </button>
          </footer>
        </Dialog>
      )}
    </section>
  );
}
