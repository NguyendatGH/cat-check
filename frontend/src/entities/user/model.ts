
export type Role = "USER" | "ADMIN_SUPPORT" | "ADMIN_CATALOG" | "ADMIN_SUPER" | "DPO" | "MODERATOR" | "VET";
export type AdminRole = Exclude<Role, "USER" | "VET">;

export interface OnboardingState {
  completed: boolean;
  nextStep: string | null;
}

export interface SessionUser {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string | null;
  roles: Role[];
  onboarding: OnboardingState;
}

export type SessionStatus = "idle" | "loading" | "authenticated" | "anonymous";
