import { useTranslation } from "react-i18next";
import { EmptyState } from "@/shared/ui";

/**
 * Trang dùng chung cho MỌI route Phase 2/3 khi featureFlags tắt (p9 §9.4.3 mục E, và
 * /admin/places, /admin/community/reports ở mục D). Route vẫn đăng ký thật trong router,
 * chỉ đổi element sang trang này.
 */
export function ComingSoonPage() {
  const { t } = useTranslation("common");
  return <EmptyState title={t("comingSoon.title")} description={t("comingSoon.description")} />;
}
