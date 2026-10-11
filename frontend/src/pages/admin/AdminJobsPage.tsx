import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  MutationErrorNote,
  Pager,
  ReasonField,
  SearchField,
  SortableTh,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  matchesQuery,
  nextSort,
  sortRows,
  formatAdminDateTime,
  isReasonValid,
  useAdminJobRuns,
  type AdminJobRun,
  type SortState,
} from "@/features/admin";
import { isJobRunValid, useGroup3Can, useRunAdminJob } from "./group3Ops";

type JobSortKey = "jobName" | "status" | "startedAt" | "durationMs" | "rowCount";

export function AdminJobsPage() {
  const { t } = useTranslation("admin");
  const can = useGroup3Can();
  if (!can("jobs")) {
    return <AccessDeniedState />;
  }
  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader
        title={t("pages.jobs.title")}
        description={t("pages.jobs.description")}
        specRef="L64 · GET /api/v1/admin/jobs/runs"
      />
      {can("jobRun") ? <RunJobPanel /> : null}
      <JobRunsPanel />
    </div>
  );
}

function RunJobPanel() {
  const { t } = useTranslation("admin");
  const [jobName, setJobName] = useState("");
  const [reason, setReason] = useState("");
  const [dryRun, setDryRun] = useState(true);
  const run = useRunAdminJob();
  const valid = isJobRunValid(jobName, reason) && isReasonValid(reason);
  return (
    <DensePanel title={t("jobs.runTitle")} description={t("jobs.runDescription")}>
      <Input
        label={t("jobs.runName")}
        value={jobName}
        onChange={(event) => {
          setJobName(event.target.value);
        }}
      />
      <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
      <label className="flex items-center gap-2 text-caption text-text-primary">
        <input
          type="checkbox"
          checked={dryRun}
          onChange={(event) => {
            setDryRun(event.target.checked);
          }}
        />
        {t("jobs.runDry")}
      </label>
      <div className="flex justify-end">
        <Button
          size="sm"
          disabled={!valid || run.isPending}
          loading={run.isPending}
          onClick={() => {
            run.mutate({ jobName, dryRun, reason: reason.trim() });
          }}
        >
          {t("jobs.runButton")}
        </Button>
      </div>
      {run.data ? (
        <p role="status" className="rounded-lg bg-info/60 px-3 py-2 text-caption text-text-primary">
          {t("jobs.runResult", {
            name: run.data.jobName,
            status: run.data.status,
            processed: run.data.itemsProcessed ?? 0,
            deleted: run.data.itemsDeleted ?? 0,
            failed: run.data.itemsFailed ?? 0,
          })}
        </p>
      ) : null}
      <MutationErrorNote error={run.error} />
    </DensePanel>
  );
}

function JobRunsPanel() {
  const { t } = useTranslation("admin");
  const [jobName, setJobName] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<JobSortKey> | null>(null);
  const runs = useAdminJobRuns({ jobName, status, page, size: 20 });

  const rows = useMemo(() => {
    const filtered = (runs.data?.items ?? []).filter((run) =>
      matchesQuery([run.jobName, run.status, run.errorSummary], query),
    );
    return sortRows(filtered, sort, (run: AdminJobRun, key) => run[key]);
  }, [runs.data, query, sort]);

  const header = (label: string, key: JobSortKey) => (
    <SortableTh
      label={label}
      sortKey={key}
      sort={sort}
      onSort={(next) => {
        setSort(nextSort(sort, next));
      }}
    />
  );

  return (
    <DensePanel
      title={t("jobs.listTitle")}
      actions={
        <Button
          size="sm"
          variant="tertiary"
          onClick={() => {
            void runs.refetch();
          }}
        >
          {t("jobs.refresh")}
        </Button>
      }
    >
      <div className="grid gap-3 md:grid-cols-3">
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
        <SearchField label={t("jobs.search")} value={query} onChange={setQuery} placeholder={t("dense.search")} />
      </div>
      {runs.isPending ? <TableSkeleton /> : null}
      {runs.isError ? (
        <AdminQueryError
          error={runs.error}
          onRetry={() => {
            void runs.refetch();
          }}
        />
      ) : null}
      {runs.data && runs.data.items.length === 0 ? (
        <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
      ) : null}
      {runs.data && runs.data.items.length > 0 ? (
        <>
          <DenseTableFrame label={t("jobs.listTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  {header(t("jobs.colName"), "jobName")}
                  {header(t("jobs.colStatus"), "status")}
                  {header(t("jobs.colStarted"), "startedAt")}
                  <th scope="col" className={denseThClass}>
                    {t("jobs.colFinished")}
                  </th>
                  {header(t("jobs.colDuration"), "durationMs")}
                  {header(t("jobs.colRows"), "rowCount")}
                  <th scope="col" className={denseThClass}>
                    {t("jobs.colError")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((run) => (
                  <tr key={run.id} className={denseRowClass}>
                    <td className={denseTdClass}>
                      <code>{run.jobName}</code>
                    </td>
                    <td className={denseTdClass}>{run.status}</td>
                    <td className={denseTdClass}>{formatAdminDateTime(run.startedAt)}</td>
                    <td className={denseTdClass}>{formatAdminDateTime(run.finishedAt)}</td>
                    <td className={`${denseTdClass} font-mono tabular-nums`}>
                      {run.durationMs == null ? "—" : `${String(run.durationMs)} ms`}
                    </td>
                    <td className={`${denseTdClass} font-mono tabular-nums`}>{run.rowCount ?? "—"}</td>
                    <td className={denseTdClass}>{run.errorSummary ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
          {rows.length === 0 ? <p className="text-caption text-text-secondary">{t("dense.noMatch")}</p> : null}
          <Pager
            page={runs.data.number}
            totalPages={runs.data.totalPages}
            totalElements={runs.data.totalElements}
            onPageChange={setPage}
          />
        </>
      ) : null}
    </DensePanel>
  );
}
