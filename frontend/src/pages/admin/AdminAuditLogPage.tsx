import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Input } from "@/shared/ui";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  Pager,
  SearchField,
  SortableTh,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  matchesQuery,
  nextSort,
  sortRows,
  useAdminAuditLogs,
  type AdminAuditLog,
  type SortState,
} from "@/features/admin";
import { useGroup3Can } from "./group3Ops";

type AuditSortKey = "occurredAt" | "action" | "result" | "actorType" | "subjectType";

export function AdminAuditLogPage() {
  const { t } = useTranslation("admin");
  const can = useGroup3Can();
  if (!can("auditLog")) {
    return <AccessDeniedState />;
  }
  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader
        title={t("pages.auditLog.title")}
        description={t("pages.auditLog.description")}
        specRef="L68 · GET /api/v1/admin/audit-logs"
      />
      <AuditLogPanel />
    </div>
  );
}

function AuditLogPanel() {
  const { t } = useTranslation("admin");
  const [action, setAction] = useState("");
  const [result, setResult] = useState("");
  const [actorType, setActorType] = useState("");
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<AuditSortKey> | null>(null);
  const logs = useAdminAuditLogs({ action, result, actorType, page, size: 20 });

  const rows = useMemo(() => {
    const filtered = (logs.data?.items ?? []).filter((log) =>
      matchesQuery([log.action, log.actorType, log.actorRole, log.subjectType, log.metadata], query),
    );
    return sortRows(filtered, sort, (log: AdminAuditLog, key) => log[key]);
  }, [logs.data, query, sort]);

  const header = (label: string, key: AuditSortKey) => (
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
    <DensePanel title={t("auditLog.listTitle")}>
      <div className="grid gap-3 md:grid-cols-4">
        <Input
          label={t("auditLog.action")}
          value={action}
          onChange={(event) => {
            setAction(event.target.value);
            setPage(0);
          }}
        />
        <AdminSelect
          label={t("auditLog.result")}
          value={result}
          onChange={(event) => {
            setResult(event.target.value);
            setPage(0);
          }}
        >
          <option value="">{t("auditLog.all")}</option>
          <option value="SUCCESS">SUCCESS</option>
          <option value="DENIED">DENIED</option>
          <option value="ERROR">ERROR</option>
        </AdminSelect>
        <AdminSelect
          label={t("auditLog.actorType")}
          value={actorType}
          onChange={(event) => {
            setActorType(event.target.value);
            setPage(0);
          }}
        >
          <option value="">{t("auditLog.all")}</option>
          <option value="USER">USER</option>
          <option value="ADMIN">ADMIN</option>
          <option value="DPO">DPO</option>
          <option value="SYSTEM">SYSTEM</option>
          <option value="JOB">JOB</option>
        </AdminSelect>
        <SearchField label={t("auditLog.search")} value={query} onChange={setQuery} placeholder={t("dense.search")} />
      </div>
      {logs.isPending ? <TableSkeleton /> : null}
      {logs.isError ? (
        <AdminQueryError
          error={logs.error}
          onRetry={() => {
            void logs.refetch();
          }}
        />
      ) : null}
      {logs.data && logs.data.items.length === 0 ? (
        <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
      ) : null}
      {logs.data && logs.data.items.length > 0 ? (
        <>
          <DenseTableFrame label={t("auditLog.listTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  {header(t("auditLog.occurredAt"), "occurredAt")}
                  {header(t("auditLog.action"), "action")}
                  {header(t("auditLog.result"), "result")}
                  {header(t("auditLog.actor"), "actorType")}
                  {header(t("auditLog.subject"), "subjectType")}
                  <th scope="col" className={denseThClass}>
                    {t("auditLog.metadata")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((log) => (
                  <tr key={log.id} className={denseRowClass}>
                    <td className={denseTdClass}>{formatAdminDateTime(log.occurredAt)}</td>
                    <td className={denseTdClass}>
                      <code>{log.action}</code>
                    </td>
                    <td className={denseTdClass}>{log.result}</td>
                    <td className={denseTdClass}>
                      {log.actorType}
                      {log.actorRole ? ` · ${log.actorRole}` : ""}
                    </td>
                    <td className={denseTdClass}>{log.subjectType}</td>
                    <td className={denseTdClass}>
                      <code className="break-all text-small">{log.metadata}</code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
          {rows.length === 0 ? <p className="text-caption text-text-secondary">{t("dense.noMatch")}</p> : null}
          <Pager
            page={logs.data.number}
            totalPages={logs.data.totalPages}
            totalElements={logs.data.totalElements}
            onPageChange={setPage}
          />
        </>
      ) : null}
    </DensePanel>
  );
}
