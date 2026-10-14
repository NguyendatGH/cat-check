import { Fragment, useMemo, useState, type SyntheticEvent } from "react";
import { useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { ChevronDown, ChevronRight, FileSearch, Search } from "lucide-react";
import { Badge, Button, EmptyState, Input } from "@/shared/ui";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DenseTableFrame,
  DensePanel,
  DetailGrid,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  MonoId,
  SearchField,
  SortableTh,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isUuid,
  latestConsentByPurpose,
  matchesQuery,
  nextSort,
  sortRows,
  useAdminCan,
  useAdminConsentPurposes,
  useAdminConsentRecords,
  type AdminConsentRecord,
  type ConsentRecordStatus,
  type SortState,
} from "@/features/admin";

const STATUSES: readonly ConsentRecordStatus[] = ["GRANTED", "DENIED", "WITHDRAWN"];

type ConsentSortKey = "occurredAt" | "purposeCode" | "status" | "method";

function statusTone(status: ConsentRecordStatus): "success" | "warning" | "neutral" {
  if (status === "GRANTED") return "success";
  if (status === "WITHDRAWN") return "warning";
  return "neutral";
}

function consentSortValue(row: AdminConsentRecord, key: ConsentSortKey): string | number {
  switch (key) {
    case "occurredAt":
      return Date.parse(row.occurredAt);
    case "purposeCode":
      return row.purposeCode;
    case "status":
      return row.status;
    case "method":
      return row.method;
  }
}

