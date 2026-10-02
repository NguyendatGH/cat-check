/**
 * Model tối thiểu cho phiên đăng nhập hiện tại (session).
 * TODO: đồng bộ field thật với API /api/v1/auth/session ở milestone kế — đây là khung M0.
 */

export type AdminRole = "ADMIN" | "ADMIN_SUPER" | "DPO";

export type Role = "USER" | AdminRole;

export interface OnboardingState {
  completed: boolean;
  /** Bước còn dở khi completed=false, ví dụ "cat" | "health-survey" | "disclaimer" | "activate". */
  nextStep: string | null;
}

export interface SessionUser {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  onboarding: OnboardingState;
}

export type SessionStatus = "idle" | "loading" | "authenticated" | "anonymous";
