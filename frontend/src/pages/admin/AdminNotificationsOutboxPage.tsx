import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/shared/ui";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  MutationErrorNote,
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
  useAdminOutbox,
  type AdminOutboxEntry,
  type SortState,
} from "@/features/admin";
import { canResendOutbox, useGroup3Can, useResendAdminOutbox } from "./group3Ops";

type OutboxSortKey = "channel" | "status" | "attempts" | "nextAttemptAt" | "createdAt";

export function AdminNotificationsOutboxPage() {
  const { t } = useTranslation("admin");
  const can = useGroup3Can();
  if (!can("outbox")) {
    return <AccessDeniedState />;
  }
  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader title={t("pages.outbox.title")} description={t("pages.outbox.description")} />
      <OutboxPanel />
    </div>
  );
}

function OutboxPanel() {
  const { t } = useTranslation("admin");
  const can = useGroup3Can();
  const [channel, setChannel] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<OutboxSortKey> | null>(null);
  const outbox = useAdminOutbox({ channel, status, page, size: 20 });
  const resend = useResendAdminOutbox();

  const rows = useMemo(() => {
    const filtered = (outbox.data?.items ?? []).filter((entry) =>
      matchesQuery([entry.recipientMasked, entry.reference, entry.lastError], query),
    );
    return sortRows(filtered, sort, (entry: AdminOutboxEntry, key) => entry[key]);
  }, [outbox.data, query, sort]);

  const header = (label: string, key: OutboxSortKey) => (
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
      title={t("outbox.listTitle")}
      actions={
        <Button
          size="sm"
          variant="tertiary"
          onClick={() => {
            void outbox.refetch();
          }}
        >
          {t("outbox.refresh")}
        </Button>
      }
    >
      <div className="grid gap-3 md:grid-cols-3">
        <AdminSelect
          label={t("outbox.channel")}
          value={channel}
          onChange={(event) => {
            setChannel(event.target.value);
            setPage(0);
          }}
        >
          <option value="">{t("outbox.allChannels")}</option>
          <option value="EMAIL">{t("outbox.channels.EMAIL")}</option>
          <option value="PUSH">{t("outbox.channels.PUSH")}</option>
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
          <option value="PENDING">{t("outbox.statuses.PENDING")}</option>
          <option value="SENT">{t("outbox.statuses.SENT")}</option>
          <option value="FAILED">{t("outbox.statuses.FAILED")}</option>
        </AdminSelect>
        <SearchField label={t("outbox.search")} value={query} onChange={setQuery} placeholder={t("dense.search")} />
      </div>
      {outbox.isPending ? <TableSkeleton /> : null}
      {outbox.isError ? (
        <AdminQueryError
          error={outbox.error}
          onRetry={() => {
            void outbox.refetch();
          }}
        />
      ) : null}
      <MutationErrorNote error={resend.error} />
      {resend.isSuccess ? (
        <p role="status" className="rounded-lg bg-success-bg px-3 py-2 text-caption text-success-text">
          {t("outbox.resent")}
        </p>
      ) : null}
      {outbox.data && outbox.data.items.length === 0 ? (
        <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
      ) : null}
      {outbox.data && outbox.data.items.length > 0 ? (
        <>
          <DenseTableFrame label={t("outbox.listTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  {header(t("outbox.colChannel"), "channel")}
                  {header(t("outbox.colStatus"), "status")}
                  <th scope="col" className={denseThClass}>
                    {t("outbox.colRecipient")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("outbox.colReference")}
                  </th>
                  {header(t("outbox.colAttempts"), "attempts")}
                  {header(t("outbox.colNextAttempt"), "nextAttemptAt")}
                  <th scope="col" className={denseThClass}>
                    {t("outbox.colError")}
                  </th>
                  {header(t("outbox.colCreated"), "createdAt")}
                  {can("outbox") ? (
                    <th scope="col" className={denseThClass}>
                      {t("outbox.colActions")}
                    </th>
                  ) : null}
                </tr>
              </thead>
              <tbody>
                {rows.map((entry) => (
                  <tr key={entry.id} className={denseRowClass}>
                    <td className={denseTdClass}>
                      {t(`outbox.channels.${entry.channel}`, { defaultValue: entry.channel })}
                    </td>
                    <td className={denseTdClass}>
                      {t(`outbox.statuses.${entry.status}`, { defaultValue: entry.status })}
                    </td>
                    <td className={denseTdClass}>{entry.recipientMasked}</td>
                    <td className={denseTdClass}>
                      <code>{entry.reference}</code>
                    </td>
                    <td className={`${denseTdClass} font-mono tabular-nums`}>{entry.attempts}</td>
                    <td className={denseTdClass}>{formatAdminDateTime(entry.nextAttemptAt)}</td>
                    <td className={denseTdClass}>{entry.lastError ?? "—"}</td>
                    <td className={denseTdClass}>{formatAdminDateTime(entry.createdAt)}</td>
                    {can("outbox") ? (
                      <td className={denseTdClass}>
                        {canResendOutbox(can, entry.status) ? (
                          <Button
                            size="sm"
                            variant="tertiary"
                            disabled={resend.isPending}
                            onClick={() => {
                              resend.mutate(entry.id);
                            }}
                          >
                            {t("outbox.resend")}
                          </Button>
                        ) : null}
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
          {rows.length === 0 ? <p className="text-caption text-text-secondary">{t("dense.noMatch")}</p> : null}
          <Pager
            page={outbox.data.number}
            totalPages={outbox.data.totalPages}
            totalElements={outbox.data.totalElements}
            onPageChange={setPage}
          />
        </>
      ) : null}
    </DensePanel>
  );
}
