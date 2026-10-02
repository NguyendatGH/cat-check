import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/users/:userId` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (a) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck`. Trang chỉ hiển thị lại `userId` lấy từ URL —
 * một định danh mờ, KHÔNG phải PII, và vốn đã nằm trên thanh địa chỉ. Không gọi, không
 * đoán, không mock bất kỳ trường hồ sơ nào: p15 REQ-RBAC-01 bắt email/điện thoại phải
 * mask sẵn từ server và chỉ lộ qua L3 (`unmask`) có `reason` + tự mask lại sau 15 phút.
 */
export function AdminUserDetailPage() {
  const { t } = useTranslation("admin");
  const { userId } = useParams<{ userId: string }>();

  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.userDetail.title")}
        description={t("pages.userDetail.description")}
        specRef={userId === undefined ? undefined : t("pages.userDetail.subjectId", { id: userId })}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (a)"
        endpoints={[
          { code: "L2", signature: "GET /admin/users/{userId}" },
          { code: "L4", signature: "GET /admin/users/{userId}/cats" },
          { code: "L5", signature: "GET /admin/users/{userId}/scans" },
          { code: "L9", signature: "GET /admin/users/{userId}/credits" },
          { code: "L10", signature: "POST /admin/users/{userId}/credit-adjustments" },
          { code: "L11", signature: "GET /admin/users/{userId}/sessions" },
        ]}
        note={t("pages.userDetail.noApiNote")}
      />
    </div>
  );
}
