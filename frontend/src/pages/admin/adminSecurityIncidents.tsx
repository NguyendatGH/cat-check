import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Clock, FilePlus2, FolderOpen, Lock, Save, ShieldCheck } from "lucide-react";
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  EmptyState,
  Input,
  Switch,
  toast,
} from "@/shared/ui";
import {
  AdminQueryError,
  AdminSelect,
  DenseTableFrame,
  DensePanel,
  DetailGrid,
  DistributionBar,
  INCIDENT_CATEGORIES,
  INCIDENT_MILESTONES,
  INCIDENT_SEVERITIES,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  MonoId,
  MutationErrorNote,
  Pager,
  ReasonField,
  SearchField,
  SortableTh,
  TableSkeleton,
  TextAreaField,
  buildIncidentPatch,
  buildOpenIncidentPayload,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  emptyIncidentForm,
  formatAdminDateTime,
  formatAdminNumber,
  incidentToForm,
  matchesQuery,
  nextSort,
  sortRows,
  toLocalInputValue,
  useAdminSecurityIncident,
  useAdminSecurityIncidents,
  useOpenSecurityIncident,
  useUpdateSecurityIncident,
  type AdminSecurityIncident,
  type IncidentCategory,
  type IncidentForm,
  type IncidentFormErrors,
  type IncidentSeverity,
  type SortState,
} from "@/features/admin";

const PAGE_SIZE = 50;

type IncidentSortKey = "detectedAt" | "severity" | "authorityDeadline" | "affectedSubjectCount";

export function severityTone(severity: IncidentSeverity): "neutral" | "info" | "warning" | "danger" {
  if (severity === "CRITICAL") return "danger";
  if (severity === "HIGH") return "warning";
  if (severity === "MEDIUM") return "info";
  return "neutral";
}

function incidentSortValue(incident: AdminSecurityIncident, key: IncidentSortKey): number | null | undefined {
  switch (key) {
    case "detectedAt":
      return Date.parse(incident.detectedAt);
    case "severity":
      return INCIDENT_SEVERITIES.indexOf(incident.severity);
    case "authorityDeadline":
      return incident.authorityDeadline === null || incident.authorityDeadline === undefined
        ? null
        : Date.parse(incident.authorityDeadline);
    case "affectedSubjectCount":
      return incident.affectedSubjectCount;
  }
}

