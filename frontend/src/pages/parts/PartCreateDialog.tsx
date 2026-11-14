import { PartEditDialog, type PartDialogProps } from "./PartEditDialog";

// UI-15-M1
export function PartCreateDialog(props: PartDialogProps) {
  return <PartEditDialog {...props} mode="create" />;
}
