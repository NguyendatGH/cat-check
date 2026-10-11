import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ArrowRight, CircleCheck, CircleX, Loader2 } from "lucide-react";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  DensePanel,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  formatAdminNumber,
  useAdminMetrics,
  useAdminSystemStatus,
  type KpiTone,
} from "@/features/admin";
import { useGroup3Can } from "./group3Ops";
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

const METRIC_KEYS = ["users", "activeCats", "scans", "failedJobs", "pendingOutbox"] as const;

function metricTone(key: (typeof METRIC_KEYS)[number], value: number): KpiTone {
  if (key === "failedJobs") return value > 0 ? "danger" : "success";
  if (key === "pendingOutbox") return value > 0 ? "warning" : "success";
  return "neutral";
}

export function AdminDashboardPage() {
  const can = useGroup3Can();
  return can("dashboard") ? <DashboardContent /> : <AccessDeniedState />;
}

function DashboardContent() {
  const { t } = useTranslation("admin");
  const status = useAdminSystemStatus();
  const metrics = useAdminMetrics();

  return (
    <div className="flex max-w-5xl flex-col gap-4">
      <AdminPageHeader title={t("pages.dashboard.title")} description={t("pages.dashboard.description")} />

      <DensePanel title={t("dashboard.statusTitle")} description={t("dashboard.statusDescription")}>
        {status.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : status.isError ? (
          <AdminQueryError error={status.error} />
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
      </DensePanel>

      <DensePanel title={t("dashboard.wiredTitle")} description={t("dashboard.wiredDescription")}>
        <ul className="grid gap-2 md:grid-cols-2">
          {WIRED_LINKS.map((link) => (
            <li key={link.to}>
              <Link
                to={link.to}
                className="flex items-center justify-between gap-3 rounded-lg border border-border px-3 py-2 hover:bg-background-alt/60"
              >
                <span className="min-w-0">
                  <span className="block text-caption font-semibold text-text-primary">{t(link.labelKey)}</span>
                  <span className="block font-mono text-small text-text-tertiary">{t(link.specKey)}</span>
                </span>
                <ArrowRight size={16} className="shrink-0 text-text-tertiary" aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      </DensePanel>

      <DensePanel title={t("dashboard.metricsTitle")} description={t("dashboard.metricsDescription")}>
        {metrics.isPending ? <KpiSkeleton /> : null}
        {metrics.isError ? <AdminQueryError error={metrics.error} /> : null}
        {metrics.data ? (
          <KpiGrid label={t("dense.kpiLabel")}>
            {METRIC_KEYS.map((key) => (
              <KpiTile
                key={key}
                label={t(`dashboard.metrics.${key}`)}
                value={formatAdminNumber(metrics.data[key])}
                tone={metricTone(key, metrics.data[key])}
              />
            ))}
          </KpiGrid>
        ) : null}
      </DensePanel>
    </div>
  );
}
