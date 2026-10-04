import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ArrowRight, CircleCheck, CircleX, Loader2 } from "lucide-react";
import { AdminPageHeader, AdminSection, ApiErrorNote, useAdminMetrics, useAdminSystemStatus } from "@/features/admin";
import { formatDate } from "@/shared/lib/format/formatDate";

/**
 * `/admin` — tổng quan khu vực quản trị.
 *
 * NỐI API THẬT: `GET /api/v1/admin/system-status` (`admin/api/SystemStatusController`) —
 * endpoint này chỉ trả `databaseReachable` + `checkedAt`, javadoc của nó ghi rõ nó KHÔNG
 * thay `/actuator/health` và không phải business logic đầy đủ. Vì vậy trang này không
 * gọi nó là "sức khoẻ hệ thống".
 *
 * Chỉ số vận hành thật (L63 `GET /admin/metrics/dashboard`) được đọc trực tiếp từ PostgreSQL.
 */

/** Liên kết nhanh tới các màn ĐÃ nối API thật. Đường dẫn khớp `app/router/routes.ts`. */
const WIRED_LINKS = [
  { to: "/admin/ph-color-chart", labelKey: "dashboard.linkPhColorChart", specKey: "dashboard.specPhColorChart" },
  { to: "/admin/content", labelKey: "dashboard.linkContent", specKey: "dashboard.specContent" },
  { to: "/admin/privacy/retention", labelKey: "dashboard.linkRetention", specKey: "dashboard.specRetention" },
  { to: "/admin/me/security", labelKey: "dashboard.linkMeSecurity", specKey: "dashboard.specMeSecurity" },
] as const;

export function AdminDashboardPage() {
  const { t } = useTranslation("admin");
  const status = useAdminSystemStatus();
  const metrics = useAdminMetrics();

  return (
    <div className="flex max-w-5xl flex-col gap-5">
      <AdminPageHeader title={t("pages.dashboard.title")} description={t("pages.dashboard.description")} />

      <AdminSection title={t("dashboard.statusTitle")} description={t("dashboard.statusDescription")}>
        {status.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : status.isError ? (
          <ApiErrorNote error={status.error} />
        ) : (
          <div className="flex flex-wrap items-center gap-3">
            <span
              className={
                status.data.databaseReachable
                  ? "inline-flex items-center gap-2 rounded-full bg-success-bg px-3 py-1.5 text-caption font-semibold text-success-text"
                  : "inline-flex items-center gap-2 rounded-full bg-danger-bg px-3 py-1.5 text-caption font-semibold text-danger-text"
              }
            >
              {status.data.databaseReachable ? (
                <CircleCheck size={16} aria-hidden="true" />
              ) : (
                <CircleX size={16} aria-hidden="true" />
              )}
              {status.data.databaseReachable ? t("dashboard.dbUp") : t("dashboard.dbDown")}
            </span>
            <span className="text-caption text-text-secondary">
              {t("dashboard.checkedAt", { at: formatDate(status.data.checkedAt, "dd/MM/yyyy HH:mm:ss") })}
            </span>
          </div>
        )}
      </AdminSection>

      <AdminSection title={t("dashboard.wiredTitle")} description={t("dashboard.wiredDescription")}>
        <ul className="grid gap-3 md:grid-cols-2">
          {WIRED_LINKS.map((link) => (
            <li key={link.to}>
              <Link
                to={link.to}
                className="flex items-center justify-between gap-3 rounded-xl bg-background-alt/70 px-4 py-3 hover:bg-chip-bg"
              >
                <span className="min-w-0">
                  <span className="block text-body font-semibold text-text-primary">{t(link.labelKey)}</span>
                  <span className="block font-mono text-small text-text-tertiary">{t(link.specKey)}</span>
                </span>
                <ArrowRight size={16} className="shrink-0 text-text-tertiary" aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      </AdminSection>

      <AdminSection title={t("dashboard.metricsTitle")} description={t("dashboard.metricsDescription")}>
        {metrics.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {metrics.isError ? <ApiErrorNote error={metrics.error} /> : null}
        {metrics.data ? (
          <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            {[
              ["users", metrics.data.users],
              ["activeCats", metrics.data.activeCats],
              ["scans", metrics.data.scans],
              ["failedJobs", metrics.data.failedJobs],
              ["pendingOutbox", metrics.data.pendingOutbox],
            ].map(([key, value]) => (
              <div key={key} className="rounded-xl bg-background-alt/70 p-4">
                <dt className="text-caption text-text-secondary">{t(`dashboard.metrics.${String(key)}`)}</dt>
                <dd className="pt-1 text-[26px] font-bold text-primary-dark">
                  {Number(value).toLocaleString("vi-VN")}
                </dd>
              </div>
            ))}
          </dl>
        ) : null}
      </AdminSection>
    </div>
  );
}
