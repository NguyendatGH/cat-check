import { useTranslation } from "react-i18next";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/notifications/outbox` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (f) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck` (đã rà toàn bộ controller). Không mock dữ liệu,
 * không tự thêm endpoint: p17 §17.3.8 AD9 bắt mọi endpoint `/api/v1/admin/**` phải có
 * đúng một dòng trong bảng `ADMIN_CAPABILITIES` — việc đó thuộc backend.
 */
export function AdminNotificationsOutboxPage() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.outbox.title")}
        description={t("pages.outbox.description")}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (f)"
        endpoints={[
          { code: "L66", signature: "GET /admin/notifications/outbox" },
          { code: "L67", signature: "POST /admin/notifications/outbox/{id}/resend" },
        ]}
        note={t("pages.outbox.noApiNote")}
      />
    </div>
  );
}
