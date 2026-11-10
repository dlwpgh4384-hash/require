import type { ComponentProps, ReactNode } from "react";
import { InterfaceIcon } from "./InterfaceIcon";

interface StateNoticeProps {
  icon: ComponentProps<typeof InterfaceIcon>["name"];
  title: string;
  description: string;
  testId?: string;
  tone?: "neutral" | "error" | "loading";
  children?: ReactNode;
}

export function StateNotice({
  icon,
  title,
  description,
  testId,
  tone = "neutral",
  children,
}: StateNoticeProps) {
  return (
    <div
      className={`notice notice-${tone}`}
      data-testid={testId}
      role={tone === "loading" ? "status" : undefined}
    >
      <div className="notice-icon">
        <InterfaceIcon name={icon} />
      </div>
      <h2>{title}</h2>
      <p className="notice-description">{description}</p>
      {tone === "loading" && (
        <div className="loading-track" aria-hidden="true">
          <span />
        </div>
      )}
      {children}
    </div>
  );
}
