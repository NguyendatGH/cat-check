import { useTranslation } from "react-i18next";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/privacy/requests` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (e) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck` (đã rà toàn bộ controller). Không mock dữ liệu,
 * không tự thêm endpoint: p17 §17.3.8 AD9 bắt mọi endpoint `/api/v1/admin/**` phải có
 * đúng một dòng trong bảng `ADMIN_CAPABILITIES` — việc đó thuộc backend.
 */
export function AdminPrivacyRequestsPage() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.privacyRequests.title")}
        description={t("pages.privacyRequests.description")}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (e)"
        endpoints={[
          { code: "L49", signature: "GET /admin/privacy/requests" },
          { code: "L50", signature: "GET /admin/privacy/requests/{id}" },
          { code: "L51", signature: "POST /admin/privacy/requests/{id}/transition" },
          { code: "L52", signature: "POST /admin/privacy/requests/{id}/export" },
          { code: "L53", signature: "POST /admin/privacy/requests/{id}/approve-erasure" },
          { code: "L54", signature: "POST /admin/privacy/requests" },
        ]}
        note={t("pages.privacyRequests.noApiNote")}
      />
    </div>
  );
}
