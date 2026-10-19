import { useAdminCan } from "@/features/admin";
import type { ROLES } from "@/shared/auth/roles";

export type Group2Capability = Extract<
  keyof typeof ROLES,
  "products" | "colorChart" | "careTipsWrite" | "careTipsPublish" | "communityModeration"
>;

/** Narrowed view of {@link useAdminCan}; stable across renders while capabilities are unchanged. */
export function useGroup2Can(): (key: Group2Capability) => boolean {
  return useAdminCan();
}
