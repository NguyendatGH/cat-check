import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { loadSession, useSessionStore } from "@/entities/user";
import { playSplash } from "@/shared/lib/motion/splash";
import { resolveRedirectTarget } from "@/shared/auth/landing";
import { Button, EmptyState, SkeletonLoader } from "@/shared/ui";
import { useAuthFlowStore, useAuthSessionQuery } from "@/features/auth";

export function OAuthCompletePage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // Tài khoản có TOTP: backend chưa tạo phiên, chuyển sang bước 2 của /auth/login.
  const mfaRequired = searchParams.get("mfa") === "required";
  const setPendingLoginMfaMethods = useAuthFlowStore((s) => s.setPendingLoginMfaMethods);
  const { data, isLoading, isError } = useAuthSessionQuery(!mfaRequired);

  const queryClient = useQueryClient();
  const setSession = useSessionStore((state) => state.setSession);
  const started = useRef(false);

  useEffect(() => {
    if (!mfaRequired) return;
    setPendingLoginMfaMethods(["TOTP"]);
    void navigate("/auth/login", { replace: true });
  }, [mfaRequired, navigate, setPendingLoginMfaMethods]);

  useEffect(() => {
    if (!data?.authenticated || started.current) return;
    started.current = true;
    const next = searchParams.get("next");
    playSplash(loadSession(), (user) => {
      queryClient.removeQueries();
      setSession(user);
      void navigate(user ? resolveRedirectTarget(next, user.roles) : "/auth/login", { replace: true });
    });
  }, [data, navigate, queryClient, searchParams, setSession]);

  if (mfaRequired || isLoading) {
    return (
      <div className="flex flex-col items-center gap-4 py-12">
        <SkeletonLoader shape="circle" />
        <p className="text-caption text-text-secondary">{t("oauthComplete.checking")}</p>
      </div>
    );
  }

  if (isError || !data?.authenticated) {
    return (
      <EmptyState
        title={t("oauthComplete.failed.title")}
        description={t("oauthComplete.failed.description")}
        action={
          <Button
            type="button"
            onClick={() => {
              void navigate("/auth/login");
            }}
          >
            {t("oauthComplete.failed.cta")}
          </Button>
        }
      />
    );
  }

  return null;
}
