import type { ReactNode } from "react";
import "./list.css";

/** 시안 .kpis: 요약 칸 묶음. 칸 배치는 시안과 같고, 화면마다 더할 배치는 className으로 준다. */
export function KpiCards({
  "aria-label": label,
  testId,
  className,
  children,
}: {
  "aria-label": string;
  testId?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={className ? `ls-kpis ${className}` : "ls-kpis"}
      role="group"
      aria-label={label}
      data-testid={testId}
    >
      {children}
    </div>
  );
}

/**
 * 시안 kpi(l, v, d, to): 이름 · 값 · 보조 문구 한 칸.
 * onClick을 주면 시안처럼 누를 수 있는 단추(button.kpi)로, 아니면 그냥 칸(div.kpi)으로 그린다.
 */
export function KpiCard({
  label,
  value,
  description,
  onClick,
  testId,
}: {
  label: ReactNode;
  value: ReactNode;
  description?: ReactNode;
  onClick?: () => void;
  testId?: string;
}) {
  const body = (
    <>
      <span className="l">{label}</span>
      <span className="v">{value}</span>
      <span className="d">{description}</span>
    </>
  );
  return onClick ? (
    <button
      type="button"
      className="ls-kpi ls-kpi-go"
      data-testid={testId}
      onClick={onClick}
    >
      {body}
    </button>
  ) : (
    <div className="ls-kpi" data-testid={testId}>
      {body}
    </div>
  );
}
