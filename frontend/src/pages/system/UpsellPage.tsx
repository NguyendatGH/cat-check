import { useTranslation } from "react-i18next";
import { EmptyState } from "@/shared/ui";

/**
 * Render TẠI CHỖ trong layout của route khi RequireEntitlement thấy gói không có tính
 * năng — guard thứ 5/6 (p9 mục 6). Khác UI với ForbiddenPage (thiếu role) và màn hình
 * redirect login (chưa đăng nhập) — 3 loại "không vào được" PHẢI khác nhau.
 * TODO: thay bằng UpsellPanel thật (nêu gói cần nâng cấp) khi có business logic entitlement.
 */
export function UpsellPage() {
  const { t } = useTranslation("common");
  return <EmptyState title={t("upsell.title")} description={t("upsell.description")} />;
}
