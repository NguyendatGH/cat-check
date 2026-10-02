import { useTranslation } from "react-i18next";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/staff` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (a) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck` (đã rà toàn bộ controller). Không mock dữ liệu,
 * không tự thêm endpoint: p17 §17.3.8 AD9 bắt mọi endpoint `/api/v1/admin/**` phải có
 * đúng một dòng trong bảng `ADMIN_CAPABILITIES` — việc đó thuộc backend.
 */
export function AdminStaffPage() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.staff.title")}
        description={t("pages.staff.description")}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (a)"
        endpoints={[
          { code: "L13", signature: "POST /admin/users/{userId}/roles" },
          { code: "L14", signature: "DELETE /admin/users/{userId}/roles/{role}" },
          { code: "L15", signature: "POST /admin/users/{userId}/mfa/totp/reset" },
          { code: "L16", signature: "POST /admin/totp-reset-requests/{requestId}/approve" },
          { code: "L17", signature: "GET /admin/totp-reset-requests" },
        ]}
        note={t("pages.staff.noApiNote")}
      />
    </div>
  );
}
