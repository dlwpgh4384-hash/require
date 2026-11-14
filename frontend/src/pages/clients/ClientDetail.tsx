import { useEffect, useState, type ReactNode } from "react";
import { formatDateTime } from "../../shared/time";
import { Badge, DetailPanel, KeyValues } from "../../shared/list";
import {
  detailAccount,
  getCommonClient,
  getCommonClientAssignments,
  getCommonClientChangeHistory,
  getCommonClientPaymentAccount,
  type Client,
  type ClientAccount,
  type ClientAssignment,
  type ClientChange,
  type ClientCode,
} from "./clients";

const dash = (value: string | null | undefined) => (value ? value : "–");

/** 거래처 상세(시안 drawer): 제목·작은 제목, [수정] [⋯ 변경 이력 보기·사용 중지] [×], 탭 3개. */
export function ClientDetail({
  clientId,
  initialTab = "basic",
  codes,
  onClose,
  onEdit,
  onStatus,
}: {
  clientId: string;
  initialTab?: string;
  codes: Record<string, ClientCode[]>;
  onClose: () => void;
  onEdit: (client: Client) => void;
  onStatus: (client: Client) => void;
}) {
  const [client, setClient] = useState<Client | null>(null);
  const [assignments, setAssignments] = useState<ClientAssignment[]>([]);
  const [changes, setChanges] = useState<ClientChange[]>([]);
  const [account, setAccount] = useState<ClientAccount | null>(null);
  const [tab, setTab] = useState(initialTab);
  const [menu, setMenu] = useState(false);
  const [accountBusy, setAccountBusy] = useState(false);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    setError("");
    setAccount(null);
    Promise.all([
      getCommonClient(clientId),
      getCommonClientAssignments(clientId),
      getCommonClientChangeHistory(clientId),
    ])
      .then(([value, lines, history]) => {
        if (!active) return;
        setClient(value);
        setAssignments(lines.items);
        setChanges(history.items);
      })
      .catch((reason) => {
        if (active) setError(reason.message);
      });
    return () => {
      active = false;
    };
  }, [clientId, revision]);
  const label = (group: string, code: string) =>
    codes[group]?.find((item) => item.code === code)?.codeName || code;

  const actions = client && (
    <>
      <button type="button" className="ls-btn ls-btn-sm" data-testid="client-detail-edit" onClick={() => onEdit(client)}>
        수정
      </button>
      <div className="client-more">
        <button
          type="button"
          className="ls-ibtn ls-ibtn-sm"
          aria-label="더보기"
          aria-expanded={menu}
          data-testid="client-detail-more"
          onClick={() => setMenu((value) => !value)}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12h.01M12 12h.01M19 12h.01" /></svg>
        </button>
        {menu && (
          <div className="client-more-menu" role="menu">
            <button
              type="button"
              role="menuitem"
              data-testid="client-detail-history"
              onClick={() => {
                setMenu(false);
                setTab("history");
              }}
            >
              변경 이력 보기
            </button>
            <button
              type="button"
              role="menuitem"
              className={client.useYn === "Y" ? "client-more-danger" : undefined}
              data-testid="client-detail-status"
              onClick={() => {
                setMenu(false);
                onStatus(client);
              }}
            >
              {client.useYn === "Y" ? "사용 중지" : "다시 사용"}
            </button>
          </div>
        )}
      </div>
    </>
  );

  const vendor = client ? client.clientTypeCode !== "CUST" : false;
  const customer = client ? client.clientTypeCode !== "VNDR" : false;
  const basicTab = client && (
    <KeyValues
      items={[
        ["구분", <Badge kind={client.clientTypeCode === "VNDR" ? "neutral" : "info"}>{label("CLNT_TYPE", client.clientTypeCode)}</Badge>],
        ["상호", client.clientName],
        ["사업자번호", client.businessNumber],
        ["대표자", dash(client.representativeName)],
        ["업종", client.industryCode ? label("INDUTY", client.industryCode) : "–"],
        ["담당자", dash(client.contactName)],
        ["연락처", dash(client.contactPhone)],
        ...(customer ? ([["세금계산서 이메일", dash(client.taxEmail)]] as [string, string][]) : []),
        ...(vendor
          ? ([
              [
                "지급 계좌",
                <div className="client-account">
                  <span data-testid="client-account-masked">
                    {account
                      ? `${label("BANK", account.bankCode)} ${account.accountNumber}`
                      : detailAccount(client.paymentAccountSummary)}
                  </span>{" "}
                  {!account && client.paymentAccountSummary && (
                    <button
                      type="button"
                      className="ls-lnk"
                      data-testid="client-account-read"
                      disabled={accountBusy}
                      onClick={() => {
                        setAccountBusy(true);
                        void getCommonClientPaymentAccount(clientId)
                          .then(setAccount)
                          .catch((reason) => setError(reason.message))
                          .finally(() => setAccountBusy(false));
                      }}
                    >
                      보기
                    </button>
                  )}
                  <div className="ls-cap">
                    {account ? `예금주 ${account.accountHolder} · ` : ""}재무 권한에만 보임. 처음엔 끝 4자리만,
                    [보기]를 누르면 전체가 보이고 접근 기록에 남습니다
                  </div>
                </div>,
              ],
            ] as [string, ReactNode][])
          : []),
        ["사용 여부", <Badge kind={client.useYn === "Y" ? "ok" : "lock"}>{client.useYn === "Y" ? "사용" : "중지"}</Badge>],
      ]}
    />
  );
  const assignmentsTab = assignments.length ? (
    <div className="ls-twrap">
      <table className="ls-t" data-testid="client-assignments">
        <thead>
          <tr>
            <th>프로젝트</th>
            <th>이름</th>
            <th>소속</th>
            <th>직무</th>
            <th>철수일</th>
          </tr>
        </thead>
        <tbody>
          {assignments.map((line, index) => (
            <tr key={`${line.projectId}-${index}`} data-testid={`client-assignment-${index}`}>
              <td>{line.projectName}</td>
              <td>{line.personName}</td>
              <td>{dash(line.companyName)}</td>
              <td>{dash(line.jobName)}</td>
              <td>{dash(line.withdrawalDate)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <p className="ls-cap">오늘 투입 중인 사람이 없습니다.</p>
  );
  const historyTab = changes.length ? (
    <div className="ls-twrap">
      <table className="ls-t" data-testid="client-changes">
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
          {changes.map((line, index) => (
            <tr key={index} data-testid={`client-history-${index}`}>
              <td>{formatDateTime(line.changedAt)}</td>
              <td>{line.fieldName}</td>
              <td>{dash(line.beforeValue)}</td>
              <td>{dash(line.afterValue)}</td>
              <td>{line.changedBy}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <p className="ls-cap">아직 바꾼 기록이 없습니다.</p>
  );

  return (
    <DetailPanel
      title={client?.clientName ?? "거래처"}
      sub={client ? `${label("CLNT_TYPE", client.clientTypeCode)} · ${client.businessNumber}` : undefined}
      actions={actions}
      activeTab={tab}
      onTab={setTab}
      onClose={onClose}
      testId="client-detail"
      tabs={
        client && !error
          ? [
              { key: "basic", label: "기본 정보", content: basicTab },
              { key: "assignments", label: `투입 내역 (${client.personCount})`, content: assignmentsTab },
              { key: "history", label: `변경 이력 (${changes.length})`, content: historyTab },
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
            data-testid="client-detail-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            다시 불러오기
          </button>
        </div>
      ) : (
        <p role="status">거래처를 불러오는 중입니다.</p>
      )}
    </DetailPanel>
  );
}
