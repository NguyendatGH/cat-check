import type { SessionUser } from "./model";

interface RawSessionUser {
  id: string;
  email: string;
  fullName: string;
  avatarUrl?: string | null;
  onboardingStatus: "ACCOUNT_ONLY" | "CAT_CREATED" | "SURVEY_DONE_OR_SKIPPED" | "COMPLETED";
}
interface RawSessionResponse {
  authenticated: boolean;
  user: RawSessionUser | null;
  roles: string[];
}

const NEXT_STEP_BY_STATUS: Record<RawSessionUser["onboardingStatus"], string | null> = {
  ACCOUNT_ONLY: "cat",
  CAT_CREATED: "health-survey",
  SURVEY_DONE_OR_SKIPPED: "disclaimer",
  COMPLETED: null,
};

const ONBOARDING_DONE: ReadonlySet<RawSessionUser["onboardingStatus"]> = new Set(["COMPLETED"]);

function toSessionUser(raw: RawSessionResponse): SessionUser | null {
  if (!raw.authenticated || !raw.user) return null;
  const { user } = raw;
  return {
    id: user.id,
    email: user.email,
    displayName: user.fullName,
    avatarUrl: user.avatarUrl ?? null,
    roles: raw.roles as SessionUser["roles"],
    onboarding: {
      completed: ONBOARDING_DONE.has(user.onboardingStatus),
      nextStep: NEXT_STEP_BY_STATUS[user.onboardingStatus],
    },
  };
}

export async function loadSession(): Promise<SessionUser | null> {
  try {
    const response = await fetch("/api/v1/auth/session", { credentials: "include" });
    if (!response.ok) return null;
    return toSessionUser((await response.json()) as RawSessionResponse);
  } catch {
    return null;
  }
}
