import { useTranslation } from "react-i18next";
import { Loader2 } from "lucide-react";
import {
  AdminPageHeader,
  AdminSection,
  AdminTableScroll,
  ApiErrorNote,
  MissingApiNotice,
  adminTableClass,
  adminTdClass,
  adminThClass,
  useAdminConsentPurposes,
  useAdminDataInventory,
} from "@/features/admin";

/**
 * `/admin/privacy/retention` — bảng tra chỉ-đọc cho DPO.
 *
 * NỐI API THẬT, nhưng KHÔNG phải endpoint admin:
 * - `GET /api/v1/privacy/data-inventory` (C5) — danh mục `data_inventory_item`
 * - `GET /api/v1/privacy/purposes` (C1) — danh mục `consent_purpose`
 *
 * Hai endpoint này trả **danh mục toàn cục**, không chứa dữ liệu của bất kỳ user nào
 * (đã đọc `PrivacyController`: `dataInventoryItemPort.findAllActive()` và
 * `consentService.listPurposes(locale)` đều không nhận `userId`), nên đọc chúng ở màn
 * quản trị không phải là mượn dữ liệu cá nhân của người đang đăng nhập.
 *
 * Phần SỬA được (`retention_policy`: thời hạn + cron) là L56/L57 và chưa có backend —
 * khai báo thẳng ở cuối trang.
 */
export function AdminPrivacyRetentionPage() {
  const { t } = useTranslation("admin");
  const inventory = useAdminDataInventory();
  const purposes = useAdminConsentPurposes();

  const inventoryItems = inventory.data?.items ?? [];
  const purposeItems = purposes.data?.items ?? [];

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.privacyRetention.title")}
        description={t("pages.privacyRetention.description")}
        specRef="GET /api/v1/privacy/data-inventory (C5) · GET /api/v1/privacy/purposes (C1)"
      />

      <AdminSection title={t("retention.inventoryTitle")} description={t("retention.inventoryDescription")}>
        {inventory.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : inventory.isError ? (
          <ApiErrorNote error={inventory.error} />
        ) : inventoryItems.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("retention.colCode")}</th>
                  <th className={adminThClass}>{t("retention.colCategory")}</th>
                  <th className={adminThClass}>{t("retention.colSensitivity")}</th>
                  <th className={adminThClass}>{t("retention.colLegalBasis")}</th>
                  <th className={adminThClass}>{t("retention.colPolicy")}</th>
                  <th className={adminThClass}>{t("retention.colStorage")}</th>
                  <th className={adminThClass}>{t("retention.colCrossBorder")}</th>
                  <th className={adminThClass}>{t("retention.colRecipient")}</th>
                </tr>
              </thead>
              <tbody>
                {inventoryItems.map((item) => (
                  <tr key={item.code}>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small font-bold">{item.code}</span>
                      <span className="block max-w-[32ch] pt-0.5 text-small text-text-tertiary">
                        {item.description}
                      </span>
                    </td>
                    <td className={adminTdClass}>{item.category}</td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">{item.sensitivity}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">{item.legalBasis}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">{item.retentionPolicyCode ?? "—"}</span>
                    </td>
                    <td className={adminTdClass}>{item.storageLocation ?? "—"}</td>
                    <td className={adminTdClass}>
                      <span
                        className={
                          item.crossBorder
                            ? "rounded-md bg-warning-bg px-2 py-0.5 text-small font-semibold text-warning-text"
                            : "rounded-md bg-background-alt px-2 py-0.5 text-small font-semibold text-text-secondary"
                        }
                      >
                        {item.crossBorder ? t("retention.crossBorderYes") : t("retention.crossBorderNo")}
                      </span>
                    </td>
                    <td className={adminTdClass}>{item.recipient ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        )}
      </AdminSection>

      <AdminSection title={t("retention.purposesTitle")} description={t("retention.purposesDescription")}>
        {purposes.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : purposes.isError ? (
          <ApiErrorNote error={purposes.error} />
        ) : purposeItems.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("retention.colCode")}</th>
                  <th className={adminThClass}>{t("retention.colPurposeLabel")}</th>
                  <th className={adminThClass}>{t("retention.colMandatory")}</th>
                  <th className={adminThClass}>{t("retention.colSensitive")}</th>
                  <th className={adminThClass}>{t("retention.colWithdrawEffect")}</th>
                  <th className={adminThClass}>{t("retention.colPhase")}</th>
                </tr>
              </thead>
              <tbody>
                {purposeItems.map((purpose) => (
                  <tr key={purpose.code}>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small font-bold">{purpose.code}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-semibold">{purpose.label}</span>
                      <span className="block max-w-[48ch] pt-0.5 text-small text-text-tertiary">
                        {purpose.description}
                      </span>
                    </td>
                    <td className={adminTdClass}>
                      {purpose.mandatory ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={adminTdClass}>
                      {purpose.sensitive ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">{purpose.withdrawEffect ?? "—"}</span>
                    </td>
                    <td className={adminTdClass}>{purpose.phase}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        )}
      </AdminSection>

      <MissingApiNotice
        specSection="p8 §8.4.12 (e)"
        endpoints={[
          { code: "L56", signature: "GET /admin/privacy/retention-policies" },
          { code: "L57", signature: "PATCH /admin/privacy/retention-policies/{code}" },
          { code: "L58", signature: "POST /admin/privacy/retention-policies/{code}/dry-run" },
        ]}
        note={t("pages.privacyRetention.noApiNote")}
      />
    </div>
  );
}
