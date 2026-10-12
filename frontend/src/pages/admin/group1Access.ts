import { useCallback } from "react";
import { useAdminCan, type AdminCapabilityKey } from "@/features/admin";

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

export function useGroup1Can(): (key: Group1Capability) => boolean {
  const can = useAdminCan();
  return useCallback((key: Group1Capability) => can(key), [can]);
}
