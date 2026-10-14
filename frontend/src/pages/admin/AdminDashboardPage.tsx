import { useRef } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { Card as AntCard, Skeleton as AntSkeleton } from "antd";
import {
  Archive,
  ArrowRight,
  BookOpenText,
  CalendarClock,
  Database,
  Flag,
  Inbox,
  Palette,
  Send,
  ShieldAlert,
  ShieldCheck,
} from "lucide-react";
import {
  AccessDeniedState,
  AdminQueryError,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  formatAdminNumber,
  useAdminCan,
  useAdminMetrics,
  useAdminSystemStatus,
  type KpiTone,
} from "@/features/admin";
import { useGroup3Can } from "./group3Ops";
import { formatDate } from "@/shared/lib/format/formatDate";
import { Badge, buttonVariants } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

// p17 A1 Pha 1: lối vào theo công việc (DSAR, báo cáo cộng đồng, sự cố, job, outbox) lọc theo capability;
// không tự đếm số việc từ trang đầu của từng danh sách.
const WIRED_LINKS = [
  { to: "/admin/privacy/requests", labelKey: "nav.privacyRequests", capability: "privacyRequests", icon: Inbox },
  { to: "/admin/community/reports", labelKey: "nav.communityReports", capability: "communityModeration", icon: Flag },
  {
    to: "/admin/security?tab=incidents",
    labelKey: "nav.incidents",
    capability: "securityIncidentsRead",
    icon: ShieldAlert,
  },
  { to: "/admin/system/jobs", labelKey: "nav.jobs", capability: "jobs", icon: CalendarClock },
  { to: "/admin/notifications/outbox", labelKey: "nav.outbox", capability: "outbox", icon: Send },
  { to: "/admin/ph-color-chart", labelKey: "dashboard.linkPhColorChart", capability: "colorChart", icon: Palette },
  { to: "/admin/content", labelKey: "dashboard.linkContent", capability: "careTipsWrite", icon: BookOpenText },
  { to: "/admin/privacy/retention", labelKey: "dashboard.linkRetention", capability: "retention", icon: Archive },
  { to: "/admin/me/security", labelKey: "dashboard.linkMeSecurity", capability: "adminPortal", icon: ShieldCheck },
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
  const can = useAdminCan();
  const status = useAdminSystemStatus();
  const metrics = useAdminMetrics();
  const dashboardRef = useRef<HTMLDivElement>(null);
  const availableLinks = WIRED_LINKS.filter((link) => can(link.capability));

  useGSAP(
    () => {
      if (!metrics.data || !dashboardRef.current) return;
      const cards = gsap.utils.toArray<HTMLElement>("[data-admin-kpi]", dashboardRef.current);
      if (
        cards.length === 0 ||
        (typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches)
      )
        return;
      const reveal = gsap.fromTo(
        cards,
        { autoAlpha: 0, y: 10 },
        {
          autoAlpha: 1,
          y: 0,
          duration: 0.28,
          stagger: 0.04,
          ease: "power2.out",
          clearProps: "opacity,visibility,transform",
        },
      );
      return () => {
        reveal.progress(1).kill();
      };
    },
    { scope: dashboardRef, dependencies: [metrics.data] },
  );

  return (
    <div ref={dashboardRef} className="mx-auto flex w-full max-w-7xl flex-col gap-6">
      <header
        data-reveal
        className="relative overflow-hidden rounded-md border border-border/60 bg-surface px-4 py-5 shadow-sm sm:px-6 sm:py-7 md:px-9 md:py-9"
      >
        <span className="absolute inset-y-0 left-0 w-1.5 bg-primary-dark" aria-hidden="true" />
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between sm:gap-6">
          <div className="min-w-0 max-w-2xl">
            <Badge tone="info" className="mb-3 sm:mb-4">
              {t("nav.label")}
            </Badge>
            <h1 className="text-h2 font-extrabold tracking-tight text-text-primary sm:text-h1 md:text-display">
              {t("pages.dashboard.title")}
            </h1>
            <p className="mt-2 max-w-xl text-caption text-text-secondary sm:text-body">
              {t("pages.dashboard.description")}
            </p>
          </div>
          <Link
            to="/admin/me/security"
            className={cn(
              buttonVariants({ variant: "primary", size: "md" }),
              "w-full shrink-0 min-h-[44px] justify-center sm:w-auto",
            )}
          >
            <ShieldCheck size={18} aria-hidden="true" />
            {t("nav.settings")}
          </Link>
        </div>
      </header>

      <section aria-labelledby="admin-metrics-title" data-reveal>
        <div className="mb-4">
          <h2 id="admin-metrics-title" className="text-h3 font-extrabold text-text-primary">
            {t("dashboard.metricsTitle")}
          </h2>
          <p className="text-caption text-text-secondary">{t("dashboard.metricsDescription")}</p>
        </div>
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
      </section>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <AntCard
          data-reveal
          title={<h2 className="text-body font-bold">{t("dashboard.statusTitle")}</h2>}
          className="shadow-brand-md"
        >
          <div className="flex items-start gap-3">
            <span
              className="flex size-11 shrink-0 items-center justify-center rounded-md bg-background-alt text-primary-dark"
              aria-hidden="true"
            >
              <Database size={21} />
            </span>
            <div className="min-w-0">
              {status.isPending ? (
                <AntSkeleton active title={{ width: "45%" }} paragraph={{ rows: 1 }} />
              ) : status.isError ? (
                <AdminQueryError error={status.error} />
              ) : (
                <>
                  <Badge tone={status.data.databaseReachable ? "success" : "danger"}>
                    {status.data.databaseReachable ? t("dashboard.dbUp") : t("dashboard.dbDown")}
                  </Badge>
                  <p className="mt-2 text-caption text-text-secondary">
                    {t("dashboard.checkedAt", { at: formatDate(status.data.checkedAt, "dd/MM/yyyy HH:mm:ss") })}
                  </p>
                </>
              )}
            </div>
          </div>
          <p className="mt-5 border-t border-border/60 pt-4 text-small text-text-tertiary">
            {t("dashboard.statusDescription")}
          </p>
        </AntCard>

        <AntCard
          data-reveal
          title={<h2 className="text-body font-bold">{t("dashboard.wiredTitle")}</h2>}
          className="shadow-brand-md"
        >
          <p className="mb-3 text-caption text-text-secondary">{t("dashboard.wiredDescription")}</p>
          <ul className="grid gap-2 md:grid-cols-2">
            {availableLinks.map((link) => {
              const Icon = link.icon;
              return (
                <li key={link.to}>
                  <Link
                    to={link.to}
                    className="flex min-h-14 items-center gap-3 rounded-md border border-border/70 px-3 text-caption font-semibold text-text-primary transition-colors hover:border-primary/40 hover:bg-background-alt focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-[var(--focus-ring-color)] motion-reduce:transition-none"
                    data-motion="row"
                  >
                    <Icon size={18} className="shrink-0 text-primary-dark" aria-hidden="true" />
                    <span className="min-w-0 flex-1">{t(link.labelKey)}</span>
                    <ArrowRight size={15} className="shrink-0 text-text-tertiary" aria-hidden="true" />
                  </Link>
                </li>
              );
            })}
          </ul>
        </AntCard>
      </div>
    </div>
  );
}
