import { isApiError } from "@/shared/api";
import { ROLES } from "@/shared/auth/roles";


export type AdminCapabilityKey = keyof typeof ROLES;

export type AdminCapabilities = Readonly<Record<AdminCapabilityKey, boolean>>;

export const NO_ADMIN_CAPABILITIES: AdminCapabilities = Object.fromEntries(
  (Object.keys(ROLES) as AdminCapabilityKey[]).map((key) => [key, false]),
) as AdminCapabilities;

export type RoleCheck = (allowed: readonly string[]) => boolean;

export function computeCapabilities<K extends string>(
  table: Readonly<Record<K, readonly string[]>>,
  allows: RoleCheck,
): Record<K, boolean> {
  const result = {} as Record<K, boolean>;
  for (const key of Object.keys(table) as K[]) {
    result[key] = allows(table[key]);
  }
  return result;
}


export interface AdminNavItemBase {
  id: string;
  to: string;
  tab?: string;
  defaultTab?: boolean;
  end?: boolean;
  roles: readonly string[];
}

export interface AdminNavGroup<T extends AdminNavItemBase> {
  id: string;
  labelKey: string;
  items: readonly T[];
}

export function filterNavGroups<T extends AdminNavItemBase>(
  groups: readonly AdminNavGroup<T>[],
  allows: RoleCheck,
): AdminNavGroup<T>[] {
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item) => allows(item.roles)) }))
    .filter((group) => group.items.length > 0);
}

export interface NavLocation {
  pathname: string;
  search: string;
}

export function isNavItemActive(item: AdminNavItemBase, location: NavLocation): boolean {
  const path = location.pathname.replace(/\/+$/, "") || "/";
  const target = item.to.replace(/\/+$/, "") || "/";
  const pathMatches = item.end === true ? path === target : path === target || path.startsWith(`${target}/`);
  if (!pathMatches) {
    return false;
  }
  if (item.tab === undefined) {
    return true;
  }
  const currentTab = new URLSearchParams(location.search).get("tab");
  return currentTab === null || currentTab === "" ? item.defaultTab === true : currentTab === item.tab;
}

export function findActiveNavItem<T extends AdminNavItemBase>(
  groups: readonly AdminNavGroup<T>[],
  location: NavLocation,
): T | undefined {
  for (const group of groups) {
    const hit = group.items.find((item) => isNavItemActive(item, location));
    if (hit !== undefined) {
      return hit;
    }
  }
  return undefined;
}


export type AdminErrorKind =
  | "unauthenticated"
  | "forbidden"
  | "stale"
  | "preconditionRequired"
  | "conflict"
  | "notFound"
  | "gone"
  | "invalid"
  | "server"
  | "network"
  | "unknown";

export function classifyAdminError(error: unknown): AdminErrorKind {
  if (isApiError(error)) {
    const { status } = error;
    if (status === 401) return "unauthenticated";
    if (status === 403) return "forbidden";
    if (status === 412) return "stale";
    if (status === 428) return "preconditionRequired";
    if (status === 409) return "conflict";
    if (status === 404) return "notFound";
    if (status === 410) return "gone";
    if (status === 400 || status === 422) return "invalid";
    if (status >= 500) return "server";
    return "unknown";
  }
  if (error instanceof TypeError) {
    return "network";
  }
  return "unknown";
}

export function isAccessError(error: unknown): boolean {
  const kind = classifyAdminError(error);
  return kind === "unauthenticated" || kind === "forbidden";
}
