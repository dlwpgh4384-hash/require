import { PartEditDialog, type PartDialogProps } from "./PartEditDialog";

// UI-15-M8
export function PartStopDialog(
  props: PartDialogProps & { part: NonNullable<PartDialogProps["part"]> },
) {
  return <PartEditDialog {...props} mode="stop" />;
}
