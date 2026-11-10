import type { MenuItem } from "./services/menus";

export interface MenuNode {
  item: MenuItem;
  children: MenuNode[];
  group: boolean;
}
export function visibleTree(
  items: MenuItem[],
  parentIds: string[],
  mobile: boolean,
): MenuNode[] {
  const parents = new Set(parentIds);
  const children = new Map<string, MenuItem[]>();
  for (const item of items) {
    const parent = item.upperMenuId?.trim() ? item.upperMenuId : "";
    const siblings = children.get(parent) ?? [];
    siblings.push(item);
    children.set(parent, siblings);
  }
  const visit = (parent: string, visited: Set<string>): MenuNode[] =>
    (children.get(parent) ?? [])
      .filter((item) => !visited.has(item.menuId))
      .sort(
        (left, right) =>
          left.sortOrd - right.sortOrd ||
          (left.menuId < right.menuId
            ? -1
            : left.menuId > right.menuId
              ? 1
              : 0),
      )
      .flatMap((item) => {
        const group = parents.has(item.menuId) || !item.scrnId?.trim();
        const descendants = visit(
          item.menuId,
          new Set([...visited, item.menuId]),
        );
        if (group && !descendants.length) return [];
        if (!group && mobile && item.pcOnlyYn === "Y") return [];
        return [{ item, children: descendants, group }];
      });
  return visit("", new Set());
}
export function firstScreen(nodes: MenuNode[]): MenuItem | undefined {
  for (const node of nodes) {
    if (!node.group) return node.item;
    const child = firstScreen(node.children);
    if (child) return child;
  }
  return undefined;
}
export function screenPath(scrnId: string) {
  return `/screens/${encodeURIComponent(scrnId)}`;
}
