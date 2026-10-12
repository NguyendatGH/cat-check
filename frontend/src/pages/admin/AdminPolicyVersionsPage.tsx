import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FilePlus2, FileText, Lock, Send } from "lucide-react";
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
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DenseTableFrame,
  DensePanel,
  DistributionBar,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  MonoId,
  MutationErrorNote,
  POLICY_LOCALES,
  POLICY_TYPES,
  Pager,
  ReasonField,
  SearchField,
  SortableTh,
  TableSkeleton,
  TextAreaField,
  classifyAdminError,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  emptyPolicyDraft,
  formatAdminDateTime,
  formatAdminNumber,
  fromLocalInputValue,
  isReasonValid,
  matchesQuery,
  nextSort,
  policyDraftToPayload,
  sortRows,
  toLocalInputValue,
  useAdminCan,
  useAdminConsentPurposes,
  useAdminPolicyVersions,
  useCreatePolicyVersion,
  usePublishPolicyVersion,
  validatePolicyDraft,
  type AdminPolicyVersion,
  type PolicyDraftErrors,
  type PolicyDraftForm,
  type PolicyLocale,
  type PolicyType,
  type PolicyVersionStatus,
  type SortState,
} from "@/features/admin";

const PAGE_SIZE = 50;
const STATUSES: readonly PolicyVersionStatus[] = ["DRAFT", "SCHEDULED", "EFFECTIVE", "SUPERSEDED"];

type PolicySortKey = "policyType" | "version" | "effectiveFrom" | "createdAt" | "status";

function statusTone(status: PolicyVersionStatus): "neutral" | "info" | "success" | "warning" {
  if (status === "EFFECTIVE") return "success";
  if (status === "SCHEDULED") return "info";
  if (status === "DRAFT") return "warning";
  return "neutral";
}

function policySortValue(row: AdminPolicyVersion, key: PolicySortKey): string | number | null | undefined {
  switch (key) {
    case "policyType":
      return row.policyType;
    case "version":
      return row.version;
    case "effectiveFrom":
      return row.effectiveFrom === null || row.effectiveFrom === undefined ? null : Date.parse(row.effectiveFrom);
    case "createdAt":
      return row.createdAt === null || row.createdAt === undefined ? null : Date.parse(row.createdAt);
    case "status":
      return STATUSES.indexOf(row.status);
  }
}

