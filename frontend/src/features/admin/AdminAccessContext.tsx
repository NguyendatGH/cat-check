import { createContext, useCallback, useContext, type ReactNode } from "react";
import { NO_ADMIN_CAPABILITIES, type AdminCapabilities, type AdminCapabilityKey } from "./adminAccess";

/**
 * Bảng khả năng của người đang đăng nhập, do `AdminLayout` tính từ `ROLES` (app/router/roles.ts)
 * rồi đặt vào đây. Trang admin chỉ hỏi `can("scanImage")`, không tự giữ danh sách vai trò.
 *
 * Đây chỉ là quyền HIỂN THỊ: server vẫn kiểm độc lập (AdminGuard), nên trang vẫn phải xử lý
 * 401/403 dù nút đã được ẩn.
 */
const AdminAccessContext = createContext<AdminCapabilities>(NO_ADMIN_CAPABILITIES);

export function AdminAccessProvider({
  capabilities,
  children,
}: {
  capabilities: AdminCapabilities;
  children: ReactNode;
}) {
  return <AdminAccessContext.Provider value={capabilities}>{children}</AdminAccessContext.Provider>;
}

/** `can(key)` — người dùng có vai trò cho khả năng này không. Không có provider thì luôn `false`. */
export function useAdminCan(): (key: AdminCapabilityKey) => boolean {
  const capabilities = useContext(AdminAccessContext);
  return useCallback((key: AdminCapabilityKey) => capabilities[key], [capabilities]);
}
