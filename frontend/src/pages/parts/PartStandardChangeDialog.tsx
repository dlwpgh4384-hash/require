import { PartEditDialog, type PartDialogProps } from "./PartEditDialog";

// UI-15-M5
export function PartStandardChangeDialog(
  props: PartDialogProps & { part: NonNullable<PartDialogProps["part"]> },
) {
  return <PartEditDialog {...props} mode="standards" />;
}
