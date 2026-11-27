import type { PersonalCostProfitContribution } from "./personalCostProfitTypes";
import { Money, type MoneyUnit } from "../../shared/list";

const grades = ["초급", "중급", "고급", "특급"] as const;

/**
 * 시안 UI-03 「직무·등급별 인당 공헌이익」 접는 카드. 칸 = 「금액 (인원)」, 없으면 「–」.
 * 전체 칸 = 그 직무 칸들의 (인당 값 × 인원) 합 ÷ 인원 합.
 */
export function PersonalCostProfitContributions({
  items,
  unit,
}: {
  items: PersonalCostProfitContribution[];
  unit: MoneyUnit;
}) {
  const jobs = [
    ...new Map(items.map((item) => [item.jobCode, item.jobName])).entries(),
  ];
  const cell = (value: number | null, people: number) =>
    value == null || people === 0 ? (
      <span className="ls-cap">–</span>
    ) : (
      <>
        <Money value={value} unit={unit} />{" "}
        <span className="ls-cap">({people})</span>
      </>
    );
  return (
    <details
      className="personal-cost-profit-card"
      data-testid="personal-cost-profit-contributions"
    >
      <summary data-testid="personal-cost-profit-contributions-toggle">
        직무·등급별 인당 공헌이익{" "}
        <span className="ls-cap">(인당 평균 영업이익 · 단위 {unit})</span>
      </summary>
      <div className="ls-twrap personal-cost-profit-free">
        <table className="ls-t">
          <thead>
            <tr>
              <th className="ls-sc">직무</th>
              {grades.map((grade) => (
                <th key={grade} className="ls-right">
                  {grade}
                </th>
              ))}
              <th className="ls-right">전체</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map(([code, name]) => {
              const rows = items.filter((item) => item.jobCode === code);
              const people = rows.reduce((sum, row) => sum + row.people, 0);
              const total = rows.reduce(
                (sum, row) => sum + row.contributionPerPerson * row.people,
                0,
              );
              return (
                <tr key={code} data-testid={`personal-contribution-${code}`}>
                  {/* 시안 td.sc: 몸통 칸이라 머리 칸(th) 모양·고정이 아니라 아래·오른쪽 선이 있는 첫 칸(55번 5-1 표 테두리). */}
                  <td className="ls-sc">{name}</td>
                  {grades.map((grade) => {
                    const found = rows.find((row) => row.gradeName === grade);
                    return (
                      <td key={grade} className="ls-right">
                        {found
                          ? cell(found.contributionPerPerson, found.people)
                          : cell(null, 0)}
                      </td>
                    );
                  })}
                  <td className="ls-right" data-testid={`personal-contribution-${code}-all`}>
                    {cell(people ? total / people : null, people)}
                  </td>
                </tr>
              );
            })}
            {jobs.length === 0 && (
              <tr>
                <td colSpan={6}>조회된 직무·등급 자료가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </details>
  );
}
