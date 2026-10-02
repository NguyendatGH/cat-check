import { useTranslation } from "react-i18next";
import { AdminPageHeader, MissingApiNotice } from "@/features/admin";

/**
 * `/admin/activation-codes` — khung thật, CHƯA có backend.
 *
 * Endpoint sở hữu màn này nằm ở p8 §8.4.12 (b) nhưng chưa được hiện thực trong
 * `backend/src/main/java/com/catcheck` (đã rà toàn bộ controller). Không mock dữ liệu,
 * không tự thêm endpoint: p17 §17.3.8 AD9 bắt mọi endpoint `/api/v1/admin/**` phải có
 * đúng một dòng trong bảng `ADMIN_CAPABILITIES` — việc đó thuộc backend.
 */
export function AdminActivationCodesPage() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.activationCodes.title")}
        description={t("pages.activationCodes.description")}
      />
      <MissingApiNotice
        specSection="p8 §8.4.12 (b)"
        endpoints={[
          { code: "L19", signature: "GET /admin/activation-codes" },
          { code: "L20", signature: "POST /admin/activation-codes/batch" },
          { code: "L21", signature: "GET /admin/activation-codes/batches" },
          { code: "L22", signature: "GET /admin/activation-codes/batches/{batchId}/csv" },
          { code: "L23", signature: "POST /admin/activation-codes/{codeId}/void" },
          { code: "L24", signature: "POST /admin/activation-codes/batches/{batchId}/void" },
        ]}
        note={t("pages.activationCodes.noApiNote")}
      />
    </div>
  );
}