/** Tab "Sự cố" — hàng chờ hồ sơ sự cố, mở mới và cập nhật mốc 72 giờ. */
export function SecurityIncidentsTab({ canWrite }: { canWrite: boolean }) {
  const { t } = useTranslation("admin");
  const [severity, setSeverity] = useState<IncidentSeverity | "">("");
  const [unresolved, setUnresolved] = useState(false);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<IncidentSortKey> | null>({ key: "detectedAt", direction: "desc" });
  const [openId, setOpenId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const list = useAdminSecurityIncidents({ severity, unresolved, page, size: PAGE_SIZE });
  // Hai truy vấn đếm (size = 1) cho hàng KPI: tổng số và số chưa khắc phục, không phụ thuộc bộ lọc.
  const totalCount = useAdminSecurityIncidents({ severity: "", unresolved: false, page: 0, size: 1 });
  const openCount = useAdminSecurityIncidents({ severity: "", unresolved: true, page: 0, size: 1 });

  const items = useMemo(() => list.data?.items ?? [], [list.data]);
  const visible = useMemo(
    () =>
      sortRows(
        items.filter((incident) =>
          matchesQuery([incident.publicRef, incident.summary, incident.category, incident.reportRef], query),
        ),
        sort,
        incidentSortValue,
      ),
    [items, query, sort],
  );
  const onSort = (key: IncidentSortKey) => {
    setSort((current) => nextSort(current, key));
  };
  const overdueOnPage = items.filter((incident) => incident.authorityNotificationOverdue === true).length;

  return (
    <div className="flex flex-col gap-4">
      {totalCount.isPending || openCount.isPending ? (
        <KpiSkeleton />
      ) : (
        <KpiGrid label={t("dense.kpiLabel")}>
          <KpiTile
            label={t("security.kpiTotal")}
            value={formatAdminNumber(totalCount.data?.totalElements)}
            tone="info"
          />
          <KpiTile
            label={t("security.kpiOpen")}
            value={formatAdminNumber(openCount.data?.totalElements)}
            tone={(openCount.data?.totalElements ?? 0) > 0 ? "warning" : "success"}
          />
          <KpiTile
            label={t("security.kpiOverdue")}
            value={formatAdminNumber(overdueOnPage)}
            hint={t("dense.onPageHint")}
            tone={overdueOnPage > 0 ? "danger" : "neutral"}
          />
          <KpiTile
            label={t("security.kpiHighCritical")}
            value={formatAdminNumber(
              items.filter((incident) => incident.severity === "HIGH" || incident.severity === "CRITICAL").length,
            )}
            hint={t("dense.onPageHint")}
          />
        </KpiGrid>
      )}

      <DensePanel
        title={t("security.incidentsTitle")}
        description={t("security.incidentsDescription")}
        actions={
          canWrite ? (
            <Button
              size="sm"
              onClick={() => {
                setCreating(true);
              }}
            >
              <FilePlus2 size={14} aria-hidden="true" />
              {t("security.openIncident")}
            </Button>
          ) : (
            <span className="inline-flex items-center gap-1 text-small text-text-tertiary">
              <Lock size={12} aria-hidden="true" />
              {t("security.readOnly")}
            </span>
          )
        }
      >
        <div className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <SearchField label={t("dense.search")} value={query} onChange={setQuery} />
          </div>
          <div className="lg:col-span-3">
            <AdminSelect
              label={t("security.colSeverity")}
              value={severity}
              className="h-10"
              onChange={(event) => {
                setSeverity(event.target.value as IncidentSeverity | "");
                setPage(0);
              }}
            >
              <option value="">{t("dense.all")}</option>
              {INCIDENT_SEVERITIES.map((value) => (
                <option key={value} value={value}>
                  {t(`security.severities.${value}`)}
                </option>
              ))}
            </AdminSelect>
          </div>
          <div className="lg:col-span-4">
            <Switch
              label={t("security.unresolvedOnly")}
              checked={unresolved}
              onCheckedChange={(checked) => {
                setUnresolved(checked);
                setPage(0);
              }}
            />
          </div>
        </div>

        {list.isPending ? (
          <TableSkeleton />
        ) : list.isError ? (
          <AdminQueryError
            error={list.error}
            onRetry={() => {
              void list.refetch();
            }}
          />
        ) : items.length === 0 ? (
          <EmptyState icon={<ShieldCheck size={22} />} title={t("security.emptyTitle")} />
        ) : (
          <>
            <DistributionBar
              label={t("security.severityDistribution")}
              segments={INCIDENT_SEVERITIES.map((value) => ({
                key: value,
                label: t(`security.severities.${value}`),
                value: items.filter((incident) => incident.severity === value).length,
                tone: severityTone(value),
              }))}
            />
            {visible.length === 0 ? (
              <p className="py-6 text-center text-caption text-text-secondary">{t("dense.noMatch")}</p>
            ) : (
              <DenseTableFrame label={t("security.incidentsTitle")}>
                <table className={denseTableClass}>
                  <thead>
                    <tr>
                      <th scope="col" className={denseThClass}>
                        {t("security.colRef")}
                      </th>
                      <SortableTh label={t("security.colSeverity")} sortKey="severity" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        {t("security.colCategory")}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t("security.colSummary")}
                      </th>
                      <SortableTh label={t("security.colDetected")} sortKey="detectedAt" sort={sort} onSort={onSort} />
                      <SortableTh
                        label={t("security.colDeadline")}
                        sortKey="authorityDeadline"
                        sort={sort}
                        onSort={onSort}
                      />
                      <SortableTh
                        label={t("security.colAffected")}
                        sortKey="affectedSubjectCount"
                        sort={sort}
                        onSort={onSort}
                      />
                      <th scope="col" className={denseThClass}>
                        {t("security.colStatus")}
                      </th>
                      <th scope="col" className={denseThClass}>
                        <span className="sr-only">{t("security.colActions")}</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((incident) => (
                      <tr key={incident.id} className={denseRowClass}>
                        <td className={`${denseTdClass} font-mono text-small font-semibold`}>{incident.publicRef}</td>
                        <td className={denseTdClass}>
                          <Badge tone={severityTone(incident.severity)}>
                            {t(`security.severities.${incident.severity}`)}
                          </Badge>
                        </td>
                        <td className={denseTdClass}>{t(`security.categories.${incident.category}`)}</td>
                        <td className={denseTdClass}>
                          <span className="block max-w-[280px] truncate" title={incident.summary}>
                            {incident.summary}
                          </span>
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                          {formatAdminDateTime(incident.detectedAt)}
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap`}>
                          <span className="font-mono tabular-nums">
                            {formatAdminDateTime(incident.authorityDeadline)}
                          </span>
                          {incident.authorityNotificationOverdue === true ? (
                            <Badge tone="danger" className="ml-1.5">
                              {t("security.overdue")}
                            </Badge>
                          ) : incident.authorityNotifiedAt ? (
                            <Badge tone="success" className="ml-1.5">
                              {t("security.notified")}
                            </Badge>
                          ) : null}
                        </td>
                        <td className={`${denseTdClass} font-mono tabular-nums`}>
                          {formatAdminNumber(incident.affectedSubjectCount)}
                        </td>
                        <td className={denseTdClass}>
                          <Badge tone={incident.resolvedAt ? "success" : "warning"}>
                            {incident.resolvedAt ? t("security.resolved") : t("security.open")}
                          </Badge>
                        </td>
                        <td className={`${denseTdClass} text-right`}>
                          <Button
                            variant="tertiary"
                            size="sm"
                            className="min-h-8 px-2"
                            onClick={() => {
                              setOpenId(incident.id);
                            }}
                          >
                            <FolderOpen size={14} aria-hidden="true" />
                            {t("security.openRecord")}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </DenseTableFrame>
            )}
            <Pager
              page={list.data.page}
              totalPages={list.data.totalPages}
              totalElements={list.data.totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </DensePanel>

      {openId !== null ? (
        <IncidentDetailDialog
          incidentId={openId}
          canWrite={canWrite}
          onClose={() => {
            setOpenId(null);
          }}
        />
      ) : null}
      {creating ? (
        <OpenIncidentDialog
          onClose={() => {
            setCreating(false);
          }}
        />
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------------- form chung

function useIncidentErrorText() {
  const { t } = useTranslation("admin");
  return (errors: IncidentFormErrors, field: keyof IncidentForm): string | undefined => {
    const code = errors[field];
    return code === undefined ? undefined : t(`security.errors.${code}`);
  };
}

interface IncidentFieldsProps {
  form: IncidentForm;
  onChange: (next: IncidentForm) => void;
  errors: IncidentFormErrors;
  mode: "create" | "edit";
}

function IncidentFields({ form, onChange, errors, mode }: IncidentFieldsProps) {
  const { t } = useTranslation("admin");
  const errorText = useIncidentErrorText();
  const set = <K extends keyof IncidentForm>(key: K, value: IncidentForm[K]) => {
    onChange({ ...form, [key]: value });
  };
  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <AdminSelect
          label={t("security.colSeverity")}
          value={form.severity}
          onChange={(event) => {
            set("severity", event.target.value as IncidentSeverity);
          }}
        >
          {INCIDENT_SEVERITIES.map((value) => (
            <option key={value} value={value}>
              {t(`security.severities.${value}`)}
            </option>
          ))}
        </AdminSelect>
        <AdminSelect
          label={t("security.colCategory")}
          value={form.category}
          onChange={(event) => {
            set("category", event.target.value as IncidentCategory);
          }}
        >
          {INCIDENT_CATEGORIES.map((value) => (
            <option key={value} value={value}>
              {t(`security.categories.${value}`)}
            </option>
          ))}
        </AdminSelect>
      </div>
      <TextAreaField
        label={t("security.colSummary")}
        value={form.summary}
        rows={3}
        maxLength={4000}
        error={errorText(errors, "summary")}
        helperText={t("security.summaryHelp")}
        onChange={(value) => {
          set("summary", value);
        }}
      />
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label={t("security.colAffected")}
          inputMode="numeric"
          value={form.affectedSubjectCount}
          error={errorText(errors, "affectedSubjectCount")}
          className="h-10 font-mono"
          onChange={(event) => {
            set("affectedSubjectCount", event.target.value);
          }}
        />
        <Input
          label={t("security.dataCodes")}
          value={form.affectedDataCodes}
          error={errorText(errors, "affectedDataCodes")}
          helperText={t("security.dataCodesHelp")}
          className="h-10 font-mono"
          onChange={(event) => {
            set("affectedDataCodes", event.target.value);
          }}
        />
      </div>
      <Input
        label={t("security.reportRef")}
        value={form.reportRef}
        maxLength={500}
        error={errorText(errors, "reportRef")}
        className="h-10"
        onChange={(event) => {
          set("reportRef", event.target.value);
        }}
      />
      {mode === "create" ? (
        <Input
          type="datetime-local"
          label={t("security.colDetected")}
          value={form.detectedAt}
          error={errorText(errors, "detectedAt")}
          helperText={t("security.detectedHelp")}
          className="h-10 font-mono"
          onChange={(event) => {
            set("detectedAt", event.target.value);
          }}
        />
      ) : (
        <fieldset className="flex flex-col gap-3 rounded-lg border border-border p-3">
          <legend className="px-1 text-caption font-semibold text-text-secondary">{t("security.milestones")}</legend>
          <p className="text-small text-text-tertiary">{t("security.milestonesHelp")}</p>
          <div className="grid gap-3 sm:grid-cols-2">
            {INCIDENT_MILESTONES.map((field) => (
              <div key={field} className="flex items-end gap-2">
                <div className="min-w-0 flex-1">
                  <Input
                    type="datetime-local"
                    label={t(`security.milestone.${field}`)}
                    value={form[field]}
                    error={errorText(errors, field)}
                    className="h-10 font-mono"
                    onChange={(event) => {
                      set(field, event.target.value);
                    }}
                  />
                </div>
                {form[field] === "" ? (
                  <Button
                    type="button"
                    variant="tertiary"
                    size="sm"
                    className="mb-px min-h-10 px-2"
                    aria-label={t("security.markNowFor", { field: t(`security.milestone.${field}`) })}
                    onClick={() => {
                      set(field, toLocalInputValue(new Date().toISOString()));
                    }}
                  >
                    <Clock size={14} aria-hidden="true" />
                    {t("security.markNow")}
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        </fieldset>
      )}
    </div>
  );
}

// ------------------------------------------------------------------- chi tiết + sửa

function IncidentDetailDialog({
  incidentId,
  canWrite,
  onClose,
}: {
  incidentId: string;
  canWrite: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation("admin");
  const detail = useAdminSecurityIncident(incidentId);
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogTitle>{t("security.detailTitle")}</DialogTitle>
        <DialogDescription className="font-mono">{detail.data?.publicRef ?? ""}</DialogDescription>
        <div className="flex flex-col gap-4 pt-3">
          {detail.isPending ? (
            <TableSkeleton rows={4} />
          ) : detail.isError ? (
            <AdminQueryError
              error={detail.error}
              onRetry={() => {
                void detail.refetch();
              }}
            />
          ) : (
            <>
              <DetailGrid
                items={[
                  [
                    t("security.colSeverity"),
                    <Badge key="sev" tone={severityTone(detail.data.severity)}>
                      {t(`security.severities.${detail.data.severity}`)}
                    </Badge>,
                  ],
                  [t("security.colCategory"), t(`security.categories.${detail.data.category}`)],
                  [t("security.colDetected"), formatAdminDateTime(detail.data.detectedAt)],
                  [
                    t("security.colDeadline"),
                    <span key="dl" className="inline-flex items-center gap-1.5">
                      {formatAdminDateTime(detail.data.authorityDeadline)}
                      {detail.data.authorityNotificationOverdue === true ? (
                        <Badge tone="danger">{t("security.overdue")}</Badge>
                      ) : null}
                    </span>,
                  ],
                  ...INCIDENT_MILESTONES.map(
                    (field) => [t(`security.milestone.${field}`), formatAdminDateTime(detail.data[field])] as const,
                  ),
                  [t("security.retainUntil"), formatAdminDateTime(detail.data.retainUntil)],
                  [t("security.handledBy"), <MonoId key="hb" value={detail.data.handledBy} />],
                  [t("security.colAffected"), formatAdminNumber(detail.data.affectedSubjectCount)],
                  [t("security.dataCodes"), (detail.data.affectedDataCodes ?? []).join(", ") || "—"],
                  [t("security.reportRef"), detail.data.reportRef ?? "—"],
                ]}
              />
              <div>
                <p className="text-small text-text-tertiary">{t("security.colSummary")}</p>
                <p className="whitespace-pre-wrap text-caption text-text-primary">{detail.data.summary}</p>
              </div>
              {canWrite ? (
                <IncidentEditForm key={detail.data.id} incident={detail.data} onDone={onClose} />
              ) : (
                <div className="flex justify-end border-t border-border pt-3">
                  <Button variant="tertiary" size="sm" onClick={onClose}>
                    {t("dense.close")}
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function IncidentEditForm({ incident, onDone }: { incident: AdminSecurityIncident; onDone: () => void }) {
  const { t } = useTranslation("admin");
  const update = useUpdateSecurityIncident();
  const [form, setForm] = useState<IncidentForm>(() => incidentToForm(incident));
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const result = buildIncidentPatch(incident, form, reason);
  const errors: IncidentFormErrors = submitted && !result.ok ? result.errors : {};

  return (
    <form
      className="flex flex-col gap-3 border-t border-border pt-3"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        setSubmitted(true);
        if (!result.ok) return;
        update.mutate(
          { incidentId: incident.id, payload: result.payload },
          {
            onSuccess: (updated) => {
              toast.success(t("security.savedToast", { ref: updated.publicRef }));
              onDone();
            },
          },
        );
      }}
    >
      <h3 className="text-caption font-bold text-text-primary">{t("security.editTitle")}</h3>
      <IncidentFields form={form} onChange={setForm} errors={errors} mode="edit" />
      <ReasonField value={reason} onChange={setReason} showError={submitted} />
      {errors.unchanged === true ? (
        <p className="text-small text-text-secondary">{t("security.errors.unchanged")}</p>
      ) : null}
      <MutationErrorNote error={update.error} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="tertiary" size="sm" onClick={onDone}>
          {t("dense.close")}
        </Button>
        <Button type="submit" size="sm" disabled={update.isPending}>
          <Save size={14} aria-hidden="true" />
          {t("security.save")}
        </Button>
      </div>
    </form>
  );
}

// ------------------------------------------------------------------- mở hồ sơ

function OpenIncidentDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation("admin");
  const open = useOpenSecurityIncident();
  const [form, setForm] = useState<IncidentForm>(emptyIncidentForm);
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const result = buildOpenIncidentPayload(form, reason);
  const errors: IncidentFormErrors = submitted && !result.ok ? result.errors : {};

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogTitle>{t("security.openIncident")}</DialogTitle>
        <DialogDescription>{t("security.openDescription")}</DialogDescription>
        <form
          className="flex flex-col gap-3 pt-3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            setSubmitted(true);
            if (!result.ok) return;
            open.mutate(result.payload, {
              onSuccess: (created) => {
                toast.success(t("security.openedToast", { ref: created.publicRef }));
                onClose();
              },
            });
          }}
        >
          <IncidentFields form={form} onChange={setForm} errors={errors} mode="create" />
          <ReasonField value={reason} onChange={setReason} showError={submitted} />
          <MutationErrorNote error={open.error} />
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="tertiary" size="sm" onClick={onClose}>
              {t("actions.cancel")}
            </Button>
            <Button type="submit" size="sm" disabled={open.isPending}>
              <FilePlus2 size={14} aria-hidden="true" />
              {t("security.openSubmit")}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
