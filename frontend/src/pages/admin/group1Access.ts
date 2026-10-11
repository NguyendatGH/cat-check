import { useCallback } from "react";
import { useAdminCan, type AdminCapabilityKey } from "@/features/admin";

/**
 * Khả năng mà nhóm trang người dùng, gói, mã kích hoạt và quyền riêng tư hỏi tới. Khoá nằm trong
 * `AdminCapabilityKey`, vốn lấy từ `ROLES`, nên không lệch với backend.
 *
 * Chỉ là quyền HIỂN THỊ: backend vẫn kiểm độc lập và trang vẫn xử lý 401/403.
 */
export const GROUP1_CAPABILITIES = [
  "users",
  "userLock",
  "activationCodes",
  "activationCodesWrite",
  "packagesRead",
  "packagesWrite",
  "privacyRequests",
  "privacyExport",
  "privacyRequestsCreate",
  "privacyErasureApproval",
  "retention",
  "retentionWrite",
] as const;

export type Group1Capability = Extract<AdminCapabilityKey, (typeof GROUP1_CAPABILITIES)[number]>;

/** `can(key)` cho các khoá của nhóm; không có provider thì luôn `false`. */
export function useGroup1Can(): (key: Group1Capability) => boolean {
  const can = useAdminCan();
  return useCallback((key: Group1Capability) => can(key), [can]);
}
