import { useState } from "react";
import { useTranslation } from "react-i18next";
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
  useAdminAuditLogs,
} from "@/features/admin";

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminAuditLogPage() {
  const { t } = useTranslation("admin");
  const [action, setAction] = useState("");
  const [result, setResult] = useState("");
  const [actorType, setActorType] = useState("");
  const [page, setPage] = useState(0);
  const logs = useAdminAuditLogs({ action, result, actorType, page, size: 20 });

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.auditLog.title")}
        description={t("pages.auditLog.description")}
        specRef="L68 · GET /api/v1/admin/audit-logs"
      />
      <AdminSection title={t("auditLog.filtersTitle")}>
        <div className="grid gap-4 md:grid-cols-3">
          <label className="flex flex-col gap-1.5 text-caption font-semibold text-text-secondary">
            {t("auditLog.action")}
            <input
              className="h-11 rounded-xl bg-surface px-3 text-body font-normal text-text-primary shadow-xs"
              value={action}
              onChange={(event) => {
                setAction(event.target.value);
                setPage(0);
              }}
            />
          </label>
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
        </div>
      </AdminSection>
      <AdminSection title={t("auditLog.listTitle")}>
        {logs.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {logs.isError ? <ApiErrorNote error={logs.error} /> : null}
        {logs.data && logs.data.items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {logs.data && logs.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("auditLog.occurredAt")}</th>
                    <th className={adminThClass}>{t("auditLog.action")}</th>
                    <th className={adminThClass}>{t("auditLog.result")}</th>
                    <th className={adminThClass}>{t("auditLog.actor")}</th>
                    <th className={adminThClass}>{t("auditLog.subject")}</th>
                    <th className={adminThClass}>{t("auditLog.metadata")}</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.data.items.map((log) => (
                    <tr key={log.id}>
                      <td className={adminTdClass}>{formatDate(log.occurredAt)}</td>
                      <td className={adminTdClass}>
                        <code>{log.action}</code>
                      </td>
                      <td className={adminTdClass}>{log.result}</td>
                      <td className={adminTdClass}>
                        {log.actorType}
                        {log.actorRole ? ` · ${log.actorRole}` : ""}
                      </td>
                      <td className={adminTdClass}>{log.subjectType}</td>
                      <td className={adminTdClass}>
                        <code className="break-all text-small">{log.metadata}</code>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={logs.data.number}
              totalPages={logs.data.totalPages}
              totalElements={logs.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </AdminSection>
    </div>
  );
}
