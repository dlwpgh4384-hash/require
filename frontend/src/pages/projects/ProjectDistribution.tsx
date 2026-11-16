import { useEffect, useState } from "react";
import { useViewport } from "../../shared/layout/useViewport";
import { UploadDialog } from "../uploads/UploadDialog";
import { UploadResultDialog } from "../uploads/UploadResultDialog";
import { getCommonCodes } from "../codegroups/codeGroups";
import { type UploadResult } from "../uploads/uploads";
import {
  getCommonProjectDistribution,
  getCommonProjectDistributionTemplate,
  projectFailure,
  won,
  type ProjectDistributionData,
} from "./projects";

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
      .catch((e) => setError(projectFailure(e)));
  }, []);
  const months: string[] = [];
  if (data) {
    const date = new Date(`${data.project.startDate.slice(0, 7)}-01T00:00:00Z`);
    const end = data.project.endDate.slice(0, 7);
    while (date.toISOString().slice(0, 7) <= end) {
      months.push(date.toISOString().slice(0, 7));
      date.setUTCMonth(date.getUTCMonth() + 1);
    }
  }
  return (
    <section
      className="project-distribution"
      data-testid="project-distribution"
    >
      <button data-testid="project-distribution-back" onClick={onBack}>
        목록으로
      </button>
      <h2>턴키 배분표</h2>
      {mobile ? (
        <p>배분표는 PC전용 화면입니다. PC에서 열어 주세요.</p>
      ) : (
        <>
          {error && (
            <p role="alert">
              {error}
              <button
                data-testid="project-distribution-retry"
                onClick={() => {
                  setError("");
                  setRevision((n) => n + 1);
                }}
              >
                다시 조회
              </button>
            </p>
          )}
          {!data && !error && <p role="status">배분표를 불러오는 중…</p>}
          {data && (
            <>
              <p>
                {data.project.projectId} · {data.project.projectName} ·{" "}
                {data.project.customerName} · {won(data.project.contractAmount)}
                원 · {data.project.startDate} ~ {data.project.endDate} ·{" "}
                {data.project.statusName}
              </p>
              <div className="project-actions">
                <button
                  data-testid="project-distribution-template"
                  onClick={() =>
                    void getCommonProjectDistributionTemplate(projectId).catch(
                      (e) => setError(projectFailure(e)),
                    )
                  }
                >
                  양식 내려받기
                </button>
                {!readOnly && data.project.canEdit && (
                  <button
                    data-testid="project-distribution-upload"
                    onClick={() => setUpload(true)}
                  >
                    배분표 올리기
                  </button>
                )}
              </div>
              {data.totalAmountWarning && (
                <p className="project-banner">
                  계약금액과 배분표 합계가 다릅니다.
                </p>
              )}
              {data.mismatches.length > 0 && (
                <aside className="project-banner">
                  불일치 {data.mismatches.length}건
                  {data.mismatches.map((m, i) => (
                    <p key={i}>
                      {m.name} · {m.kind} · {m.months.join(", ")}
                    </p>
                  ))}
                </aside>
              )}
              <div className="project-table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>구분</th>
                      <th>관리번호</th>
                      <th>이름/항목</th>
                      <th>직무</th>
                      {months.map((m) => (
                        <th key={m}>{m}</th>
                      ))}
                      <th>합계</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((r, i) => (
                      <tr key={i} data-testid={`project-distribution-row-${i}`}>
                        <td>{r.rowTypeCode}</td>
                        <td>{r.managementNumber ?? "–"}</td>
                        <td>{r.itemName ?? r.name}</td>
                        <td>{r.jobNames.join(", ")}</td>
                        {months.map((m) => (
                          <td key={m}>{won(r.monthlyAmounts[m] ?? 0)}</td>
                        ))}
                        <td>{won(r.totalAmount)}</td>
                      </tr>
                    ))}
                    <tr>
                      <th colSpan={4}>합계</th>
                      {months.map((m) => (
                        <td key={m}>
                          {won(
                            data.items.reduce(
                              (sum, r) => sum + (r.monthlyAmounts[m] ?? 0),
                              0,
                            ),
                          )}
                        </td>
                      ))}
                      <td>
                        {won(
                          data.items.reduce((sum, r) => sum + r.totalAmount, 0),
                        )}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <h3>올린 기록</h3>
              <table>
                <thead>
                  <tr>
                    <th>올린날</th>
                    <th>단계</th>
                    <th>파일</th>
                    <th>올린사람</th>
                  </tr>
                </thead>
                <tbody>
                  {data.uploads.map((u, i) => (
                    <tr key={i} data-testid={`project-upload-history-${i}`}>
                      <td>{u.uploadedAt}</td>
                      <td>{u.stepName}</td>
                      <td>{u.fileName}</td>
                      <td>{u.uploadedBy}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
          {upload && (
            <UploadDialog
              formTypeCode="B6"
              projectId={projectId}
              distributionSteps={steps}
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
              onClose={() => setResult(null)}
              onApplied={() => setRevision((n) => n + 1)}
            />
          )}
        </>
      )}
    </section>
  );
}
