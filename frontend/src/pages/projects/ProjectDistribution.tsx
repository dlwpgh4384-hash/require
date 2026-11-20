import { useEffect, useState } from "react";
import { Toast } from "../../shared/feedback/Dialog";
import { Badge, Banner } from "../../shared/list";
import { useScreenTitleOverride } from "../../shared/layout/ScreenTitleOverride";
import { useViewport } from "../../shared/layout/useViewport";
import { formatDateTime } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import { UploadDialog } from "../uploads/UploadDialog";
import { UploadResultDialog } from "../uploads/UploadResultDialog";
import { type UploadResult } from "../uploads/uploads";
import {
  getCommonProjectDistribution,
  getCommonProjectDistributionTemplate,
  projectFailure,
  statusKind,
  won,
  type ProjectDistributionData,
} from "./projects";

const icon = (d: string, size = 14) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={d} />
  </svg>
);
const downIcon = icon("M12 4v11M7 10l5 5 5-5M5 20h14");
const upIcon = icon("M12 20V9M7 14l5-5 5 5M5 4h14");
const warnIcon = icon("M12 3l9 16H3zM12 10v4M12 17h.01", 16);
const okIcon = icon("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12l3 3 5-6", 16);
/** 계약 기간의 달 목록(YYYY-MM). 날짜 객체 없이 글자로 센다. */
const monthsBetween = (start: string, end: string) => {
  const out: string[] = [];
  let [y, m] = start.slice(0, 7).split("-").map(Number);
  const last = end.slice(0, 7);
  for (let guard = 0; guard < 600; guard++) {
    const key = `${y}-${String(m).padStart(2, "0")}`;
    if (key > last) break;
    out.push(key);
    m++;
    if (m > 12) {
      m = 1;
      y++;
    }
  }
  return out;
};
/** 이어진 달은 「2026-03 ~ 2026-05」로 묶는다(시안 msRange). */
const monthRanges = (months: string[]) => {
  const sorted = [...months].sort();
  const next = (v: string) => {
    let [y, m] = v.split("-").map(Number);
    m++;
    if (m > 12) {
      m = 1;
      y++;
    }
    return `${y}-${String(m).padStart(2, "0")}`;
  };
  const out: [string, string][] = [];
  for (const m of sorted) {
    const last = out.at(-1);
    if (last && next(last[1]) === m) last[1] = m;
    else out.push([m, m]);
  }
  return out.map(([a, b]) => (a === b ? a : `${a} ~ ${b}`)).join(", ");
};
/** 시안 문구 그대로 */
const why = (kind: string) =>
  kind === "배정 없음"
    ? "배분표에는 금액이 있는데 그 달 투입 배정이 없습니다."
    : "투입 배정은 있는데 배분표에 줄이 없어 「그 달 원가 × 계약금액 ÷ 예산」으로 계산합니다.";

