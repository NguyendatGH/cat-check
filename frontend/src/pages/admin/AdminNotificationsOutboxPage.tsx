import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/shared/ui";
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
  useAdminOutbox,
} from "@/features/admin";

function formatDate(value: string | null): string {
  return value === null
    ? "—"
    : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminNotificationsOutboxPage() {
  const { t } = useTranslation("admin");
  const [channel, setChannel] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const outbox = useAdminOutbox({ channel, status, page, size: 20 });

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.outbox.title")}
        description={t("pages.outbox.description")}
        specRef="L66 · GET /api/v1/admin/notifications/outbox"
      />
      <AdminSection title={t("outbox.filtersTitle")}>
        <div className="grid gap-4 md:grid-cols-2">
          <AdminSelect
            label={t("outbox.channel")}
            value={channel}
            onChange={(event) => {
              setChannel(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("outbox.allChannels")}</option>
            <option value="EMAIL">EMAIL</option>
            <option value="PUSH">PUSH</option>
          </AdminSelect>
          <AdminSelect
            label={t("outbox.status")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("outbox.allStatuses")}</option>
            <option value="PENDING">PENDING</option>
            <option value="SENT">SENT</option>
            <option value="FAILED">FAILED</option>
          </AdminSelect>
        </div>
      </AdminSection>
      <AdminSection title={t("outbox.listTitle")}>
        {outbox.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {outbox.isError ? <ApiErrorNote error={outbox.error} /> : null}
        {outbox.data && outbox.data.items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {outbox.data && outbox.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("outbox.colChannel")}</th>
                    <th className={adminThClass}>{t("outbox.colStatus")}</th>
                    <th className={adminThClass}>{t("outbox.colRecipient")}</th>
                    <th className={adminThClass}>{t("outbox.colReference")}</th>
                    <th className={adminThClass}>{t("outbox.colAttempts")}</th>
                    <th className={adminThClass}>{t("outbox.colNextAttempt")}</th>
                    <th className={adminThClass}>{t("outbox.colError")}</th>
                    <th className={adminThClass}>{t("outbox.colCreated")}</th>
                  </tr>
                </thead>
                <tbody>
                  {outbox.data.items.map((entry) => (
                    <tr key={entry.id}>
                      <td className={adminTdClass}>{entry.channel}</td>
                      <td className={adminTdClass}>{entry.status}</td>
                      <td className={adminTdClass}>{entry.recipientMasked}</td>
                      <td className={adminTdClass}>
                        <code>{entry.reference}</code>
                      </td>
                      <td className={adminTdClass}>{entry.attempts}</td>
                      <td className={adminTdClass}>{formatDate(entry.nextAttemptAt)}</td>
                      <td className={adminTdClass}>{entry.lastError ?? "—"}</td>
                      <td className={adminTdClass}>{formatDate(entry.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={outbox.data.number}
              totalPages={outbox.data.totalPages}
              totalElements={outbox.data.totalElements}
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
          void outbox.refetch();
        }}
      >
        {t("outbox.refresh")}
      </Button>
    </div>
  );
}
