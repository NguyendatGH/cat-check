import { useTranslation } from "react-i18next";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/packages` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (b) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck` (đã rà toàn bộ controller). Không mock dữ liệu,
 * không tự thêm endpoint: p17 §17.3.8 AD9 bắt mọi endpoint `/api/v1/admin/**` phải có
 * đúng một dòng trong bảng `ADMIN_CAPABILITIES` — việc đó thuộc backend.
 */
export function AdminPackagesPage() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.packages.title")}
        description={t("pages.packages.description")}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (b)"
        endpoints={[
          { code: "L25", signature: "GET /admin/package-plans" },
          { code: "L26", signature: "PATCH /admin/package-plans/{code}" },
        ]}
        note={t("pages.packages.noApiNote")}
      />
    </div>
  );
}
