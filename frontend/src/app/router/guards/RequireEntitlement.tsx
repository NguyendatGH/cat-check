import type { ReactNode } from "react";
import { UpsellPage } from "@/pages/system/UpsellPage";

export interface RequireEntitlementProps {
  entitlement: string;
  children: ReactNode;
}

function checkHasEntitlement(): boolean {
  return true;
}

export function RequireEntitlement({ children }: RequireEntitlementProps) {
  const hasEntitlement = checkHasEntitlement();

  if (!hasEntitlement) {
    return <UpsellPage />;
  }

  return <>{children}</>;
}
