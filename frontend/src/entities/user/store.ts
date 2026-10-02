import { create } from "zustand";
import type { SessionStatus, SessionUser } from "./model";

/**
 * Store phiên đăng nhập tối thiểu cho M0.
 * TODO: nối với shared/api thật (bootstrap qua cookie session + CSRF) ở milestone kế.
 * Guard SessionProvider (app/providers/SessionProvider.tsx) chặn render router tới khi
 * status khác "idle"/"loading".
 */
interface SessionStore {
  status: SessionStatus;
  user: SessionUser | null;
  setSession: (user: SessionUser | null) => void;
  setStatus: (status: SessionStatus) => void;
}

export const useSessionStore = create<SessionStore>((set) => ({
  status: "idle",
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
