import type { ReactNode } from "react";
import { Dialog, InlineConfirmation, Toast } from "../../shared/feedback/Dialog";
import "./userPreferences.css";

// 화면 설정 창은 공통 부품(shared/feedback, FR-0106)을 그대로 쓴다. 이름과 시험 ID는 기존 것을 유지한다.
export function UserPreferenceDialog({
  title,
  children,
  onClose,
  testId = "preference-dialog",
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  testId?: string;
}) {
  return (
    <Dialog title={title} onClose={onClose} testId={testId}>
      {children}
    </Dialog>
  );
}

export function UserPreferenceConfirmation({
  title,
  description,
  onCancel,
  onProceed,
  destructive = false,
}: {
  title: string;
  description: string;
  onCancel: () => void;
  onProceed: () => void;
  destructive?: boolean;
}) {
  return (
    <InlineConfirmation
      title={title}
      description={description}
      onCancel={onCancel}
      onProceed={onProceed}
      destructive={destructive}
      proceedLabel={destructive ? "실행" : "나가기"}
      testId="preference-confirmation"
    />
  );
}

export function UserPreferenceToast({
  message,
  onDismiss,
}: {
  message: string;
  onDismiss: () => void;
}) {
  return <Toast message={message} onDismiss={onDismiss} testId="preference-success" />;
}
