import { hasRole, useSessionStore } from "@/entities/user";
import { ROLES } from "@/shared/auth/roles";

export type Group2Capability = Extract<
  keyof typeof ROLES,
  "products" | "colorChart" | "careTipsWrite" | "careTipsPublish" | "communityModeration"
>;

export function useGroup2Can(): (key: Group2Capability) => boolean {
  const user = useSessionStore((state) => state.user);
  return (key) => hasRole(user, ROLES[key]);
}
