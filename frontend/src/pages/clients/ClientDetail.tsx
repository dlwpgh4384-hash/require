import { useEffect, useState } from "react";
import { formatDateTime } from "../../shared/time";
import { ClientForm } from "./ClientForm";
import {
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

export function ClientDetail({
  clientId,
  initialTab = "basic",
  codes,
  onClose,
  onSaved,
  onStatus,
}: {
  clientId: string;
  initialTab?: "basic" | "assignments";
  codes: Record<string, ClientCode[]>;
  onClose: () => void;
  onSaved: (client: Client, warnings: string[]) => void;
  onStatus: (client: Client) => void;
}) {
  const [client, setClient] = useState<Client | null>(null);
  const [assignments, setAssignments] = useState<ClientAssignment[]>([]);
  const [changes, setChanges] = useState<ClientChange[]>([]);
  const [account, setAccount] = useState<ClientAccount | null>(null);
  const [tab, setTab] = useState<"basic" | "assignments" | "history">(
    initialTab,
  );
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [accountBusy, setAccountBusy] = useState(false);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    setAccount(null);
    Promise.all([
      getCommonClient(clientId),
      getCommonClientAssignments(clientId),
      getCommonClientChangeHistory(clientId),
    ])
      .then(([value, lines, history]) => {
        if (active) {
          setClient(value);
          setAssignments(lines.items);
          setChanges(history.items);
        }
      })
      .catch((reason) => {
        if (active) setError(reason.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [clientId, revision]);
  const label = (group: string, code: string) =>
    codes[group]?.find((item) => item.code === code)?.codeName || code;
  return (
    <aside
      className="client-drawer"
      aria-label="거래처 상세"
      data-testid="client-detail"
      data-screen-id="UI-15"
    >
      <header>
        <h2>{client?.clientName || "거래처 상세"}</h2>
        <button data-testid="client-detail-close" onClick={onClose}>
          닫기
        </button>
      </header>
      {loading ? (
        <p role="status">상세 조회 중…</p>
      ) : error ? (
        <div role="alert">
          {error}
          <button
            data-testid="client-detail-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            다시 조회
          </button>
        </div>
      ) : (
        client && (
          <>
            {editing ? (
              <ClientForm
                client={client}
                codes={codes}
                onCancel={() => setEditing(false)}
                onSaved={(saved, warnings) => {
                  setClient(saved);
                  setEditing(false);
                  setRevision((value) => value + 1);
                  onSaved(saved, warnings);
                }}
              />
            ) : (
              <>
                <nav aria-label="거래처 상세 탭">
                  {(
                    [
                      ["basic", "기본 정보"],
                      ["assignments", "투입 내역"],
                      ["history", `변경 이력(${changes.length})`],
                    ] as const
                  ).map(([key, text]) => (
                    <button
                      data-testid={`client-detail-${key}`}
                      key={key}
                      aria-pressed={tab === key}
                      onClick={() => {
                        setTab(key);
                        setAccount(null);
                      }}
                    >
                      {text}
                    </button>
                  ))}
                </nav>
                <section data-testid={`client-${tab}-panel`}>
                  {tab === "basic" && (
                    <>
                      <dl>
                        {[
                          ["구분", label("CLNT_TYPE", client.clientTypeCode)],
                          ["상호", client.clientName],
                          ["사업자번호", client.businessNumber],
                          ["대표자", client.representativeName],
                          ["업종", label("INDUTY", client.industryCode)],
                          ["담당자", client.contactName],
                          ["연락처", client.contactPhone],
                          ["세금계산서 이메일", client.taxEmail],
                          ["지급 계좌", client.paymentAccountSummary],
                          ["사용 여부", client.useYn === "Y" ? "사용" : "중지"],
                        ].map(([name, value]) => (
                          <div key={name}>
                            <dt>{name}</dt>
                            <dd>{value || "–"}</dd>
                          </div>
                        ))}
                      </dl>
                      {client.clientTypeCode !== "CUST" && (
                        <>
                          <button
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
                            지급 계좌 전체 보기
                          </button>
                          {account && (
                            <p data-testid="client-account-full">
                              {label("BANK", account.bankCode)}{" "}
                              {account.accountNumber} · {account.accountHolder}
                            </p>
                          )}
                        </>
                      )}
                    </>
                  )}
                  {tab === "assignments" &&
                    (assignments.length ? (
                      <table>
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
                            <tr
                              key={`${line.projectId}-${index}`}
                              data-testid={`client-assignment-${index}`}
                            >
                              <td>{line.projectName}</td>
                              <td>{line.personName}</td>
                              <td>{line.companyName}</td>
                              <td>{line.jobName}</td>
                              <td>{line.withdrawalDate || "–"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <p>오늘 투입 내역이 없습니다.</p>
                    ))}
                  {tab === "history" &&
                    (changes.length ? (
                      <table>
                        <thead>
                          <tr>
                            <th>일시</th>
                            <th>항목</th>
                            <th>이전</th>
                            <th>이후</th>
                            <th>처리자</th>
                          </tr>
                        </thead>
                        <tbody>
                          {changes.map((line, index) => (
                            <tr
                              key={index}
                              data-testid={`client-history-${index}`}
                            >
                              <td>{formatDateTime(line.changedAt)}</td>
                              <td>{line.fieldName}</td>
                              <td>{line.beforeValue || "–"}</td>
                              <td>{line.afterValue || "–"}</td>
                              <td>{line.changedBy}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <p>변경 이력이 없습니다.</p>
                    ))}
                </section>
                <footer>
                  <button
                    data-testid="client-detail-edit"
                    onClick={() => setEditing(true)}
                  >
                    수정
                  </button>
                  <button
                    data-testid="client-detail-status"
                    onClick={() => onStatus(client)}
                  >
                    {client.useYn === "Y" ? "사용 중지" : "다시 사용"}
                  </button>
                </footer>
              </>
            )}
          </>
        )
      )}
    </aside>
  );
}
