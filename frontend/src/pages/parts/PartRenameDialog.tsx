import { PartEditDialog, type PartDialogProps } from "./PartEditDialog";

// UI-15-M6
export function PartRenameDialog(
  props: PartDialogProps & { part: NonNullable<PartDialogProps["part"]> },
) {
  return <PartEditDialog {...props} mode="rename" />;
}
