import { useEffect, useMemo, useState, type SyntheticEvent } from "react";
import { useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowRightLeft, Eye, ImageOff, Info, Search } from "lucide-react";
import { Badge, Button, Dialog, DialogContent, DialogDescription, DialogTitle, EmptyState, Input } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { formatPh } from "@/shared/lib/format/formatPh";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  BulkActionBar,
  DenseTableFrame,
  DensePanel,
  DetailGrid,
  DistributionBar,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  MonoId,
  MutationErrorNote,
  Pager,
  ReasonField,
  SHARED_UNKNOWN,
  SearchField,
  SelectCheckbox,
  SortableTh,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isReasonValid,
  isUuid,
  matchesQuery,
  nextSort,
  planReassign,
  pruneSelection,
  runSequential,
  selectionState,
  sortRows,
  summarizeBulk,
  toggleAllVisible,
  toggleSelection,
  useAdminCan,
  useAdminScanImage,
  useAdminUserCats,
  useAdminUserScans,
  useReassignAdminScan,
  type AdminScanItem,
  type BulkOutcome,
  type ReassignTarget,
  type SortState,
} from "@/features/admin";

const PAGE_SIZE = 50;
const SCAN_STATUSES = ["PENDING", "ANALYZED", "FAILED", "DISCARDED"] as const;

type ScanSortKey = "capturedAt" | "phValue" | "confidence" | "status" | "reassignCount";

function sortValue(scan: AdminScanItem, key: ScanSortKey): string | number | null | undefined {
  switch (key) {
    case "capturedAt":
      return Date.parse(scan.capturedAt);
    case "phValue":
      return scan.phValue;
    case "confidence":
      return scan.confidence;
    case "status":
      return scan.status;
    case "reassignCount":
      return scan.reassignCount;
  }
}

function statusTone(status: string): "success" | "warning" | "danger" | "neutral" {
  if (status === "ANALYZED") return "success";
  if (status === "PENDING") return "warning";
  if (status === "FAILED") return "danger";
  return "neutral";
}

interface Lookup {
  userId: string;
  reason: string;
}