export function AdminPolicyVersionsPage() {
  const { t } = useTranslation("admin");
  const can = useAdminCan();
  const canRead = can("policyVersionsRead");
  const canWrite = can("policyVersionsWrite");
  const [policyType, setPolicyType] = useState<PolicyType | "">("");
  const [locale, setLocale] = useState<PolicyLocale | "">("");
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<PolicySortKey> | null>({ key: "createdAt", direction: "desc" });
  const [creating, setCreating] = useState(false);
  const [publishing, setPublishing] = useState<AdminPolicyVersion | null>(null);

  const list = useAdminPolicyVersions({ policyType, locale, page, size: PAGE_SIZE });
  const items = useMemo(() => list.data?.items ?? [], [list.data]);
  const visible = useMemo(
    () =>
      sortRows(
        items.filter((row) => matchesQuery([row.title, row.version, row.policyType, row.summaryOfChanges], query)),
        sort,
        policySortValue,
      ),
    [items, query, sort],
  );
  const onSort = (key: PolicySortKey) => {
    setSort((current) => nextSort(current, key));
  };

  const header = (
    <AdminPageHeader
      title={t("pages.policyVersions.title")}
      description={t("pages.policyVersions.description")}
      specRef="L46–L48 — /api/v1/admin/policy-versions"
      actions={
        canWrite ? (
          <Button
            size="sm"
            onClick={() => {
              setCreating(true);
            }}
          >
            <FilePlus2 size={14} aria-hidden="true" />
            {t("policies.createCta")}
          </Button>
        ) : undefined
      }
    />
  );

  if (!canRead) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <AccessDeniedState />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {header}
      {!canWrite ? (
        <p className="flex items-center gap-2 rounded-lg bg-background-alt px-3 py-2 text-caption text-text-secondary">
          <Lock size={14} aria-hidden="true" />
          {t("policies.readOnly")}
        </p>
      ) : null}

      {list.isPending ? (
        <KpiSkeleton />
      ) : list.isSuccess ? (
        <KpiGrid label={t("dense.kpiLabel")}>
          <KpiTile label={t("policies.kpiTotal")} value={formatAdminNumber(list.data.totalElements)} tone="info" />
          <KpiTile
            label={t("policies.kpiDraft")}
            value={formatAdminNumber(items.filter((row) => row.status === "DRAFT").length)}
            hint={t("dense.onPageHint")}
            tone="warning"
          />
          <KpiTile
            label={t("policies.kpiEffective")}
            value={formatAdminNumber(items.filter((row) => row.status === "EFFECTIVE").length)}
            hint={t("dense.onPageHint")}
            tone="success"
          />
          <KpiTile
            label={t("policies.kpiReconsent")}
            value={formatAdminNumber(items.filter((row) => row.requiresReconsent === true).length)}
            hint={t("dense.onPageHint")}
          />
        </KpiGrid>
      ) : null}

      <DensePanel title={t("policies.listTitle")}>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
          <div className="lg:col-span-6">
            <SearchField label={t("dense.search")} value={query} onChange={setQuery} />
          </div>
          <div className="lg:col-span-3">
            <AdminSelect
              label={t("policies.colType")}
              value={policyType}
              className="h-10"
              onChange={(event) => {
                setPolicyType(event.target.value as PolicyType | "");
                setPage(0);
              }}
            >
              <option value="">{t("dense.all")}</option>
              {POLICY_TYPES.map((value) => (
                <option key={value} value={value}>
                  {t(`policies.types.${value}`)}
                </option>
              ))}
            </AdminSelect>
          </div>
          <div className="lg:col-span-3">
            <AdminSelect
              label={t("policies.colLocale")}
              value={locale}
              className="h-10"
              onChange={(event) => {
                setLocale(event.target.value as PolicyLocale | "");
                setPage(0);
              }}
            >
              <option value="">{t("dense.all")}</option>
              {POLICY_LOCALES.map((value) => (
                <option key={value} value={value}>
                  {t(`policies.locales.${value}`)}
                </option>
              ))}
            </AdminSelect>
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
          <EmptyState icon={<FileText size={22} />} title={t("policies.emptyTitle")} />
        ) : (
          <>
            <DistributionBar
              label={t("policies.statusDistribution")}
              segments={STATUSES.map((status) => ({
                key: status,
                label: t(`policies.statuses.${status}`),
                value: items.filter((row) => row.status === status).length,
                tone: statusTone(status),
              }))}
            />
            {visible.length === 0 ? (
              <p className="py-6 text-center text-caption text-text-secondary">{t("dense.noMatch")}</p>
            ) : (
              <DenseTableFrame label={t("policies.listTitle")}>
                <table className={denseTableClass}>
                  <thead>
                    <tr>
                      <SortableTh label={t("policies.colType")} sortKey="policyType" sort={sort} onSort={onSort} />
                      <SortableTh label={t("policies.colVersion")} sortKey="version" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        {t("policies.colLocale")}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t("policies.colTitle")}
                      </th>
                      <SortableTh label={t("policies.colStatus")} sortKey="status" sort={sort} onSort={onSort} />
                      <SortableTh
                        label={t("policies.colEffective")}
                        sortKey="effectiveFrom"
                        sort={sort}
                        onSort={onSort}
                      />
                      <th scope="col" className={denseThClass}>
                        {t("policies.colReconsent")}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t("policies.colHash")}
                      </th>
                      <SortableTh label={t("policies.colCreated")} sortKey="createdAt" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        <span className="sr-only">{t("policies.colActions")}</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((row) => (
                      <tr key={row.id} className={denseRowClass}>
                        <td className={denseTdClass}>{t(`policies.types.${row.policyType}`)}</td>
                        <td className={`${denseTdClass} font-mono font-semibold tabular-nums`}>{row.version}</td>
                        <td className={`${denseTdClass} font-mono uppercase`}>{row.locale}</td>
                        <td className={denseTdClass}>
                          <span className="block max-w-[260px] truncate" title={row.title}>
                            {row.title}
                          </span>
                          {row.summaryOfChanges ? (
                            <span className="block max-w-[260px] truncate text-small text-text-tertiary">
                              {row.summaryOfChanges}
                            </span>
                          ) : null}
                        </td>
                        <td className={denseTdClass}>
                          <Badge tone={statusTone(row.status)}>{t(`policies.statuses.${row.status}`)}</Badge>
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                          {formatAdminDateTime(row.effectiveFrom)}
                          {row.effectiveTo ? (
                            <span className="block text-small text-text-tertiary">
                              {t("policies.until", { at: formatAdminDateTime(row.effectiveTo) })}
                            </span>
                          ) : null}
                        </td>
                        <td className={denseTdClass}>
                          {row.requiresReconsent === true ? (
                            <span className="flex flex-col gap-0.5">
                              <Badge tone="warning">{t("policies.reconsentYes")}</Badge>
                              <span className="font-mono text-small text-text-tertiary">
                                {(row.affectedPurposes ?? []).join(", ")}
                              </span>
                            </span>
                          ) : (
                            <span className="text-text-tertiary">{t("dense.no")}</span>
                          )}
                        </td>
                        <td className={denseTdClass}>
                          <MonoId value={row.contentHash} chars={10} />
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                          {formatAdminDateTime(row.createdAt)}
                        </td>
                        <td className={`${denseTdClass} text-right`}>
                          {canWrite && row.status === "DRAFT" ? (
                            <Button
                              variant="tertiary"
                              size="sm"
                              className="min-h-8 px-2"
                              onClick={() => {
                                setPublishing(row);
                              }}
                            >
                              <Send size={14} aria-hidden="true" />
                              {t("policies.publish")}
                            </Button>
                          ) : null}
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

      {creating ? (
        <CreatePolicyDialog
          onClose={() => {
            setCreating(false);
          }}
        />
      ) : null}
      {publishing !== null ? (
        <PublishPolicyDialog
          version={publishing}
          onReload={() => {
            void list.refetch();
          }}
          onClose={() => {
            setPublishing(null);
          }}
        />
      ) : null}
    </div>
  );
}


function CreatePolicyDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation("admin");
  const create = useCreatePolicyVersion();
  const purposes = useAdminConsentPurposes();
  const [form, setForm] = useState<PolicyDraftForm>(emptyPolicyDraft);
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const validation: PolicyDraftErrors = validatePolicyDraft(form, reason, new Date());
  const errors: PolicyDraftErrors = submitted ? validation : {};
  const errorText = (field: keyof PolicyDraftForm): string | undefined => {
    const code = errors[field];
    return code === undefined ? undefined : t(`policies.errors.${code}`);
  };
  const set = <K extends keyof PolicyDraftForm>(key: K, value: PolicyDraftForm[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogTitle>{t("policies.createTitle")}</DialogTitle>
        <DialogDescription>{t("policies.createDescription")}</DialogDescription>
        <form
          className="flex flex-col gap-3 pt-3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            setSubmitted(true);
            if (Object.keys(validation).length > 0) return;
            create.mutate(policyDraftToPayload(form, reason), {
              onSuccess: (created) => {
                toast.success(t("policies.createdToast", { version: created.version }));
                onClose();
              },
            });
          }}
        >
          <div className="grid gap-3 sm:grid-cols-3">
            <AdminSelect
              label={t("policies.colType")}
              value={form.policyType}
              onChange={(event) => {
                set("policyType", event.target.value as PolicyType);
              }}
            >
              {POLICY_TYPES.map((value) => (
                <option key={value} value={value}>
                  {t(`policies.types.${value}`)}
                </option>
              ))}
            </AdminSelect>
            <Input
              label={t("policies.colVersion")}
              value={form.version}
              placeholder="2.1.0"
              error={errorText("version")}
              helperText={t("policies.versionHelp")}
              className="h-10 font-mono"
              onChange={(event) => {
                set("version", event.target.value);
              }}
            />
            <AdminSelect
              label={t("policies.colLocale")}
              value={form.locale}
              onChange={(event) => {
                set("locale", event.target.value as PolicyLocale);
              }}
            >
              {POLICY_LOCALES.map((value) => (
                <option key={value} value={value}>
                  {t(`policies.locales.${value}`)}
                </option>
              ))}
            </AdminSelect>
          </div>
          <Input
            label={t("policies.colTitle")}
            value={form.title}
            maxLength={300}
            error={errorText("title")}
            className="h-10"
            onChange={(event) => {
              set("title", event.target.value);
            }}
          />

          <fieldset className="flex flex-col gap-2">
            <legend className="pb-1 text-caption font-semibold text-text-secondary">{t("policies.source")}</legend>
            <div className="flex flex-wrap gap-4">
              {(["markdown", "url"] as const).map((source) => (
                <label key={source} className="flex min-h-9 items-center gap-2 text-caption text-text-primary">
                  <input
                    type="radio"
                    name="policy-source"
                    value={source}
                    checked={form.source === source}
                    className="accent-primary-dark"
                    onChange={() => {
                      set("source", source);
                    }}
                  />
                  {t(`policies.sources.${source}`)}
                </label>
              ))}
            </div>
          </fieldset>
          {form.source === "markdown" ? (
            <TextAreaField
              label={t("policies.contentMd")}
              value={form.contentMd}
              rows={8}
              className="font-mono text-caption"
              error={errorText("contentMd")}
              onChange={(value) => {
                set("contentMd", value);
              }}
            />
          ) : (
            <Input
              label={t("policies.contentUrl")}
              value={form.contentUrl}
              placeholder="https://"
              error={errorText("contentUrl")}
              className="h-10 font-mono"
              onChange={(event) => {
                set("contentUrl", event.target.value);
              }}
            />
          )}
          <TextAreaField
            label={t("policies.summaryOfChanges")}
            value={form.summaryOfChanges}
            rows={3}
            maxLength={4000}
            error={errorText("summaryOfChanges")}
            helperText={t("policies.summaryHelp")}
            onChange={(value) => {
              set("summaryOfChanges", value);
            }}
          />
          <Input
            type="datetime-local"
            label={t("policies.colEffective")}
            value={form.effectiveFrom}
            error={errorText("effectiveFrom")}
            helperText={t("policies.effectiveHelp")}
            className="h-10 font-mono"
            onChange={(event) => {
              set("effectiveFrom", event.target.value);
            }}
          />
          <Switch
            label={t("policies.requiresReconsent")}
            checked={form.requiresReconsent}
            onCheckedChange={(checked) => {
              set("requiresReconsent", checked);
            }}
          />
          {form.requiresReconsent ? (
            <fieldset className="flex flex-col gap-2 rounded-lg border border-border p-3">
              <legend className="px-1 text-caption font-semibold text-text-secondary">
                {t("policies.affectedPurposes")}
              </legend>
              {purposes.isPending ? (
                <p className="text-small text-text-secondary">{t("feedback.loading")}</p>
              ) : purposes.isError ? (
                <MutationErrorNote error={purposes.error} />
              ) : (
                <div className="grid gap-1 sm:grid-cols-2">
                  {purposes.data.items.map((purpose) => (
                    <label
                      key={purpose.code}
                      className="flex min-h-9 items-center gap-2 text-caption text-text-primary"
                    >
                      <input
                        type="checkbox"
                        className="size-4 accent-primary-dark"
                        checked={form.affectedPurposes.includes(purpose.code)}
                        onChange={(event) => {
                          set(
                            "affectedPurposes",
                            event.target.checked
                              ? [...form.affectedPurposes, purpose.code]
                              : form.affectedPurposes.filter((code) => code !== purpose.code),
                          );
                        }}
                      />
                      <span>
                        {purpose.label}
                        <code className="ml-1.5 font-mono text-small text-text-tertiary">{purpose.code}</code>
                      </span>
                    </label>
                  ))}
                </div>
              )}
              {errors.affectedPurposes !== undefined ? (
                <p role="alert" className="text-small text-danger-text">
                  {t("policies.errors.purposesRequired")}
                </p>
              ) : null}
            </fieldset>
          ) : null}
          <ReasonField value={reason} onChange={setReason} showError={submitted} />
          <MutationErrorNote error={create.error} />
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="tertiary" size="sm" onClick={onClose}>
              {t("actions.cancel")}
            </Button>
            <Button type="submit" size="sm" disabled={create.isPending}>
              <FilePlus2 size={14} aria-hidden="true" />
              {t("policies.createSubmit")}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}


