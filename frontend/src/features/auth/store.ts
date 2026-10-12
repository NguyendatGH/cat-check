import { create } from "zustand";
import type { PendingRegistration } from "./types";

interface AuthFlowStore {
  pendingRegistration: PendingRegistration | null;
  setPendingRegistration: (draft: PendingRegistration | null) => void;

  passwordResetEmail: string | null;
  setPasswordResetEmail: (email: string | null) => void;

  passwordResetTicket: string | null;
  setPasswordResetTicket: (ticket: string | null) => void;

  pendingLoginMfaMethods: string[] | null;
  setPendingLoginMfaMethods: (methods: string[] | null) => void;

  reset: () => void;
}

export const useAuthFlowStore = create<AuthFlowStore>((set) => ({
  pendingRegistration: null,
  setPendingRegistration: (pendingRegistration) => {
    set({ pendingRegistration });
  },

  passwordResetEmail: null,
  setPasswordResetEmail: (passwordResetEmail) => {
    set({ passwordResetEmail });
  },

  passwordResetTicket: null,
  setPasswordResetTicket: (passwordResetTicket) => {
    set({ passwordResetTicket });
  },

  pendingLoginMfaMethods: null,
  setPendingLoginMfaMethods: (pendingLoginMfaMethods) => {
    set({ pendingLoginMfaMethods });
  },

  reset: () => {
    set({
      pendingRegistration: null,
      passwordResetEmail: null,
      passwordResetTicket: null,
      pendingLoginMfaMethods: null,
    });
  },
}));
