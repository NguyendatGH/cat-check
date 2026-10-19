import { createContext, useCallback, useContext, type ReactNode } from "react";
import { NO_ADMIN_CAPABILITIES, type AdminCapabilities, type AdminCapabilityKey } from "./adminAccess";

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

export function useAdminCan(): (key: AdminCapabilityKey) => boolean {
  const capabilities = useContext(AdminAccessContext);
  return useCallback((key: AdminCapabilityKey) => capabilities[key], [capabilities]);
}
