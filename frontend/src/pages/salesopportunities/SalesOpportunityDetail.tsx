import { useEffect, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { ProjectLink } from "../projects/ProjectLink";
import "./salesOpportunities.css";
import {
  getCommonSalesOpportunity,
  salesOpportunityAction,
  salesWinDefaults,
  SalesOpportunityError,
  type SalesOpportunity,
  type SalesOpportunityAction,
  type SalesOpportunityOptions,
} from "./salesOpportunities";

export function SalesOpportunityDetail({
  opportunityId,
  options,
  onEdit,
  onChanged,
  onClose,
}: {
  opportunityId: string;
  options: SalesOpportunityOptions;
  onEdit: (row: SalesOpportunity) => void;
  onChanged: (row: SalesOpportunity) => void;
  onClose: () => void;
}) {
  const [row, setRow] = useState<SalesOpportunity | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [action, setAction] = useState<SalesOpportunityAction | null>(null);
  const [values, setValues] = useState<Record<string, string | number>>({});
  const [fields, setFields] = useState<Record<string, string>>({});
  const [warnings, setWarnings] = useState<
    { needSn: number; developerId: string; message: string }[]
  >([]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    getCommonSalesOpportunity(opportunityId)
      .then((value) => {
        if (active) {
          setRow(value);
          setError("");
        }
      })
      .catch((reason) => {
        if (active)
          setError(
            reason instanceof Error
              ? reason.message
              : "상세를 불러오지 못했습니다.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [opportunityId]);
  function open(next: SalesOpportunityAction) {
    if (!row) return;
    setAction(next);
    setFields({});
    setError("");
    setWarnings([]);
    setValues(
      next === "win"
        ? salesWinDefaults(row)
        : next === "stage"
          ? { stageCode: row.stageCode }
          : next === "tentative-assignment"
            ? {
                needSn: row.needs[0]?.needSn || "",
                startMonth: row.startMonth,
                developerId: "",
              }
            : next === "reject"
              ? { reason: "" }
              : next === "lose"
                ? { lossReasonCode: "", memo: "" }
                : {},
    );
  }
  async function perform(check = false) {
    if (!row || !action) return;
    setFields({});
    setError("");
    if (action === "reject" && [...String(values.reason || "")].length > 300) {
      setFields({ reason: "반려 사유는 300자 이내로 넣어 주세요." });
      return;
    }
    if (action === "lose" && !values.lossReasonCode) {
      setFields({ lossReasonCode: "실주 사유를 골라 주세요." });
      return;
    }
    if (
      action === "win" &&
      String(values.contractEndDate) <= String(values.contractStartDate)
    ) {
      setFields({ contractEndDate: "종료일은 시작일보다 뒤여야 합니다." });
      return;
    }
    setBusy(true);
    try {
      if (check) {
        const result = await salesOpportunityAction<{
          warnings: typeof warnings;
        }>(row, "tentative-assignment-check", values);
        setWarnings(result.warnings);
      } else {
        await salesOpportunityAction(row, action, values);
        const latest = await getCommonSalesOpportunity(row.opportunityId);
        setRow(latest);
        setAction(null);
        onChanged(latest);
        setError("처리했습니다.");
      }
    } catch (reason) {
      if (reason instanceof SalesOpportunityError && reason.status === 409) {
        setAction(null);
        try {
          const latest = await getCommonSalesOpportunity(row.opportunityId);
          setRow(latest);
          onChanged(latest);
        } catch {
          setRow(null);
        }
      }
      if (reason instanceof SalesOpportunityError)
        setFields(
          Object.fromEntries(reason.errors.map((e) => [e.field, e.message])),
        );
      setError(
        reason instanceof Error
          ? reason.message
          : "처리하지 못했습니다. 다시 시도해 주세요.",
      );
    } finally {
      setBusy(false);
    }
  }
  const name = (group: string, code: string | null) =>
    options.codes[group]?.find((c) => c.code === code)?.codeName || code || "—";
  const titles: Partial<Record<SalesOpportunityAction, string>> = {
    approve: "견적 승인",
    reject: "견적 반려",
    win: "수주 처리",
    lose: "실주 처리",
    stop: "기회 삭제",
    resume: "다시 사용",
    stage: "단계 변경",
    "tentative-assignment": "가배정",
  };
  const input = (key: string, label: string, type = "text") => (
    <label>
      {label}
      <input
        data-testid={`sales-action-${key.toLowerCase()}`}
        type={type}
        value={values[key] ?? ""}
        onChange={(e) =>
          setValues((v) => ({
            ...v,
            [key]: type === "number" ? Number(e.target.value) : e.target.value,
          }))
        }
      />
      {fields[key] && <span role="alert">{fields[key]}</span>}
    </label>
  );
  const select = (
    key: string,
    label: string,
    items: { value: string; label: string }[],
  ) => (
    <label>
      {label}
      <select
        data-testid={`sales-action-${key.toLowerCase()}`}
        value={values[key] ?? ""}
        onChange={(e) =>
          setValues((v) => ({
            ...v,
            [key]: key === "needSn" ? Number(e.target.value) : e.target.value,
          }))
        }
      >
        <option value="">선택</option>
        {items.map((c) => (
          <option key={c.value} value={c.value}>
            {c.label}
          </option>
        ))}
      </select>
      {fields[key] && <span role="alert">{fields[key]}</span>}
    </label>
  );
  const codeItems = (group: string) =>
    (options.codes[group] || []).map((c) => ({
      value: c.code,
      label: c.codeName,
    }));
  return (
    <section
      data-screen-id="UI-29"
      data-testid="sales-opportunity-detail"
      className="sales-opportunity-detail"
    >
      <button data-testid="sales-opportunity-detail-close" onClick={onClose}>
        닫기
      </button>
      {loading && <p role="status">불러오는 중…</p>}
      {error && <p role="alert">{error}</p>}
      {row && !loading && (
        <>
          <h2>{row.opportunityName}</h2>
          <p>
            {row.customerName} · {name("SALES_STEP", row.stageCode)} ·{" "}
            {row.winningProbability}%
          </p>
          <p>
            {name("OPP_STAT", row.opportunityStatusCode)} ·{" "}
            {name("APRV_STAT", row.approvalStatusCode)}
          </p>
          {row.approvalStatusCode === "REJ" && (
            <aside>
              견적이 반려됐습니다.
              {row.approvalHistory
                .filter((h) => h.actionCode === "REJ")
                .slice(0, 1)
                .map((h) => (
                  <p key={h.actedAt}>
                    {h.actedAt}
                    {h.reason ? ` · ${h.reason}` : ""}
                  </p>
                ))}
            </aside>
          )}
          <dl>
            <dt>예상 금액</dt>
            <dd>{row.expectedAmount.toLocaleString()}</dd>
            <dt>시작월 / 기간</dt>
            <dd>
              {row.startMonth} / {row.expectedMonths || 6}개월
            </dd>
            <dt>근무지</dt>
            <dd>{row.workplace || "—"}</dd>
            <dt>다음 할 일</dt>
            <dd>
              {row.nextAction || "—"} {row.nextActionDueDate}
            </dd>
            <dt>비고</dt>
            <dd>{row.memo || "—"}</dd>
            <dt>결과일</dt>
            <dd>{row.resultDate || "—"}</dd>
          </dl>
          {row.lossReasonCode && (
            <p>
              실주 사유: {name("LOSE_RSN", row.lossReasonCode)} {row.lossMemo}
            </p>
          )}
          {row.projectId && (
            <ProjectLink
              projectId={row.projectId}
              name="프로젝트 열기"
              testId="sales-opportunity-project"
            />
          )}
          <h3>필요 인력</h3>
          {row.needs.map((n, i) => (
            <article key={n.needSn ?? i} data-testid={`sales-detail-need-${i}`}>
              {name("JOB", n.jobCode)} · {name("GRADE", n.gradeCode)} ·{" "}
              {n.requiredCount}명 · {n.startMonth} · {n.months}개월 ·{" "}
              {n.monthlyMm} MM
              <p>
                가배정:{" "}
                {n.tentativeDeveloperIds
                  .map(
                    (id) =>
                      options.developers.find((d) => d.developerId === id)
                        ?.name || id,
                  )
                  .join(", ") || "없음"}
              </p>
            </article>
          ))}
          {row.opportunityStatusCode === "ING" && (
            <nav>
              <button
                data-testid="sales-opportunity-edit"
                onClick={() => onEdit(row)}
              >
                기회 수정
              </button>
              {(
                [
                  "stage",
                  "tentative-assignment",
                  "win",
                  "lose",
                  "stop",
                ] as const
              ).map((a) => (
                <button
                  key={a}
                  data-testid={`sales-opportunity-${a}`}
                  onClick={() => open(a)}
                >
                  {titles[a]}
                </button>
              ))}
              {row.approvalStatusCode === "WAIT" && (
                <aside>
                  견적 승인 대기
                  <button
                    data-testid="sales-opportunity-approve"
                    onClick={() => open("approve")}
                  >
                    견적 승인
                  </button>
                  <button
                    data-testid="sales-opportunity-reject"
                    onClick={() => open("reject")}
                  >
                    견적 반려
                  </button>
                </aside>
              )}
            </nav>
          )}
          {row.opportunityStatusCode === "STOP" && (
            <button
              data-testid="sales-opportunity-resume"
              onClick={() => open("resume")}
            >
              다시 사용
            </button>
          )}
          <h3>승인 이력</h3>
          {row.approvalHistory.length ? (
            row.approvalHistory.map((h, i) => (
              <p
                key={`${h.actedAt}-${i}`}
                data-testid={`sales-approval-history-${i}`}
              >
                {name("APRV_ACT", h.actionCode)} · {h.actedAt}
                {h.reason ? ` · ${h.reason}` : ""}
              </p>
            ))
          ) : (
            <p>승인 이력이 없습니다.</p>
          )}
          {action && (
            <Dialog
              title={titles[action] || "가배정"}
              testId="sales-opportunity-action-dialog"
              onClose={() => {
                if (!busy) setAction(null);
              }}
            >
              <fieldset disabled={busy}>
                {action === "stop" && (
                  <p>
                    진행 기회를 사용 중지합니다. 기록은 남고 가배정은
                    해제됩니다.
                  </p>
                )}
                {action === "resume" && (
                  <p>다시 사용해도 이전 가배정은 복구되지 않습니다.</p>
                )}
                {action === "reject" &&
                  input("reason", "반려 사유 (선택, 300자 이내)")}
                {action === "stage" &&
                  select("stageCode", "변경 단계", codeItems("SALES_STEP"))}
                {action === "lose" && (
                  <>
                    {select(
                      "lossReasonCode",
                      "실주 사유",
                      codeItems("LOSE_RSN"),
                    )}
                    {input("memo", "실주 메모")}
                  </>
                )}
                {action === "win" && (
                  <>
                    {input("contractAmount", "계약 금액", "number")}
                    {select(
                      "contractTypeCode",
                      "계약 유형",
                      codeItems("CTRT_TYPE"),
                    )}
                    {input("contractStartDate", "계약 시작일", "date")}
                    {input("contractEndDate", "계약 종료일", "date")}
                    {select(
                      "projectStatusCode",
                      "프로젝트 상태",
                      codeItems("PROJ_STAT").filter((c) =>
                        ["ING", "PRE"].includes(c.value),
                      ),
                    )}
                  </>
                )}
                {action === "tentative-assignment" && (
                  <>
                    {select(
                      "needSn",
                      "필요 인력 줄",
                      row.needs.map((n, i) => ({
                        value: String(n.needSn),
                        label: `${i + 1}번째 줄 ${name("JOB", n.jobCode)}`,
                      })),
                    )}
                    {select(
                      "developerId",
                      "가배정 인력",
                      options.developers.map((d) => ({
                        value: d.developerId,
                        label: d.name,
                      })),
                    )}
                    {input("startMonth", "가배정 시작월", "month")}
                    <button
                      data-testid="sales-tentative-check"
                      onClick={() => perform(true)}
                    >
                      충돌 확인
                    </button>
                    {warnings.map((w, i) => (
                      <p role="status" key={i}>
                        {w.needSn}번 줄: {w.message}
                      </p>
                    ))}
                  </>
                )}
                <button
                  data-testid="sales-action-confirm"
                  onClick={() => perform()}
                >
                  확인
                </button>
                <button
                  data-testid="sales-action-cancel"
                  onClick={() => setAction(null)}
                >
                  취소
                </button>
              </fieldset>
            </Dialog>
          )}
        </>
      )}
    </section>
  );
}
