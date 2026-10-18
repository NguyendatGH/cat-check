import i18n from "@/shared/i18n/i18n";
import type { SessionUser } from "./model";

interface RawSessionUser {
  id: string;
  email: string;
  fullName: string;
  avatarUrl?: string | null;
  locale?: string | null;
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
  SURVEY_DONE_OR_SKIPPED: null,
  COMPLETED: null,
};

const ONBOARDING_DONE: ReadonlySet<RawSessionUser["onboardingStatus"]> = new Set([
  "SURVEY_DONE_OR_SKIPPED",
  "COMPLETED",
]);

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
    const raw = (await response.json()) as RawSessionResponse;
    // Thiết bị mới chưa có lựa chọn trong localStorage: lấy ngôn ngữ từ hồ sơ trên server.
    // Trang Cài đặt ngôn ngữ không gọi hàm này nên không vòng lặp với PATCH hồ sơ của nó.
    const locale = raw.user?.locale;
    if (locale && locale !== i18n.resolvedLanguage) void i18n.changeLanguage(locale);
    return toSessionUser(raw);
  } catch {
    return null;
  }
}