/** 시안 배분표 화면(목록 자리 전체): 제목줄 [‹ 목록] 「배분표 · 프로젝트명」, 카드 하나에 표·불일치·올린 기록. */
export function ProjectDistribution({
  projectId,
  onBack,
  readOnly = false,
}: {
  projectId: string;
  onBack: () => void;
  readOnly?: boolean;
}) {
  const { mobile } = useViewport();
  const [data, setData] = useState<ProjectDistributionData | null>(null);
  const [error, setError] = useState("");
  const [upload, setUpload] = useState(false);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [revision, setRevision] = useState(0);
  const [steps, setSteps] = useState<{ code: string; codeName: string }[]>([]);
  const [notice, setNotice] = useState("");
  useScreenTitleOverride("UI-01", data ? `배분표 · ${data.project.projectName}` : "배분표", onBack);
  useEffect(() => {
    let stopped = false;
    getCommonProjectDistribution(projectId)
      .then((d) => !stopped && setData(d))
      .catch((e) => !stopped && setError(projectFailure(e)));
    return () => {
      stopped = true;
    };
  }, [projectId, revision]);
  useEffect(() => {
    getCommonCodes("DSTRB_STEP")
      .then((s) => setSteps(s.items))
      .catch(() => undefined);
  }, []);
  if (mobile)
    return (
      <section className="project-distribution" data-testid="project-distribution">
        <Banner kind="info">배분표는 PC 전용 화면입니다. PC에서 이용해 주세요.</Banner>
      </section>
    );
  const p = data?.project;
  const done = !p || p.statusCode === "END" || p.statusCode === "LOSE" || readOnly;
  const months = p ? monthsBetween(p.startDate, p.endDate) : [];
  const total = data ? data.items.reduce((s, r) => s + r.totalAmount, 0) : 0;
  const diff = p ? total - p.contractAmount : 0;
  // 계산에 쓰는 표: 가장 나중 단계의 가장 최근 업로드(목록 [배분표] 칸의 단계 이름과 같다)
  const current = data?.uploads.find((u) => u.stepName === p?.distributionStepName);
  return (
    <section className="project-distribution" data-testid="project-distribution">
      <section className="project-card">
        {error && (
          <Banner
            kind="bad"
            action={
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="project-distribution-retry"
                onClick={() => {
                  setError("");
                  setRevision((n) => n + 1);
                }}
              >
                다시 시도
              </button>
            }
          >
            {error}
          </Banner>
        )}
        {!data && !error && (
          <p className="project-sub" role="status">
            배분표를 불러오는 중…
          </p>
        )}
        {data && p && (
          <>
            <p className="project-cap project-dist-head" data-testid="project-distribution-summary">
              {p.projectId} · {p.customerName} · 계약금액 {won(p.contractAmount)}원 · {p.startDate} ~ {p.endDate} ·{" "}
              <Badge kind={statusKind(p.statusCode)}>{p.statusName}</Badge>
            </p>
            <div className="project-row">
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="project-distribution-template"
                onClick={() => void getCommonProjectDistributionTemplate(projectId).catch((e) => setError(projectFailure(e)))}
              >
                {downIcon} 양식 받기
              </button>
              {!done && p.canEdit && (
                <button type="button" className="ls-btn ls-btn-sm ls-btn-p" data-testid="project-distribution-upload" onClick={() => setUpload(true)}>
                  {upIcon} 엑셀 업로드
                </button>
              )}
              <span className="project-cap">화면에서 칸을 직접 고치지 않고, 고친 엑셀을 다시 올립니다. PC 전용.</span>
            </div>
            {!data.uploads.length ? (
              <div className="project-dist-empty" data-testid="project-distribution-empty">
                <p>아직 올린 배분표가 없습니다.</p>
                <p className="project-cap">
                  견적 → 최종 계약 → 실제 투입계획 순서로 올릴 수 있고, 계산에는 가장 나중 단계의 표를 씁니다. 올리기 전에는 「그 달 원가 × (계약금액 ÷
                  예산)」으로 매출을 잡습니다.
                </p>
              </div>
            ) : (
              <>
                <p className="project-dist-current">
                  <strong>계산에 쓰는 표</strong> <Badge kind="info">{p.distributionStepName ?? current?.stepName ?? ""}</Badge>{" "}
                  {current && (
                    <span className="project-cap">
                      {formatDateTime(current.uploadedAt)} · {current.uploadedBy} · {current.fileName}
                    </span>
                  )}
                </p>
                {diff ? (
                  <Banner kind="warn" icon={warnIcon}>
                    <span data-testid="project-distribution-total-warning">
                      배분표 합계 {won(total)}원이 계약금액 {won(p.contractAmount)}원보다{" "}
                      <strong>
                        {won(Math.abs(diff))}원 {diff > 0 ? "많습니다" : "적습니다"}
                      </strong>
                      . 저장은 되었고, 1원이라도 다르면 이렇게 알립니다.
                    </span>
                  </Banner>
                ) : (
                  <Banner kind="info" icon={okIcon}>
                    합계가 계약금액과 같습니다.
                  </Banner>
                )}
                {data.mismatches.length ? (
                  <>
                    <Banner kind="warn" icon={warnIcon}>
                      <strong>투입 배정과 안 맞는 곳 {data.mismatches.length}건</strong>이 있습니다. 배정이 바뀌었다면 실제 투입계획 배분표를 다시 올려
                      주세요. 계산은 지금 표대로 합니다.
                    </Banner>
                    <div className="ls-twrap">
                      <table className="ls-t" data-testid="project-distribution-mismatches">
                        <thead>
                          <tr>
                            <th>사람</th>
                            <th>구분</th>
                            <th>해당 월</th>
                            <th>설명</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.mismatches.map((m, i) => (
                            <tr key={i}>
                              <td>{m.name}</td>
                              <td>
                                <Badge kind="warn">{m.kind}</Badge>
                              </td>
                              <td>{monthRanges(m.months)}</td>
                              <td className="project-cap">{why(m.kind)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                ) : (
                  <Banner kind="info" icon={okIcon}>
                    투입 배정과 사람·월이 모두 맞습니다.
                  </Banner>
                )}
                <div className="ls-twrap">
                  <table className="ls-t" data-testid="project-distribution-table">
                    <thead>
                      <tr>
                        <th>사람·항목</th>
                        <th>구분</th>
                        {months.map((m) => (
                          <th key={m} className="ls-right">
                            {m.slice(2).replace("-", ".")}
                          </th>
                        ))}
                        <th className="ls-right">합계</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.items.map((r, i) => (
                        <tr key={i} data-testid={`project-distribution-row-${i}`}>
                          <td>
                            {r.rowTypeCode === "DEV" ? r.name : r.itemName}
                            {r.rowTypeCode === "DEV" && r.jobNames.length > 0 && <span className="project-cap"> {r.jobNames.join("·")}</span>}
                          </td>
                          <td>
                            <Badge kind={r.rowTypeCode === "DEV" ? "neutral" : "info"}>{r.rowTypeCode === "DEV" ? "사람" : "항목"}</Badge>
                          </td>
                          {months.map((m) => (
                            <td key={m} className="ls-right">
                              {r.monthlyAmounts[m] ? won(r.monthlyAmounts[m]) : <span className="project-sub">–</span>}
                            </td>
                          ))}
                          <td className="ls-right">
                            <strong>{won(r.totalAmount)}</strong>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr>
                        <td>합계</td>
                        <td />
                        {months.map((m) => (
                          <td key={m} className="ls-right">
                            {won(data.items.reduce((sum, r) => sum + (r.monthlyAmounts[m] ?? 0), 0))}
                          </td>
                        ))}
                        <td className="ls-right">
                          <strong>{won(total)}</strong>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
                <p className="project-note">단위: 원. 기술료·컨설팅비 같은 항목 줄은 프로젝트 매출에는 넣고 개인 단가에서는 뺍니다(식-53).</p>
              </>
            )}
            {data.uploads.length > 0 && (
              <>
                <h3 className="project-dist-h3">올린 기록 ({data.uploads.length})</h3>
                <div className="ls-twrap">
                  <table className="ls-t" data-testid="project-distribution-uploads">
                    <thead>
                      <tr>
                        <th>올린 날</th>
                        <th>단계</th>
                        <th>파일</th>
                        <th>올린 사람</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {data.uploads.map((u, i) => (
                        <tr key={i} data-testid={`project-upload-history-${i}`}>
                          <td>{formatDateTime(u.uploadedAt)}</td>
                          <td>
                            <Badge kind="neutral">{u.stepName}</Badge>
                          </td>
                          <td>{u.fileName}</td>
                          <td>{u.uploadedBy}</td>
                          <td>{u === current && <Badge kind="ok">계산에 사용</Badge>}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </>
        )}
      </section>
      {upload && (
        <UploadDialog
          formTypeCode="B6"
          projectId={projectId}
          distributionSteps={steps}
          defaultStep={steps.find((s) => s.codeName === p?.distributionStepName)?.code ?? steps[0]?.code ?? ""}
          onClose={() => setUpload(false)}
          onChecked={(r) => {
            setResult(r);
            setUpload(false);
          }}
        />
      )}
      {result && (
        <UploadResultDialog
          uploadId={result.uploadId}
          initialResult={result}
          targetName="배분표"
          onClose={() => setResult(null)}
          onApplied={() => {
            setRevision((n) => n + 1);
            setNotice("배분표를 올렸습니다.");
          }}
        />
      )}
      {notice && <Toast message={notice} onDismiss={() => setNotice("")} testId="project-distribution-toast" />}
    </section>
  );
}
