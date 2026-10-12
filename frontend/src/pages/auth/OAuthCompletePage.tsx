import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Button, EmptyState, SkeletonLoader } from "@/shared/ui";
import { useAuthSessionQuery } from "@/features/auth";

export function OAuthCompletePage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const { data, isLoading, isError } = useAuthSessionQuery(true);

  useEffect(() => {
    if (data?.authenticated) {
      window.location.assign("/");
    }
  }, [data]);

  if (isLoading) {
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
