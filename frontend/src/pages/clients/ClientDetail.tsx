import { useEffect, useState, type ReactNode } from "react";
import { ProjectLink } from "../projects/ProjectLink";
import { formatDateTime, koreanToday } from "../../shared/time";
import { Badge, Banner, DetailPanel, KeyValues } from "../../shared/list";
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
import { historyValue } from "../../shared/historyValue";

const dash = (value: string | null | undefined) => (value ? value : "–");

/** 거래처 상세(시안 drawer): 제목·작은 제목, [수정] [⋯ 변경 이력 보기·사용 중지] [×], 탭 3개. */
export function ClientDetail({
  clientId,
  initialTab = "basic",
  codes,
  onClose,
  onEdit,
  onStatus,
  onNotice,
}: {
  clientId: string;
  initialTab?: string;
  codes: Record<string, ClientCode[]>;
  onClose: () => void;
  onEdit: (client: Client) => void;
  onStatus: (client: Client) => void;
  /** 시안 알림(toast). [보기]로 계좌 전체를 본 뒤 안내한다. */
  onNotice?: (message: string, kind?: "ok" | "info" | "warn") => void;
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
      <button
        type="button"
        className="ls-btn ls-btn-sm"
        data-testid="client-detail-edit"
        onClick={() => onEdit(client)}
      >
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
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M5 12h.01M12 12h.01M19 12h.01" />
          </svg>
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
              className={
                client.useYn === "Y" ? "client-more-danger" : undefined
              }
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
    <>
    {client.useYn !== "Y" && (
      // 시안: 사용을 중지한 거래처는 기본 정보 위에 잠금 띠를 보인다.
      <Banner kind="lock">사용을 중지한 거래처입니다. 새 프로젝트에서 고를 수 없습니다.</Banner>
    )}
    <KeyValues
      items={[
        [
          "구분",
          <Badge kind={client.clientTypeCode === "VNDR" ? "neutral" : "info"}>
            {label("CLNT_TYPE", client.clientTypeCode)}
          </Badge>,
        ],
        ["상호", client.clientName],
        ["사업자번호", client.businessNumber],
        ["대표자", dash(client.representativeName)],
        [
          "업종",
          client.industryCode ? label("INDUTY", client.industryCode) : "–",
        ],
        ["담당자", dash(client.contactName)],
        ["연락처", dash(client.contactPhone)],
        ...(customer
          ? ([["세금계산서 이메일", dash(client.taxEmail)]] as [
              string,
              string,
            ][])
          : []),
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
                          .then((value) => {
                            setAccount(value);
                            onNotice?.(
                              "계좌번호 전체를 보았습니다. 접근 기록에 남습니다.",
                              "info",
                            );
                          })
                          // 계좌 보기 하나가 실패해도 상세 전체를 오류로 바꾸지 않는다(알림으로만).
                          .catch((reason) =>
                            onNotice ? onNotice(reason.message, "warn") : setError(reason.message),
                          )
                          .finally(() => setAccountBusy(false));
                      }}
                    >
                      보기
                    </button>
                  )}
                  <div className="ls-cap">
                    {account ? `예금주 ${account.accountHolder} · ` : ""}재무
                    권한에만 보임. 처음엔 끝 4자리만, [보기]를 누르면 전체가
                    보이고 접근 기록에 남습니다
                  </div>
                </div>,
              ],
            ] as [string, ReactNode][])
          : []),
        [
          "사용 여부",
          <Badge kind={client.useYn === "Y" ? "ok" : "lock"}>
            {client.useYn === "Y" ? "사용" : "중지"}
          </Badge>,
        ],
      ]}
    />
    </>
  );
  const assignmentProjects = [
    ...new Map(
      assignments.map((line) => [line.projectId, line.projectName]),
    ).entries(),
  ];
  const assignmentPeople = new Set(
    assignments.map((line) => line.managementNumber || line.personName),
  ).size;
  // 시안: 구분마다 누구를 세는지 한 문장으로 알려 준다.
  const assignmentWho = !client
    ? ""
    : client.clientTypeCode === "CUST"
      ? "이 고객사 프로젝트에 들어간 사람"
      : client.clientTypeCode === "VNDR"
        ? "이 협력사 소속으로 들어간 사람"
        : "이 회사 프로젝트에 들어갔거나 이 회사 소속인 사람";
  const assignmentsTab = assignments.length ? (
    <>
      <p className="ls-cap">
        투입 배정(UI-12)에서 오늘({koreanToday()}) 투입 중인 내역입니다.{" "}
        {assignmentWho} — 프로젝트 {assignmentProjects.length}건, 인력{" "}
        {assignmentPeople}명.
      </p>
      <div className="client-assignment-chips" data-testid="client-assignment-projects">
        {assignmentProjects.map(([projectId, projectName]) => (
          <span
            key={projectId}
            data-testid={`client-assignment-project-${projectId.toLowerCase()}`}
          >
            <Badge kind="info">
              {projectName}{" "}
              {assignments.filter((line) => line.projectId === projectId).length}명
            </Badge>
          </span>
        ))}
      </div>
      <div className="ls-twrap">
        <table className="ls-t" data-testid="client-assignments">
          <thead>
            <tr>
              <th>프로젝트</th>
              <th>이름</th>
              <th>소속 · 직무</th>
              <th>철수일</th>
            </tr>
          </thead>
          <tbody>
            {assignments.map((line, index) => (
              <tr
                key={`${line.projectId}-${index}`}
                data-testid={`client-assignment-${index}`}
              >
                <td>
                  <ProjectLink
                    projectId={line.projectId}
                    name={line.projectName}
                    testId={`client-project-${index}`}
                  />
                </td>
                <td>
                  {/* 시안 .pname: 이름 아래 작은 관리번호 */}
                  <span className="client-pname">
                    {line.personName}
                    {line.managementNumber && <small>{line.managementNumber}</small>}
                  </span>
                </td>
                <td>
                  <span className="client-pname">
                    {line.companyName || "자사"}
                    <small>
                      {dash(line.jobName)} · {Number(line.monthlyMm)} MM
                    </small>
                  </span>
                </td>
                <td>{dash(line.withdrawalDate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  ) : (
    <p className="ls-cap">지금 투입 중인 내역이 없습니다.</p>
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
              <td>{dash(historyValue(line.fieldName, line.beforeValue))}</td>
              <td>{dash(historyValue(line.fieldName, line.afterValue))}</td>
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
      sub={
        client
          ? `${label("CLNT_TYPE", client.clientTypeCode)} · ${client.businessNumber}`
          : undefined
      }
      actions={actions}
      activeTab={tab}
      onTab={setTab}
      onClose={onClose}
      testId="client-detail"
      tabs={
        client && !error
          ? [
              { key: "basic", label: "기본 정보", content: basicTab },
              {
                key: "assignments",
                label: `투입 내역 (${client.personCount})`,
                content: assignmentsTab,
              },
              {
                key: "history",
                label: `변경 이력 (${changes.length})`,
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
