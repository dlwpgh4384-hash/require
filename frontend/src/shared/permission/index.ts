export {
  PERMISSION_ACTIONS,
  screenPermission,
  usePermission,
  useCanViewScreen,
  useSensitiveView,
} from "./permission";
export type { PermissionAction, ScreenPermission } from "./permission";
export {
  READONLY_NOTICE,
  ScreenPermissionGate,
  applyScreenPermission,
} from "./ScreenPermissionGate";
