import { hasRole, useSessionStore } from "@/entities/user";
import { ROLES } from "@/shared/auth/roles";

/** Khả năng ghi của nhóm danh mục và nội dung. Khoá và vai trò lấy thẳng từ `ROLES`, không sao chép. */
export type Group2Capability = Extract<
  keyof typeof ROLES,
  "products" | "colorChart" | "careTipsWrite" | "careTipsPublish" | "communityModeration"
>;

/** `can(key)` — người dùng hiện tại có vai trò ghi cho khả năng này không. */
export function useGroup2Can(): (key: Group2Capability) => boolean {
  const user = useSessionStore((state) => state.user);
  return (key) => hasRole(user, ROLES[key]);
}
