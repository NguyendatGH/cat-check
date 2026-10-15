import type { ReactNode } from "react";
import { useEntitlement } from "@/features/credit";
import { UpsellPage } from "@/pages/system/UpsellPage";

/** Boolean feature flags on `/entitlements/me` that a route can be gated on. */
export type EntitlementFeature = "hasHistory" | "hasTrend" | "hasReminder" | "hasExport" | "storeImage";

export interface RequireEntitlementProps {
  entitlement: EntitlementFeature;
  children: ReactNode;
}

/**
 * Client-side UX gate only; the backend enforces entitlements on every call.
 * While the entitlement is loading nothing is rendered; if it cannot be loaded
 * the children render and the server remains the authority.
 */
export function RequireEntitlement({ entitlement, children }: RequireEntitlementProps) {
  const { data, isPending, isError } = useEntitlement();

  if (isPending) return null;
  if (!isError && !data[entitlement]) {
    return <UpsellPage />;
  }

  return <>{children}</>;
}
