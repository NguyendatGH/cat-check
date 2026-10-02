import { useTranslation } from "react-i18next";
import { EmptyState } from "@/shared/ui";

/**
 * Render TẠI CHỖ (không redirect) khi RequireRole thấy thiếu quyền — guard thứ 4/6
 * (p9 mục 6, bước "đủ role? thiếu → render 403 tại chỗ, KHÔNG redirect"). Phải khác UI với
 * ForbiddenPage vì đây và AuthLogin/UpsellPage là 3 loại "không vào được" khác nhau.
 */
export function ForbiddenPage() {
  const { t } = useTranslation("common");
  return <EmptyState title={t("forbidden.title")} description={t("forbidden.description")} />;
}
