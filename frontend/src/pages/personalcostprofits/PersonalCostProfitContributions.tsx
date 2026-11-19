import type {
  PersonalCostProfitContribution,
  PersonalCostProfitAllocation,
} from "./personalCostProfitTypes";
import { won } from "../projects/projects";

const grades = ["초급", "중급", "고급", "특급"] as const;
export function PersonalCostProfitContributions({
  items,
}: {
  items: PersonalCostProfitContribution[];
}) {
  const jobs = [
    ...new Map(items.map((item) => [item.jobCode, item.jobName])).entries(),
  ];
  return (
    <details data-testid="personal-cost-profit-contributions">
      <summary data-testid="personal-cost-profit-contributions-toggle">
        직무·등급별 인당 공헌이익
      </summary>
      <div className="personal-cost-profit-scroll">
        <table className="personal-cost-profit-table">
          <thead>
            <tr>
              <th>직무</th>
              {grades.map((grade) => (
                <th key={grade}>{grade}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {jobs.map(([code, name]) => (
              <tr key={code} data-testid={`personal-contribution-${code}`}>
                <th scope="row">{name}</th>
                {grades.map((grade) => {
                  const cell = items.find(
                    (item) => item.jobCode === code && item.gradeName === grade,
                  );
                  return (
                    <td key={grade}>
                      {cell
                        ? `${won(cell.contributionPerPerson)} (${cell.people}명)`
                        : "– (0명)"}
                    </td>
                  );
                })}
              </tr>
            ))}
            {jobs.length === 0 && (
              <tr>
                <td colSpan={5}>조회된 직무·등급 자료가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </details>
  );
}
export function PersonalCostProfitAllocations({
  items,
}: {
  items: PersonalCostProfitAllocation[];
}) {
  return (
    <div
      className="personal-cost-profit-scroll"
      data-testid="personal-cost-profit-allocations"
    >
      <table className="personal-cost-profit-table">
        <thead>
          <tr>
            <th>연월</th>
            <th>프로젝트</th>
            <th>직무</th>
            <th>MM</th>
            <th>단가</th>
            <th>배분 원가</th>
            <th>이익</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={`${item.yearMonth}-${item.projectId}-${item.assignmentId}`}
              data-testid={`personal-allocation-${item.yearMonth}-${item.projectId}-${item.assignmentId}`}
            >
              <td>{item.yearMonth}</td>
              <td>{item.projectName}</td>
              <td>{item.jobName ?? "–"}</td>
              <td>{item.mm.toFixed(2)}</td>
              <td>{won(item.unitPrice)}</td>
              <td>{won(item.allocatedCost)}</td>
              <td>{won(item.operatingProfit)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