export function AdminScansPage() {
  const { t } = useTranslation("admin");
  const can = useAdminCan();
  const [searchParams] = useSearchParams();
  const [userIdInput, setUserIdInput] = useState(searchParams.get("userId") ?? "");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [lookup, setLookup] = useState<Lookup | null>(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sort, setSort] = useState<SortState<ScanSortKey> | null>({ key: "capturedAt", direction: "desc" });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [detail, setDetail] = useState<AdminScanItem | null>(null);
  const [reassignFor, setReassignFor] = useState<AdminScanItem[] | null>(null);

  const scans = useAdminUserScans({
    userId: lookup?.userId ?? "",
    reason: lookup?.reason ?? "",
    page,
    size: PAGE_SIZE,
    enabled: lookup !== null && can("scans"),
  });

  const items = useMemo(() => scans.data?.items ?? [], [scans.data]);
  const visible = useMemo(
    () =>
      sortRows(
        items.filter(
          (scan) =>
            (statusFilter === "" || scan.status === statusFilter) &&
            matchesQuery([scan.scanId, scan.catName, scan.classification, scan.status, scan.assignment], query),
        ),
        sort,
        sortValue,
      ),
    [items, statusFilter, query, sort],
  );
  const visibleIds = visible.map((scan) => scan.scanId);

  useEffect(() => {
    setSelected((current) =>
      pruneSelection(
        current,
        visible.map((scan) => scan.scanId),
      ),
    );
  }, [visible]);

  if (!can("scans")) {
    return (
      <div className="flex flex-col gap-4">
        <AdminPageHeader title={t("pages.scans.title")} description={t("pages.scans.description")} />
        <AccessDeniedState />
      </div>
    );
  }

  const userIdError = submitted && !isUuid(userIdInput) ? t("scans.userIdInvalid") : undefined;
  const canReassign = can("scanReassign");

  const onLookup = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitted(true);
    if (!isUuid(userIdInput) || !isReasonValid(reason)) {
      return;
    }
    setLookup({ userId: userIdInput.trim(), reason: reason.trim() });
    setPage(0);
    setSelected(new Set());
  };

  const counts = {
    disputed: items.filter((scan) => scan.disputed).length,
    reassigned: items.filter((scan) => scan.reassignCount > 0).length,
    inconclusive: items.filter((scan) => scan.classification === "INCONCLUSIVE").length,
  };

  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader title={t("pages.scans.title")} description={t("pages.scans.description")} />

      <DensePanel title={t("scans.lookupTitle")} description={t("scans.auditNote")}>
        <form className="grid gap-3 lg:grid-cols-12" onSubmit={onLookup} noValidate>
          <div className="lg:col-span-5">
            <Input
              label={t("scans.userId")}
              value={userIdInput}
              autoComplete="off"
              spellCheck={false}
              className="h-10 font-mono"
              error={userIdError}
              helperText={t("scans.userIdHelp")}
              onChange={(event) => {
                setUserIdInput(event.target.value);
              }}
            />
          </div>
          <div className="lg:col-span-7">
            <ReasonField value={reason} onChange={setReason} showError={submitted} />
          </div>
          <div className="flex justify-end lg:col-span-12">
            <Button type="submit" size="sm">
              <Search size={15} aria-hidden="true" />
              {t("scans.lookup")}
            </Button>
          </div>
        </form>
      </DensePanel>

      {lookup === null ? (
        <EmptyState icon={<Search size={22} />} title={t("scans.idleTitle")} description={t("scans.idleBody")} />
      ) : scans.isPending ? (
        <>
          <KpiSkeleton />
          <TableSkeleton />
        </>
      ) : scans.isError ? (
        <AdminQueryError
          error={scans.error}
          onRetry={() => {
            void scans.refetch();
          }}
        />
      ) : (
        <>
          <KpiGrid label={t("dense.kpiLabel")}>
            <KpiTile label={t("scans.kpiTotal")} value={formatAdminNumber(scans.data.page.totalElements)} tone="info" />
            <KpiTile
              label={t("scans.kpiDisputed")}
              value={formatAdminNumber(counts.disputed)}
              hint={t("dense.onPageHint")}
              tone={counts.disputed > 0 ? "warning" : "neutral"}
            />
            <KpiTile
              label={t("scans.kpiReassigned")}
              value={formatAdminNumber(counts.reassigned)}
              hint={t("dense.onPageHint")}
            />
            <KpiTile
              label={t("scans.kpiInconclusive")}
              value={formatAdminNumber(counts.inconclusive)}
              hint={t("dense.onPageHint")}
            />
          </KpiGrid>

          {scans.data.masked ? (
            <p className="flex items-start gap-2 rounded-lg bg-info px-3 py-2 text-caption text-info-text">
              <Info size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
              {t("scans.maskedNote")}
            </p>
          ) : null}

          <DensePanel
            title={t("scans.listTitle")}
            actions={
              <span className="font-mono text-small text-text-tertiary">
                <MonoId value={lookup.userId} chars={13} />
              </span>
            }
          >
            {items.length === 0 ? (
              <EmptyState icon={<ImageOff size={22} />} title={t("scans.emptyTitle")} />
            ) : (
              <>
                <DistributionBar
                  label={t("scans.distribution")}
                  segments={SCAN_STATUSES.map((status) => ({
                    key: status,
                    label: t(`scans.statuses.${status}`),
                    value: items.filter((scan) => scan.status === status).length,
                    tone: statusTone(status),
                  }))}
                />
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
                  <div className="lg:col-span-6">
                    <SearchField label={t("dense.search")} value={query} onChange={setQuery} />
                  </div>
                  <div className="lg:col-span-3">
                    <AdminSelect
                      label={t("scans.colStatus")}
                      value={statusFilter}
                      className="h-10"
                      onChange={(event) => {
                        setStatusFilter(event.target.value);
                      }}
                    >
                      <option value="">{t("dense.all")}</option>
                      {SCAN_STATUSES.map((status) => (
                        <option key={status} value={status}>
                          {t(`scans.statuses.${status}`)}
                        </option>
                      ))}
                    </AdminSelect>
                  </div>
                </div>

                {canReassign ? (
                  <BulkActionBar
                    count={selected.size}
                    onClear={() => {
                      setSelected(new Set());
                    }}
                  >
                    <Button
                      variant="tertiary"
                      size="sm"
                      onClick={() => {
                        setReassignFor(items.filter((scan) => selected.has(scan.scanId)));
                      }}
                    >
                      <ArrowRightLeft size={14} aria-hidden="true" />
                      {t("scans.reassignBulk", { count: selected.size })}
                    </Button>
                  </BulkActionBar>
                ) : null}

                {visible.length === 0 ? (
                  <p className="py-6 text-center text-caption text-text-secondary">{t("dense.noMatch")}</p>
                ) : (
                  <DenseTableFrame label={t("scans.listTitle")}>
                    <table className={denseTableClass}>
                      <thead>
                        <tr>
                          {canReassign ? (
                            <th scope="col" className={`${denseThClass} w-10`}>
                              <SelectCheckbox
                                label={t("dense.selectAll")}
                                checked={selectionState(selected, visibleIds)}
                                onChange={() => {
                                  setSelected((current) => toggleAllVisible(current, visibleIds));
                                }}
                              />
                            </th>
                          ) : null}
                          <SortableTh
                            label={t("scans.colCapturedAt")}
                            sortKey="capturedAt"
                            sort={sort}
                            onSort={(key) => {
                              setSort((current) => nextSort(current, key));
                            }}
                          />
                          <th scope="col" className={denseThClass}>
                            {t("scans.colCat")}
                          </th>
                          <SortableTh
                            label={t("scans.colStatus")}
                            sortKey="status"
                            sort={sort}
                            onSort={(key) => {
                              setSort((current) => nextSort(current, key));
                            }}
                          />
                          <SortableTh
                            label={t("scans.colPh")}
                            sortKey="phValue"
                            sort={sort}
                            onSort={(key) => {
                              setSort((current) => nextSort(current, key));
                            }}
                          />
                          <th scope="col" className={denseThClass}>
                            {t("scans.colClassification")}
                          </th>
                          <SortableTh
                            label={t("scans.colConfidence")}
                            sortKey="confidence"
                            sort={sort}
                            onSort={(key) => {
                              setSort((current) => nextSort(current, key));
                            }}
                          />
                          <th scope="col" className={denseThClass}>
                            {t("scans.colFlags")}
                          </th>
                          <SortableTh
                            label={t("scans.colReassign")}
                            sortKey="reassignCount"
                            sort={sort}
                            onSort={(key) => {
                              setSort((current) => nextSort(current, key));
                            }}
                          />
                          <th scope="col" className={denseThClass}>
                            <span className="sr-only">{t("scans.colActions")}</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {visible.map((scan) => {
                          const isSelected = selected.has(scan.scanId);
                          return (
                            <tr key={scan.scanId} className={denseRowClass} data-selected={isSelected}>
                              {canReassign ? (
                                <td className={denseTdClass}>
                                  <SelectCheckbox
                                    label={t("dense.selectRow", { label: formatAdminDateTime(scan.capturedAt) })}
                                    checked={isSelected}
                                    onChange={() => {
                                      setSelected((current) => toggleSelection(current, scan.scanId));
                                    }}
                                  />
                                </td>
                              ) : null}
                              <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                                {formatAdminDateTime(scan.capturedAt)}
                              </td>
                              <td className={denseTdClass}>
                                <span className="block max-w-[160px] truncate">
                                  {scan.catName ??
                                    t(`scans.assignments.${scan.assignment}`, { defaultValue: scan.assignment })}
                                </span>
                              </td>
                              <td className={denseTdClass}>
                                <Badge tone={statusTone(scan.status)}>
                                  {t(`scans.statuses.${scan.status}`, { defaultValue: scan.status })}
                                </Badge>
                              </td>
                              <td className={`${denseTdClass} font-mono tabular-nums`}>{formatPh(scan.phValue)}</td>
                              <td className={denseTdClass}>
                                <code className="font-mono text-small text-text-secondary">
                                  {scan.classification ?? "—"}
                                </code>
                              </td>
                              <td className={`${denseTdClass} font-mono tabular-nums`}>
                                {formatAdminNumber(scan.confidence, { maximumFractionDigits: 2 })}
                              </td>
                              <td className={denseTdClass}>
                                <span className="flex flex-wrap gap-1">
                                  {scan.disputed ? <Badge tone="warning">{t("scans.flagDisputed")}</Badge> : null}
                                  {scan.nearBoundary ? <Badge tone="info">{t("scans.flagNearBoundary")}</Badge> : null}
                                  {scan.trial ? <Badge>{t("scans.flagTrial")}</Badge> : null}
                                </span>
                              </td>
                              <td className={`${denseTdClass} font-mono tabular-nums`}>{scan.reassignCount}</td>
                              <td className={`${denseTdClass} text-right`}>
                                <Button
                                  variant="tertiary"
                                  size="sm"
                                  className="min-h-8 px-2"
                                  onClick={() => {
                                    setDetail(scan);
                                  }}
                                >
                                  <Eye size={14} aria-hidden="true" />
                                  {t("scans.view")}
                                </Button>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </DenseTableFrame>
                )}
                <Pager
                  page={scans.data.page.number}
                  totalPages={scans.data.page.totalPages}
                  totalElements={scans.data.page.totalElements}
                  onPageChange={(next) => {
                    setPage(next);
                    setSelected(new Set());
                  }}
                />
              </>
            )}
          </DensePanel>
        </>
      )}

      <ScanDetailDialog
        scan={detail}
        lookupReason={lookup?.reason ?? ""}
        canImage={can("scanImage")}
        canReassign={canReassign}
        onClose={() => {
          setDetail(null);
        }}
        onReassign={(scan) => {
          setDetail(null);
          setReassignFor([scan]);
        }}
      />

      {lookup !== null && reassignFor !== null ? (
        <ReassignDialog
          userId={lookup.userId}
          lookupReason={lookup.reason}
          scans={reassignFor}
          onClose={(outcome) => {
            setReassignFor(null);
            if (outcome !== null) {
              setSelected(new Set());
            }
          }}
        />
      ) : null}
    </div>
  );
}

interface ScanDetailDialogProps {
  scan: AdminScanItem | null;
  lookupReason: string;
  canImage: boolean;
  canReassign: boolean;
  onClose: () => void;
  onReassign: (scan: AdminScanItem) => void;
}

function ScanDetailDialog({ scan, lookupReason, canImage, canReassign, onClose, onReassign }: ScanDetailDialogProps) {
  const { t } = useTranslation("admin");
  return (
    <Dialog
      open={scan !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogTitle>{t("scans.detailTitle")}</DialogTitle>
        <DialogDescription className="font-mono">{scan?.scanId}</DialogDescription>
        {scan !== null ? (
          <div className="flex flex-col gap-4 pt-3">
            <DetailGrid
              items={[
                [t("scans.colCapturedAt"), formatAdminDateTime(scan.capturedAt)],
                [t("scans.colStatus"), t(`scans.statuses.${scan.status}`, { defaultValue: scan.status })],
                [
                  t("scans.colAssignment"),
                  t(`scans.assignments.${scan.assignment}`, { defaultValue: scan.assignment }),
                ],
                [t("scans.colCat"), scan.catName ?? "—"],
                [t("scans.catId"), <MonoId key="cat" value={scan.catId} chars={36} />],
                [
                  t("scans.colPh"),
                  <span key="ph" className="font-mono">
                    {formatPh(scan.phValue)}
                  </span>,
                ],
                [t("scans.colClassification"), <code key="cls">{scan.classification ?? "—"}</code>],
                [
                  t("scans.colConfidence"),
                  <span key="conf" className="font-mono">
                    {formatAdminNumber(scan.confidence, { maximumFractionDigits: 3 })}
                  </span>,
                ],
                [
                  t("scans.lab"),
                  <span key="lab" className="font-mono">
                    {[scan.labL, scan.labA, scan.labB]
                      .map((value) => formatAdminNumber(value, { maximumFractionDigits: 2 }))
                      .join(" · ")}
                  </span>,
                ],
                [t("scans.calibration"), <code key="cal">{scan.calibrationMethod ?? "—"}</code>],
                [t("scans.engine"), <code key="eng">{scan.engineVersion ?? "—"}</code>],
                [t("scans.flagCredit"), scan.creditCharged ? t("dense.yes") : t("dense.no")],
                [t("scans.flagTrial"), scan.trial ? t("dense.yes") : t("dense.no")],
                [t("scans.flagNearBoundary"), scan.nearBoundary ? t("dense.yes") : t("dense.no")],
                [t("scans.flagDisputed"), scan.disputed ? t("dense.yes") : t("dense.no")],
                [t("scans.disputedNote"), scan.disputedNote ?? "—"],
                [t("scans.colReassign"), String(scan.reassignCount)],
                [
                  t("scans.imageStored"),
                  scan.imageStored === null || scan.imageStored === undefined
                    ? t("scans.imageUnknown")
                    : scan.imageStored
                      ? t("dense.yes")
                      : t("dense.no"),
                ],
              ]}
            />
            {canImage ? <ScanImagePanel key={scan.scanId} scanId={scan.scanId} defaultReason={lookupReason} /> : null}
            <div className="flex flex-wrap justify-end gap-2 border-t border-border pt-3">
              {canReassign ? (
                <Button
                  variant="tertiary"
                  size="sm"
                  onClick={() => {
                    onReassign(scan);
                  }}
                >
                  <ArrowRightLeft size={14} aria-hidden="true" />
                  {t("scans.reassignTitle")}
                </Button>
              ) : null}
              <Button variant="secondary" size="sm" onClick={onClose}>
                {t("dense.close")}
              </Button>
            </div>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function ScanImagePanel({ scanId, defaultReason }: { scanId: string; defaultReason: string }) {
  const { t } = useTranslation("admin");
  const [reason, setReason] = useState(defaultReason);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const image = useAdminScanImage();

  useEffect(
    () => () => {
      if (objectUrl !== null) URL.revokeObjectURL(objectUrl);
    },
    [objectUrl],
  );

  const imageErrorText = (): string | null => {
    if (!isApiError(image.error)) return null;
    if (image.error.code === "SCAN_IMAGE_DSAR_REQUIRED") return t("scans.imageErrors.dsarRequired");
    if (image.error.status === 404) return t("scans.imageErrors.notStored");
    if (image.error.status === 410) return t("scans.imageErrors.expired");
    return null;
  };
  const specific = imageErrorText();

  return (
    <section className="flex flex-col gap-3 rounded-lg border border-border p-3" aria-label={t("scans.imageTitle")}>
      <h3 className="text-caption font-bold text-text-primary">{t("scans.imageTitle")}</h3>
      {objectUrl !== null ? (
        <img
          src={objectUrl}
          alt={t("scans.imageAlt", { id: scanId.slice(0, 8) })}
          className="max-h-[50vh] w-full rounded-md bg-background-alt object-contain"
        />
      ) : (
        <>
          <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
          <p className="text-small text-text-tertiary">{t("scans.imageReasonNote")}</p>
          <div className="flex justify-end">
            <Button
              size="sm"
              variant="tertiary"
              disabled={!isReasonValid(reason) || image.isPending}
              loading={image.isPending}
              onClick={() => {
                image.mutate(
                  { scanId, reason: reason.trim() },
                  {
                    onSuccess: (blob) => {
                      setObjectUrl(URL.createObjectURL(blob));
                    },
                  },
                );
              }}
            >
              <Eye size={14} aria-hidden="true" />
              {t("scans.imageOpen")}
            </Button>
          </div>
        </>
      )}
      {specific !== null ? (
        <p role="alert" className="rounded-lg bg-warning-bg px-3 py-2 text-caption text-warning-text">
          {specific}
        </p>
      ) : (
        <MutationErrorNote error={image.error} />
      )}
    </section>
  );
}

interface ReassignDialogProps {
  userId: string;
  lookupReason: string;
  scans: AdminScanItem[];
  onClose: (outcome: BulkOutcome | null) => void;
}

function ReassignDialog({ userId, lookupReason, scans, onClose }: ReassignDialogProps) {
  const { t } = useTranslation("admin");
  const cats = useAdminUserCats(userId, lookupReason, true);
  const reassign = useReassignAdminScan();
  const [targetValue, setTargetValue] = useState("");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [running, setRunning] = useState<{ done: number; total: number } | null>(null);
  const [outcome, setOutcome] = useState<(BulkOutcome & { skipped: number }) | null>(null);

  const target: ReassignTarget | null =
    targetValue === ""
      ? null
      : targetValue === SHARED_UNKNOWN
        ? { kind: "shared" }
        : { kind: "cat", catId: targetValue };
  const summary = outcome !== null ? summarizeBulk(outcome) : null;

  const submit = async () => {
    setSubmitted(true);
    if (target === null || !isReasonValid(reason)) return;
    const plan = planReassign(scans, new Set(scans.map((scan) => scan.scanId)), target);
    setRunning({ done: 0, total: plan.tasks.length });
    const result = await runSequential(
      plan.tasks,
      (scanId) => scanId,
      (scanId) => reassign.mutateAsync({ scanId, target, reason: reason.trim() }),
      (done, total) => {
        setRunning({ done, total });
      },
    );
    setRunning(null);
    setOutcome({ ...result, skipped: plan.skipped.length });
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && running === null) onClose(outcome);
      }}
    >
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogTitle>{t("scans.reassignTitle")}</DialogTitle>
        <DialogDescription>{t("scans.reassignBulk", { count: scans.length })}</DialogDescription>
        <div className="flex flex-col gap-3 pt-3">
          {cats.isPending ? (
            <p className="text-caption text-text-secondary">{t("scans.catsLoading")}</p>
          ) : cats.isError ? (
            <MutationErrorNote error={cats.error} />
          ) : (
            <AdminSelect
              label={t("scans.reassignTarget")}
              value={targetValue}
              aria-invalid={submitted && target === null ? true : undefined}
              onChange={(event) => {
                setTargetValue(event.target.value);
              }}
            >
              <option value="">{t("scans.chooseTarget")}</option>
              <option value={SHARED_UNKNOWN}>{t("scans.assignments.SHARED_UNKNOWN")}</option>
              {cats.data.items.map((cat) => (
                <option key={cat.catId} value={cat.catId}>
                  {[cat.name ?? cat.publicCode ?? cat.catId.slice(0, 8), cat.primary ? t("scans.primaryCat") : null]
                    .filter(Boolean)
                    .join(" · ")}
                </option>
              ))}
            </AdminSelect>
          )}
          <ReasonField value={reason} onChange={setReason} showError={submitted} disabled={running !== null} />

          {running !== null ? (
            <p aria-live="polite" className="text-caption text-text-secondary">
              {t("dense.bulkProgress", { done: running.done, total: running.total })}
            </p>
          ) : null}
          {summary !== null && outcome !== null ? (
            <div aria-live="polite" className="rounded-lg bg-background-alt px-3 py-2 text-caption text-text-primary">
              <p>{t("dense.bulkResult", { ok: summary.ok, failed: summary.failed, skipped: outcome.skipped })}</p>
              {outcome.failed.length > 0 ? (
                <ul className="pt-1 text-small text-danger-text">
                  {outcome.failed.map((failure) => (
                    <li key={failure.id}>
                      <MonoId value={failure.id} />
                      {" — "}
                      {failure.message}
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : null}

          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button
              variant="tertiary"
              size="sm"
              disabled={running !== null}
              onClick={() => {
                onClose(outcome);
              }}
            >
              {outcome !== null ? t("dense.close") : t("actions.cancel")}
            </Button>
            {outcome === null ? (
              <Button
                size="sm"
                disabled={running !== null || cats.isPending}
                loading={running !== null}
                onClick={() => {
                  void submit();
                }}
              >
                {t("scans.reassignSubmit")}
              </Button>
            ) : null}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
