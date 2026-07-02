import { NAV } from "./nav.config";

const ALL_ROUTES: string[] = NAV.flatMap(({ item }) => [
  item.route,
  ...(item.children?.map((c) => c.route) ?? []),
]);

/** The nav route that best matches the current path (longest prefix wins). */
export function activeKey(path: string): string {
  if (path === "/") return "/";
  const matches = ALL_ROUTES.filter((r) => r !== "/" && (path === r || path.startsWith(r + "/")));
  return matches.sort((a, b) => b.length - a.length)[0] ?? "/";
}

/** Parent item route whose submenu should be open for this path, or null. */
export function expandedParent(path: string): string | null {
  for (const { item } of NAV) {
    if (!item.children) continue;
    const hasDistinctChild = item.children.some(
      (c) => c.route !== item.route && (path === c.route || path.startsWith(c.route + "/")),
    );
    if (hasDistinctChild) return item.route;
    if (path === item.route || path.startsWith(item.route + "/")) return item.route;
  }
  return null;
}
