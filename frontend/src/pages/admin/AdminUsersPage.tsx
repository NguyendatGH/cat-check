import { useTranslation } from "react-i18next";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/users` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (a) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck` (đã rà toàn bộ controller). Không mock dữ liệu,
 * không tự thêm endpoint: p17 §17.3.8 AD9 bắt mọi endpoint `/api/v1/admin/**` phải có
 * đúng một dòng trong bảng `ADMIN_CAPABILITIES` — việc đó thuộc backend.
 */
export function AdminUsersPage() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.users.title")}
        description={t("pages.users.description")}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (a)"
        endpoints={[
          { code: "L1", signature: "GET /admin/users" },
          { code: "L2", signature: "GET /admin/users/{userId}" },
          { code: "L3", signature: "POST /admin/users/{userId}/unmask" },
          { code: "L7", signature: "POST /admin/users/{userId}/lock" },
          { code: "L8", signature: "POST /admin/users/{userId}/unlock" },
        ]}
        note={t("pages.users.noApiNote")}
      />
    </div>
  );
}
