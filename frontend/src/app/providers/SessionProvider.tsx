import { useEffect, type ReactNode } from "react";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { SkeletonLoader } from "@/shared/ui";

/** Khớp `SessionResponse`/`SessionUser` thật của backend (identity/api/dto). */
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

/** Bước kế tiếp còn dở, khớp key của ONBOARDING_STEP_PATH (RequireOnboarding). */
const NEXT_STEP_BY_STATUS: Record<RawSessionUser["onboardingStatus"], string | null> = {
  ACCOUNT_ONLY: "cat",
  CAT_CREATED: "health-survey",
  SURVEY_DONE_OR_SKIPPED: null,
  COMPLETED: null,
};

/**
 * Mốc mở khoá app. KHÔNG phải `COMPLETED`: p4 §4.4 định nghĩa `COMPLETED` là "đã quét lần đầu",
 * mà màn quét (`/scan`) lại nằm BÊN TRONG vùng bị `RequireOnboarding` canh — nếu đòi `COMPLETED`
 * thì user không bao giờ tới được chỗ quét để trở thành `COMPLETED`: deadlock, bị đá ngược về
 * `/onboarding/cat` vĩnh viễn (đây chính là lỗi đã gặp thật sau khi thêm hồ sơ mèo). Wizard coi
 * như xong sau bước khảo sát; `disclaimer`/`activate` vẫn đi qua được trong luồng nhưng không
 * chặn vào app.
 */
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

/**
 * Guard bước 1 (p9 mục 6): chặn render router tới khi biết phiên đăng nhập (tránh nháy
 * màn hình login rồi lại vào app). TODO: thay fetch thô bên dưới bằng apiClient thật khi
 * schema.d.ts đã sinh từ OpenAPI backend (`npm run api:gen`) — session cookie HttpOnly +
 * CSRF, không có JWT/refresh token ở FE.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const status = useSessionStore((state) => state.status);
  const setSession = useSessionStore((state) => state.setSession);
  const setStatus = useSessionStore((state) => state.setStatus);

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
