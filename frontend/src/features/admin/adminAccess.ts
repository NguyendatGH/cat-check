import { isApiError } from "@/shared/api";
import { ROLES } from "@/shared/auth/roles";

/**
 * Quyền hiển thị và phân loại lỗi cho khu vực quản trị — hàm thuần, không React.
 *
 * Bảng vai trò thật (`ROLES`, `hasAnyRole`) nằm ở `shared/auth/roles.ts`. ESLint `boundaries`
 * cấm feature/page import `app`, nên `AdminLayout` (thuộc `app`) tính sẵn bảng khả năng rồi
 * truyền xuống qua context ({@link AdminAccessProvider}). Các hàm ở đây nhận bảng vai trò và
 * hàm kiểm quyền làm tham số, nhờ vậy không sao chép danh sách vai trò của backend.
 */

/**
 * Khả năng mà các trang admin hỏi tới. Tên khoá trùng khoá của `ROLES` trong
 * `shared/auth/roles.ts`; `AdminLayout` gán `Record<keyof ROLES, boolean>` vào kiểu này, nên
 * nếu `ROLES` mất một khoá thì `tsc` báo lỗi ngay tại `AdminLayout`.
 */
export type AdminCapabilityKey = keyof typeof ROLES;

export type AdminCapabilities = Readonly<Record<AdminCapabilityKey, boolean>>;

/** Bảng khả năng rỗng: không có provider thì không hiện nút ghi nào. */
export const NO_ADMIN_CAPABILITIES: AdminCapabilities = Object.fromEntries(
  (Object.keys(ROLES) as AdminCapabilityKey[]).map((key) => [key, false]),
) as AdminCapabilities;

/** Kiểm "có một trong các vai trò được phép" — truyền `hasAnyRole` đã gắn sẵn vai trò người dùng. */
export type RoleCheck = (allowed: readonly string[]) => boolean;

/** Biến bảng `{ khoá: vai trò[] }` thành `{ khoá: boolean }`. */
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

// ------------------------------------------------------------------- menu

export interface AdminNavItemBase {
  /** Khoá duy nhất trong menu (React key). */
  id: string;
  /** Đường dẫn, lấy từ `ROUTE_PATTERNS`. */
  to: string;
  /** Giá trị `?tab=` khi nhiều mục cùng trỏ một trang có Tabs. */
  tab?: string;
  /** Mục này là tab mặc định khi URL không có `?tab=`. */
  defaultTab?: boolean;
  /** Chỉ khớp đúng đường dẫn, không khớp trang con. */
  end?: boolean;
  /** Vai trò được thấy mục này. */
  roles: readonly string[];
}

export interface AdminNavGroup<T extends AdminNavItemBase> {
  id: string;
  labelKey: string;
  items: readonly T[];
}

/** Giữ lại mục người dùng được thấy; bỏ nhóm không còn mục nào. */
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

/** Mục menu có đang được chọn không (dùng cho `aria-current="page"`). */
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

/** Mục đang chọn đầu tiên, để thanh trên di động hiện tên trang hiện tại. */
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

// ------------------------------------------------------------------- lỗi API

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

/**
 * Phân loại lỗi để trang chọn đúng thông báo.
 *
 * `fetch` ném `TypeError` khi mất mạng hoặc bị chặn CORS: đó là lỗi mạng, khác hẳn một
 * `ApiError` do server trả về. 412 là "bản đã đổi" (ETag lệch), 428 là thiếu `If-Match`.
 */
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

/** Lỗi thuộc nhóm "không đủ quyền" (401/403) — trang hiện thông báo quyền thay vì nút thử lại. */
export function isAccessError(error: unknown): boolean {
  const kind = classifyAdminError(error);
  return kind === "unauthenticated" || kind === "forbidden";
}
