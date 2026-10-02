import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Button, EmptyState, SkeletonLoader } from "@/shared/ui";
import { useAuthSessionQuery } from "@/features/auth";

/**
 * `/auth/oauth/complete` (p9 §9.4.3 #7, P15 §15.3.3). Google OAuth là redirect toàn trang
 * do backend điều khiển (p11 §11.1.4) — SPA không tự gọi API OAuth, chỉ cần kiểm tra lại
 * phiên khi Google trả về đây rồi vào app.
 *
 * ⚠ Ghi chú tích hợp (xem `docs/handovers/A1-fe.md`): `GoogleOAuth2Configuration` hiện
 * KHÔNG cấu hình `successHandler` riêng nên Spring Security mặc định có thể không redirect
 * đúng về route này (và `MockOAuth2Controller` dev redirect thẳng về `/`) — trang này vẫn
 * cần tồn tại đúng hợp đồng p9 cho khi W3 nối `successHandler` trỏ về đây.
 */
export function OAuthCompletePage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const { data, isLoading, isError } = useAuthSessionQuery(true);

  useEffect(() => {
    if (data?.authenticated) {
      window.location.assign("/dashboard");
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
          <Button type="button" onClick={() => { void navigate("/auth/login"); }}>
            {t("oauthComplete.failed.cta")}
          </Button>
        }
      />
    );
  }

  return null;
}