export function AdminPrivacyConsentsPage() {
  const { t } = useTranslation("admin");
  const can = useAdminCan();
  const canRead = can("consentsRead");
  const [searchParams] = useSearchParams();
  const [userIdInput, setUserIdInput] = useState(searchParams.get("userId") ?? "");
  const [submitted, setSubmitted] = useState(false);
  const [userId, setUserId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [purposeFilter, setPurposeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<ConsentRecordStatus | "">("");
  const [sort, setSort] = useState<SortState<ConsentSortKey> | null>({ key: "occurredAt", direction: "desc" });
  const [expanded, setExpanded] = useState<string | null>(null);

  const records = useAdminConsentRecords(userId ?? "", userId !== null && canRead);
  const purposes = useAdminConsentPurposes();
  const purposeLabel = useMemo(() => {
    const map = new Map<string, string>();
    for (const purpose of purposes.data?.items ?? []) map.set(purpose.code, purpose.label);
    return map;
  }, [purposes.data]);

  const items = useMemo(() => records.data?.pages.flatMap((page) => page.items) ?? [], [records.data]);
  const latest = useMemo(() => latestConsentByPurpose(items), [items]);
  const purposeCodes = useMemo(() => [...new Set(items.map((row) => row.purposeCode))].sort(), [items]);
  const visible = useMemo(
    () =>
      sortRows(
        items.filter(
          (row) =>
            (purposeFilter === "" || row.purposeCode === purposeFilter) &&
            (statusFilter === "" || row.status === statusFilter) &&
            matchesQuery(
              [row.purposeCode, purposeLabel.get(row.purposeCode), row.method, row.uiSurface, row.requestId],
              query,
            ),
        ),
        sort,
        consentSortValue,
      ),
    [items, purposeFilter, statusFilter, query, sort, purposeLabel],
  );
  const onSort = (key: ConsentSortKey) => {
    setSort((current) => nextSort(current, key));
  };

  const header = <AdminPageHeader title={t("pages.consents.title")} description={t("pages.consents.description")} />;

  if (!canRead) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <AccessDeniedState description={t("consents.dpoOnly")} />
      </div>
    );
  }

  const onLookup = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitted(true);
    if (!isUuid(userIdInput)) return;
    setUserId(userIdInput.trim());
    setExpanded(null);
    setPurposeFilter("");
  };

  const columnCount = 7;

  return (
    <div className="flex flex-col gap-4">
      {header}
      <DensePanel title={t("consents.lookupTitle")} description={t("consents.lookupDescription")}>
        <form className="grid items-start gap-3 lg:grid-cols-12" onSubmit={onLookup} noValidate>
          <div className="lg:col-span-8">
            <Input
              label={t("consents.userId")}
              value={userIdInput}
              autoComplete="off"
              spellCheck={false}
              className="h-10 font-mono"
              error={submitted && !isUuid(userIdInput) ? t("consents.userIdInvalid") : undefined}
              onChange={(event) => {
                setUserIdInput(event.target.value);
              }}
            />
          </div>
          <div className="flex lg:col-span-4 lg:pt-7">
            <Button type="submit" size="sm">
              <Search size={15} aria-hidden="true" />
              {t("consents.lookup")}
            </Button>
          </div>
        </form>
      </DensePanel>

      {userId === null ? (
        <EmptyState
          icon={<FileSearch size={22} />}
          title={t("consents.idleTitle")}
          description={t("consents.idleBody")}
        />
      ) : records.isPending ? (
        <>
          <KpiSkeleton />
          <TableSkeleton />
        </>
      ) : records.isError && records.data === undefined ? (
        <AdminQueryError
          error={records.error}
          onRetry={() => {
            void records.refetch();
          }}
        />
      ) : items.length === 0 ? (
        <EmptyState icon={<FileSearch size={22} />} title={t("consents.emptyTitle")} />
      ) : (
        <>
          <KpiGrid label={t("dense.kpiLabel")}>
            <KpiTile
              label={t("consents.kpiLoaded")}
              value={formatAdminNumber(items.length)}
              hint={records.hasNextPage ? t("consents.moreAvailable") : t("consents.allLoaded")}
              tone="info"
            />
            <KpiTile label={t("consents.kpiPurposes")} value={formatAdminNumber(latest.length)} />
            <KpiTile
              label={t("consents.kpiGranted")}
              value={formatAdminNumber(latest.filter((row) => row.status === "GRANTED").length)}
              hint={t("consents.currentHint")}
              tone="success"
            />
            <KpiTile
              label={t("consents.kpiWithdrawn")}
              value={formatAdminNumber(latest.filter((row) => row.status === "WITHDRAWN").length)}
              hint={t("consents.currentHint")}
              tone="warning"
            />
          </KpiGrid>

          <DensePanel title={t("consents.currentTitle")} description={t("consents.currentDescription")}>
            <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {latest.map((row) => (
                <li
                  key={row.purposeCode}
                  className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-caption font-semibold text-text-primary">
                      {purposeLabel.get(row.purposeCode) ?? row.purposeCode}
                    </span>
                    <span className="block font-mono text-small text-text-tertiary">
                      {formatAdminDateTime(row.occurredAt)}
                    </span>
                  </span>
                  <Badge tone={statusTone(row.status)}>{t(`consents.statuses.${row.status}`)}</Badge>
                </li>
              ))}
            </ul>
          </DensePanel>

          <DensePanel title={t("consents.historyTitle")}>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
              <div className="lg:col-span-6">
                <SearchField label={t("dense.search")} value={query} onChange={setQuery} />
              </div>
              <div className="lg:col-span-3">
                <AdminSelect
                  label={t("consents.colPurpose")}
                  value={purposeFilter}
                  className="h-10"
                  onChange={(event) => {
                    setPurposeFilter(event.target.value);
                  }}
                >
                  <option value="">{t("dense.all")}</option>
                  {purposeCodes.map((code) => (
                    <option key={code} value={code}>
                      {purposeLabel.get(code) ?? code}
                    </option>
                  ))}
                </AdminSelect>
              </div>
              <div className="lg:col-span-3">
                <AdminSelect
                  label={t("consents.colStatus")}
                  value={statusFilter}
                  className="h-10"
                  onChange={(event) => {
                    setStatusFilter(event.target.value as ConsentRecordStatus | "");
                  }}
                >
                  <option value="">{t("dense.all")}</option>
                  {STATUSES.map((status) => (
                    <option key={status} value={status}>
                      {t(`consents.statuses.${status}`)}
                    </option>
                  ))}
                </AdminSelect>
              </div>
            </div>

            {visible.length === 0 ? (
              <p className="py-6 text-center text-caption text-text-secondary">{t("dense.noMatch")}</p>
            ) : (
              <DenseTableFrame label={t("consents.historyTitle")}>
                <table className={denseTableClass}>
                  <thead>
                    <tr>
                      <th scope="col" className={`${denseThClass} w-10`}>
                        <span className="sr-only">{t("consents.expand")}</span>
                      </th>
                      <SortableTh label={t("consents.colOccurred")} sortKey="occurredAt" sort={sort} onSort={onSort} />
                      <SortableTh label={t("consents.colPurpose")} sortKey="purposeCode" sort={sort} onSort={onSort} />
                      <SortableTh label={t("consents.colStatus")} sortKey="status" sort={sort} onSort={onSort} />
                      <SortableTh label={t("consents.colMethod")} sortKey="method" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        {t("consents.colSurface")}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t("consents.colPolicy")}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((row) => {
                      const isOpen = expanded === row.id;
                      const detailId = `consent-detail-${row.id}`;
                      return (
                        <Fragment key={row.id}>
                          <tr className={denseRowClass}>
                            <td className={denseTdClass}>
                              <button
                                type="button"
                                aria-expanded={isOpen}
                                aria-controls={detailId}
                                aria-label={t("consents.toggleDetail", { at: formatAdminDateTime(row.occurredAt) })}
                                onClick={() => {
                                  setExpanded(isOpen ? null : row.id);
                                }}
                                className="flex size-8 items-center justify-center rounded-md text-text-secondary hover:bg-background-alt"
                              >
                                {isOpen ? (
                                  <ChevronDown size={16} aria-hidden="true" />
                                ) : (
                                  <ChevronRight size={16} aria-hidden="true" />
                                )}
                              </button>
                            </td>
                            <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                              {formatAdminDateTime(row.occurredAt)}
                            </td>
                            <td className={denseTdClass}>
                              <span className="block">{purposeLabel.get(row.purposeCode) ?? row.purposeCode}</span>
                              <code className="font-mono text-small text-text-tertiary">{row.purposeCode}</code>
                            </td>
                            <td className={denseTdClass}>
                              <Badge tone={statusTone(row.status)}>{t(`consents.statuses.${row.status}`)}</Badge>
                            </td>
                            <td className={denseTdClass}>
                              {t(`consents.methods.${row.method}`, { defaultValue: row.method })}
                            </td>
                            <td className={`${denseTdClass} font-mono text-small`}>{row.uiSurface ?? "—"}</td>
                            <td className={denseTdClass}>
                              <MonoId value={row.policyVersionId} />
                            </td>
                          </tr>
                          {isOpen ? (
                            <tr id={detailId}>
                              <td
                                colSpan={columnCount}
                                className="border-b border-border bg-background-alt/50 px-4 py-3"
                              >
                                <DetailGrid
                                  items={[
                                    [t("consents.recordId"), <MonoId key="id" value={row.id} chars={36} />],
                                    [
                                      t("consents.supersedes"),
                                      <MonoId key="sup" value={row.supersedesId} chars={36} />,
                                    ],
                                    [t("consents.policyHash"), <MonoId key="ph" value={row.policyHash} chars={24} />],
                                    [
                                      t("consents.textHash"),
                                      <MonoId key="th" value={row.consentTextHash} chars={24} />,
                                    ],
                                    [t("consents.locale"), row.locale ?? "—"],
                                    [t("consents.requestId"), <MonoId key="rq" value={row.requestId} chars={36} />],
                                    [
                                      t("consents.ip"),
                                      <code key="ip" className="font-mono">
                                        {row.ipAddress ?? "—"}
                                      </code>,
                                    ],
                                    [t("consents.userAgent"), row.userAgent ?? "—"],
                                    [t("consents.createdAt"), formatAdminDateTime(row.createdAt)],
                                  ]}
                                />
                                {row.evidence !== null && row.evidence !== undefined ? (
                                  <div className="pt-3">
                                    <p className="text-small text-text-tertiary">{t("consents.evidence")}</p>
                                    <pre className="max-h-48 overflow-auto rounded-md bg-surface p-2 font-mono text-small text-text-primary">
                                      {JSON.stringify(row.evidence, null, 2)}
                                    </pre>
                                  </div>
                                ) : null}
                              </td>
                            </tr>
                          ) : null}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </DenseTableFrame>
            )}
            {records.hasNextPage ? (
              <div className="flex justify-center">
                <Button
                  variant="tertiary"
                  size="sm"
                  disabled={records.isFetchingNextPage}
                  loading={records.isFetchingNextPage}
                  onClick={() => {
                    void records.fetchNextPage();
                  }}
                >
                  {t("consents.loadMore")}
                </Button>
              </div>
            ) : null}
            {records.isFetchNextPageError ? (
              <AdminQueryError
                error={records.error}
                onRetry={() => {
                  void records.fetchNextPage();
                }}
              />
            ) : null}
          </DensePanel>
        </>
      )}
    </div>
  );
}
