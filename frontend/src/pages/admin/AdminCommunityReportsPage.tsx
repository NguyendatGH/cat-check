import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminQueryError,
  AdminSection,
  AdminSelect,
  DenseTableFrame,
  Pager,
  ReasonConfirmDialog,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  useAdminCommunityReports,
  useModerateCommunityReport,
  type CommunityReport,
  type CommunityReportAction,
} from "@/features/admin";
import { useGroup2Can } from "./group2Access";

// Backend (JdbcCommunityRepository.moderateReport): HIDE_POST/REMOVE_POST chỉ tác động bài viết,
// HIDE_COMMENT chỉ tác động bình luận — chọn sai loại thì report bị đóng mà nội dung không đổi.
const ACTIONS_BY_TARGET: Record<CommunityReport["targetType"], readonly CommunityReportAction[]> = {
  POST: ["HIDE_POST", "REMOVE_POST", "DISMISS"],
  COMMENT: ["HIDE_COMMENT", "DISMISS"],
};

// Chỉ report chưa đóng mới xử lý được.
const ACTIONABLE_STATUSES = new Set(["OPEN", "REVIEWING"]);

export function AdminCommunityReportsPage() {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState("OPEN");
  const [page, setPage] = useState(0);
  const [actions, setActions] = useState<Record<string, CommunityReportAction>>({});
  const [pending, setPending] = useState<{ report: CommunityReport; action: CommunityReportAction } | null>(null);
  const reports = useAdminCommunityReports(status, page);
  const moderate = useModerateCommunityReport();
  const canModerate = useGroup2Can()("communityModeration") && ACTIONABLE_STATUSES.has(status);
  const items = reports.data?.items ?? [];

  const actionFor = (report: CommunityReport): CommunityReportAction =>
    actions[report.id] ?? ACTIONS_BY_TARGET[report.targetType][0];

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.communityReports.title")}
        description={t("pages.communityReports.description")}
      />
      <AdminSection title={t("pages.communityReports.queueTitle")}>
        <div className="flex flex-wrap items-end gap-3">
          <AdminSelect
            label={t("pages.communityReports.statusLabel")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="OPEN">{t("pages.communityReports.statusOpen")}</option>
            <option value="REVIEWING">{t("pages.communityReports.statusReviewing")}</option>
            <option value="RESOLVED">{t("pages.communityReports.statusResolved")}</option>
            <option value="DISMISSED">{t("pages.communityReports.statusDismissed")}</option>
          </AdminSelect>
        </div>

        {moderate.isSuccess ? (
          <p role="status" className="text-caption text-success-text">
            {t("pages.communityReports.done")}
          </p>
        ) : null}
        {reports.isPending ? <TableSkeleton /> : null}
        {reports.isError ? (
          <AdminQueryError
            error={reports.error}
            onRetry={() => {
              void reports.refetch();
            }}
          />
        ) : null}
        {reports.data && items.length === 0 ? (
          <p className="py-6 text-center text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {reports.data && items.length > 0 ? (
          <>
            <DenseTableFrame label={t("pages.communityReports.queueTitle")}>
              <table className={denseTableClass}>
                <thead>
                  <tr>
                    <th scope="col" className={denseThClass}>
                      {t("pages.communityReports.target")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.communityReports.reporter")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.communityReports.reason")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.communityReports.createdAt")}
                    </th>
                    {canModerate ? (
                      <th scope="col" className={denseThClass}>
                        {t("pages.communityReports.action")}
                      </th>
                    ) : null}
                  </tr>
                </thead>
                <tbody>
                  {items.map((report) => (
                    <tr key={report.id} className={denseRowClass}>
                      <td className={denseTdClass}>
                        <p className="font-semibold text-text-primary">
                          {report.targetTitle ?? t(`pages.communityReports.targetTypes.${report.targetType}`)}
                        </p>
                        <p className="text-caption text-text-tertiary">
                          {t(`pages.communityReports.targetTypes.${report.targetType}`)}
                        </p>
                      </td>
                      <td className={denseTdClass}>{report.reporterName}</td>
                      <td className={denseTdClass}>
                        <p>{t(`pages.communityReports.reasons.${report.reason}`, { defaultValue: report.reason })}</p>
                        {report.details ? <p className="text-caption text-text-tertiary">{report.details}</p> : null}
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(report.createdAt)}
                      </td>
                      {canModerate ? (
                        <td className={denseTdClass}>
                          <div className="flex min-w-[200px] flex-col gap-2">
                            <AdminSelect
                              label={t("pages.communityReports.action")}
                              value={actionFor(report)}
                              onChange={(event) => {
                                const value = event.target.value as CommunityReportAction;
                                setActions((prev) => ({ ...prev, [report.id]: value }));
                              }}
                            >
                              {ACTIONS_BY_TARGET[report.targetType].map((value) => (
                                <option key={value} value={value}>
                                  {t(`pages.communityReports.actions.${value}`)}
                                </option>
                              ))}
                            </AdminSelect>
                            <Button
                              size="sm"
                              variant="tertiary"
                              disabled={moderate.isPending}
                              onClick={() => {
                                moderate.reset();
                                setPending({ report, action: actionFor(report) });
                              }}
                            >
                              {t("pages.communityReports.resolve")}
                            </Button>
                          </div>
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </DenseTableFrame>
            <Pager
              page={page}
              totalPages={reports.data.totalPages}
              totalElements={reports.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </AdminSection>

      <ReasonConfirmDialog
        open={pending !== null}
        onOpenChange={(open) => {
          if (!open) setPending(null);
        }}
        title={pending === null ? "" : t(`pages.communityReports.actions.${pending.action}`)}
        target={
          pending === null
            ? ""
            : `${t(`pages.communityReports.targetTypes.${pending.report.targetType}`)} · ${
                pending.report.targetTitle ?? "—"
              }`
        }
        consequence={pending === null ? "" : t(`pages.communityReports.consequences.${pending.action}`)}
        confirmLabel={t("pages.communityReports.resolve")}
        destructive={pending !== null && pending.action !== "DISMISS"}
        pending={moderate.isPending}
        error={moderate.error}
        onConfirm={(reason) => {
          if (pending === null) return;
          moderate.mutate(
            { reportId: pending.report.id, action: pending.action, reason },
            {
              onSuccess: () => {
                setPending(null);
              },
            },
          );
        }}
      />
    </div>
  );
}
