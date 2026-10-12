import { useEffect, type ReactNode } from "react";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { usePushSubscriptionRefresh } from "@/features/notification";
import { SkeletonLoader } from "@/shared/ui";

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

export function SessionProvider({ children }: { children: ReactNode }) {
  const status = useSessionStore((state) => state.status);
  const setSession = useSessionStore((state) => state.setSession);
  const setStatus = useSessionStore((state) => state.setStatus);
  const signedIn = useSessionStore((state) => state.user !== null);
  usePushSubscriptionRefresh(signedIn);

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");

    async function bootstrap() {
      try {
        const response = await fetch("/api/v1/auth/session", { credentials: "include" });
        if (!response.ok) {
          if (!cancelled) setSession(null);
          return;
        }
        const raw = (await response.json()) as RawSessionResponse;
        if (!cancelled) setSession(toSessionUser(raw));
      } catch {
        if (!cancelled) setSession(null);
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [setSession, setStatus]);

  if (status === "idle" || status === "loading") {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-background">
        <SkeletonLoader shape="circle" />
      </div>
    );
  }

  return <>{children}</>;
}