function PublishPolicyDialog({
  version,
  onReload,
  onClose,
}: {
  version: AdminPolicyVersion;
  onReload: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("admin");
  const publish = usePublishPolicyVersion();
  const [effectiveFrom, setEffectiveFrom] = useState(toLocalInputValue(version.effectiveFrom));
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const original = toLocalInputValue(version.effectiveFrom);
  const alreadyPublished = classifyAdminError(publish.error) === "conflict";

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogTitle>{t("policies.publishTitle")}</DialogTitle>
        <DialogDescription>
          {t("policies.publishBody", {
            type: t(`policies.types.${version.policyType}`),
            version: version.version,
            locale: version.locale,
          })}
        </DialogDescription>
        <form
          className="flex flex-col gap-3 pt-3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            setSubmitted(true);
            if (!isReasonValid(reason)) return;
            const override = effectiveFrom !== original ? fromLocalInputValue(effectiveFrom) : null;
            publish.mutate(
              {
                id: version.id,
                payload:
                  override !== null ? { effectiveFrom: override, reason: reason.trim() } : { reason: reason.trim() },
              },
              {
                onSuccess: (published) => {
                  toast.success(t("policies.publishedToast", { version: published.version }));
                  onClose();
                },
              },
            );
          }}
        >
          {version.requiresReconsent === true ? (
            <p role="note" className="rounded-lg bg-warning-bg px-3 py-2 text-caption text-warning-text">
              {t("policies.reconsentWarning", { purposes: (version.affectedPurposes ?? []).join(", ") })}
            </p>
          ) : null}
          <Input
            type="datetime-local"
            label={t("policies.colEffective")}
            value={effectiveFrom}
            helperText={t("policies.publishEffectiveHelp")}
            className="h-10 font-mono"
            onChange={(event) => {
              setEffectiveFrom(event.target.value);
            }}
          />
          <ReasonField value={reason} onChange={setReason} showError={submitted} />
          {alreadyPublished ? (
            <div
              role="alert"
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-warning-bg px-3 py-2 text-caption text-warning-text"
            >
              <p>{t("policies.alreadyPublished")}</p>
              <Button
                type="button"
                variant="tertiary"
                size="sm"
                onClick={() => {
                  onReload();
                  onClose();
                }}
              >
                {t("access.reload")}
              </Button>
            </div>
          ) : (
            <MutationErrorNote error={publish.error} onReload={onReload} />
          )}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="tertiary" size="sm" onClick={onClose}>
              {t("actions.cancel")}
            </Button>
            <Button type="submit" size="sm" disabled={publish.isPending || alreadyPublished}>
              <Send size={14} aria-hidden="true" />
              {t("policies.publishSubmit")}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
