import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/shared/ui";
import { formatDate } from "@/shared/lib/format/formatDate";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  ApiErrorNote,
  DenseTableFrame,
  Pager,
  ReasonField,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  isReasonValid,
  useAdminCommunityReports,
  useModerateCommunityReport,
  type CommunityReportAction,
} from "@/features/admin";
import { useGroup2Can } from "./group2Access";

const ACTIONS: readonly CommunityReportAction[] = ["HIDE_POST", "REMOVE_POST", "HIDE_COMMENT", "DISMISS"];

export function AdminCommunityReportsPage() {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState("OPEN");
  const [page, setPage] = useState(0);
  const [action, setAction] = useState<CommunityReportAction>("HIDE_POST");
  const [reason, setReason] = useState("");
  const reports = useAdminCommunityReports(status, page);
  const moderate = useModerateCommunityReport();
  const canModerate = useGroup2Can()("communityModeration");

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

        <ApiErrorNote error={reports.error} />
        {reports.isPending ? <p className="pt-4 text-small text-text-secondary">{t("feedback.loading")}</p> : null}
        {reports.data ? (
          <>
            <div className="mt-4">
              <DenseTableFrame label={t("pages.communityReports.queueTitle")}>
                <table className={denseTableClass}>
                  <thead>
                    <tr>
                      <th className={denseThClass}>{t("pages.communityReports.target")}</th>
                      <th className={denseThClass}>{t("pages.communityReports.reporter")}</th>
                      <th className={denseThClass}>{t("pages.communityReports.reason")}</th>
                      <th className={denseThClass}>{t("pages.communityReports.createdAt")}</th>
                      {canModerate ? <th className={denseThClass}>{t("pages.communityReports.action")}</th> : null}
                    </tr>
                  </thead>
                  <tbody>
                    {reports.data.items.map((report) => (
                      <tr key={report.id} className={denseRowClass}>
                        <td className={denseTdClass}>
                          <p className="font-semibold text-text-primary">{report.targetTitle ?? report.targetType}</p>
                          <p className="text-caption text-text-tertiary">{report.targetType}</p>
                        </td>
                        <td className={denseTdClass}>{report.reporterName}</td>
                        <td className={denseTdClass}>
                          <p>{report.reason}</p>
                          {report.details ? <p className="text-caption text-text-tertiary">{report.details}</p> : null}
                        </td>
                        <td className={denseTdClass}>{formatDate(report.createdAt)}</td>
                        {canModerate ? (
                          <td className={denseTdClass}>
                            <div className="flex min-w-[220px] flex-col gap-2">
                              <AdminSelect
                                label={t("pages.communityReports.action")}
                                value={action}
                                onChange={(event) => {
                                  setAction(event.target.value as CommunityReportAction);
                                }}
                              >
                                {ACTIONS.map((value) => (
                                  <option key={value} value={value}>
                                    {t(`pages.communityReports.actions.${value}`)}
                                  </option>
                                ))}
                              </AdminSelect>
                              <ReasonField value={reason} onChange={setReason} disabled={moderate.isPending} />
                              <Button
                                size="sm"
                                disabled={!isReasonValid(reason)}
                                loading={moderate.isPending}
                                onClick={() => {
                                  moderate.mutate({ reportId: report.id, action, reason: reason.trim() });
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
            </div>
            <Pager
              page={page}
              totalPages={reports.data.totalPages}
              totalElements={reports.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </AdminSection>
    </div>
  );
}
