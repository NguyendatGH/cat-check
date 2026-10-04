import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  AdminTableScroll,
  ApiErrorNote,
  Pager,
  adminTableClass,
  adminTdClass,
  adminThClass,
  useAdminJobRuns,
} from "@/features/admin";

function formatDate(value: string | null): string {
  return value === null
    ? "—"
    : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminJobsPage() {
  const { t } = useTranslation("admin");
  const [jobName, setJobName] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const runs = useAdminJobRuns({ jobName, status, page, size: 20 });

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.jobs.title")}
        description={t("pages.jobs.description")}
        specRef="L64 · GET /api/v1/admin/jobs/runs"
      />
      <AdminSection title={t("jobs.filtersTitle")}>
        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label={t("jobs.jobName")}
            value={jobName}
            onChange={(event) => {
              setJobName(event.target.value);
              setPage(0);
            }}
          />
          <AdminSelect
            label={t("jobs.status")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("jobs.allStatuses")}</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
            <option value="RUNNING">RUNNING</option>
            <option value="SKIPPED">SKIPPED</option>
          </AdminSelect>
        </div>
      </AdminSection>
      <AdminSection title={t("jobs.listTitle")}>
        {runs.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {runs.isError ? <ApiErrorNote error={runs.error} /> : null}
        {runs.data && runs.data.items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {runs.data && runs.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("jobs.colName")}</th>
                    <th className={adminThClass}>{t("jobs.colStatus")}</th>
                    <th className={adminThClass}>{t("jobs.colStarted")}</th>
                    <th className={adminThClass}>{t("jobs.colFinished")}</th>
                    <th className={adminThClass}>{t("jobs.colDuration")}</th>
                    <th className={adminThClass}>{t("jobs.colRows")}</th>
                    <th className={adminThClass}>{t("jobs.colError")}</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.data.items.map((run) => (
                    <tr key={run.id}>
                      <td className={adminTdClass}>
                        <code>{run.jobName}</code>
                      </td>
                      <td className={adminTdClass}>{run.status}</td>
                      <td className={adminTdClass}>{formatDate(run.startedAt)}</td>
                      <td className={adminTdClass}>{formatDate(run.finishedAt)}</td>
                      <td className={adminTdClass}>{run.durationMs === null ? "—" : `${String(run.durationMs)} ms`}</td>
                      <td className={adminTdClass}>{run.rowCount ?? "—"}</td>
                      <td className={adminTdClass}>{run.errorSummary ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={runs.data.number}
              totalPages={runs.data.totalPages}
              totalElements={runs.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </AdminSection>
      <Button
        type="button"
        variant="tertiary"
        className="self-start"
        onClick={() => {
          void runs.refetch();
        }}
      >
        {t("jobs.refresh")}
      </Button>
    </div>
  );
}
