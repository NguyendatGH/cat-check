import { create } from "zustand";
import { ROLES } from "@/shared/auth/roles";
import type { SessionStatus, SessionUser } from "./model";

interface SessionStore {
  status: SessionStatus;
  user: SessionUser | null;
  setSession: (user: SessionUser | null) => void;
  setStatus: (status: SessionStatus) => void;
}

export const useSessionStore = create<SessionStore>((set) => ({
  status: "loading",
  user: null,
  setSession: (user) => {
    set({ user, status: user ? "authenticated" : "anonymous" });
  },
  setStatus: (status) => {
    set({ status });
  },
}));

export function hasRole(user: SessionUser | null, roles: readonly string[]): boolean {
  if (!user) return false;
  return user.roles.some((role) => roles.includes(role));
}

export function hasNonAdminRole(user: SessionUser | null): boolean {
  if (!user) return false;
  const adminRoles: readonly string[] = ROLES.adminPortal;
  return !user.roles.some((role) => adminRoles.includes(role));
}
