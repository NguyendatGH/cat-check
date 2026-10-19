import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { BellOff, BellRing, Eye, Lock, Pencil, Save } from "lucide-react";
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
  BulkActionBar,
  DenseTableFrame,
  DensePanel,
  DetailGrid,
  DistributionBar,
  KpiGrid,
  KpiSkeleton,
  KpiTile,
  MutationErrorNote,
  ReasonField,
  SearchField,
  SelectCheckbox,
  SortableTh,
  TableSkeleton,
  TextAreaField,
  ZERO_COOLDOWN_RULE,
  buildRulePatch,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isReasonValid,
  matchesQuery,
  nextSort,
  planRuleToggle,
  pruneSelection,
  ruleToDraft,
  runSequential,
  selectionState,
  sortRows,
  summarizeBulk,
  toggleAllVisible,
  toggleSelection,
  useAdminCan,
  useAdminMonitoringRules,
  useUpdateMonitoringRule,
  type AdminMonitoringRule,
  type BulkOutcome,
  type MonitoringRuleSeverity,
  type RuleDraft,
  type RulePatchErrors,
  type SortState,
} from "@/features/admin";

const SEVERITIES: readonly MonitoringRuleSeverity[] = ["INFO", "ATTENTION", "URGENT"];

type RuleSortKey = "sortOrder" | "code" | "severity" | "cooldownHours" | "updatedAt";

function severityTone(severity: MonitoringRuleSeverity): "info" | "warning" | "danger" {
  if (severity === "URGENT") return "danger";
  if (severity === "ATTENTION") return "warning";
  return "info";
}

function ruleSortValue(rule: AdminMonitoringRule, key: RuleSortKey): string | number | null | undefined {
  switch (key) {
    case "sortOrder":
      return rule.sortOrder;
    case "code":
      return rule.code;
    case "severity":
      return SEVERITIES.indexOf(rule.severity);
    case "cooldownHours":
      return rule.cooldownHours;
    case "updatedAt":
      return rule.updatedAt === null || rule.updatedAt === undefined ? null : Date.parse(rule.updatedAt);
  }
}

function formatParamValue(value: unknown): string {
  return typeof value === "string" || typeof value === "number" || typeof value === "boolean"
    ? String(value)
    : JSON.stringify(value);
}

export function AdminMonitoringRulesPage() {
  const { t } = useTranslation("admin");
  const can = useAdminCan();
  const canRead = can("monitoringRulesRead");
  const canWrite = can("monitoringRulesWrite");
  const rules = useAdminMonitoringRules();
  const [query, setQuery] = useState("");
  const [severityFilter, setSeverityFilter] = useState<MonitoringRuleSeverity | "">("");
  const [enabledFilter, setEnabledFilter] = useState<"" | "on" | "off">("");
  const [sort, setSort] = useState<SortState<RuleSortKey> | null>({ key: "sortOrder", direction: "asc" });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [editing, setEditing] = useState<string | null>(null);
  const [bulkEnable, setBulkEnable] = useState<boolean | null>(null);

  const items = useMemo(() => rules.data ?? [], [rules.data]);
  const visible = useMemo(
    () =>
      sortRows(
        items.filter(
          (rule) =>
            (severityFilter === "" || rule.severity === severityFilter) &&
            (enabledFilter === "" || rule.enabled === (enabledFilter === "on")) &&
            matchesQuery([rule.code, rule.name, rule.messageKey], query),
        ),
        sort,
        ruleSortValue,
      ),
    [items, severityFilter, enabledFilter, query, sort],
  );
  const visibleIds = visible.map((rule) => rule.code);

  useEffect(() => {
    setSelected((current) =>
      pruneSelection(
        current,
        visible.map((rule) => rule.code),
      ),
    );
  }, [visible]);

  const header = (
    <AdminPageHeader title={t("pages.monitoringRules.title")} description={t("pages.monitoringRules.description")} />
  );

  if (!canRead) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <AccessDeniedState />
      </div>
    );
  }

  const onSort = (key: RuleSortKey) => {
    setSort((current) => nextSort(current, key));
  };
  const editingRule = editing === null ? undefined : items.find((rule) => rule.code === editing);

  return (
    <div className="flex flex-col gap-4">
      {header}

      {!canWrite ? (
        <p className="flex items-center gap-2 rounded-lg bg-background-alt px-3 py-2 text-caption text-text-secondary">
          <Lock size={14} aria-hidden="true" />
          {t("rules.readOnly")}
        </p>
      ) : null}

      {rules.isPending ? (
        <>
          <KpiSkeleton />
          <TableSkeleton rows={5} />
        </>
      ) : rules.isError ? (
        <AdminQueryError
          error={rules.error}
          onRetry={() => {
            void rules.refetch();
          }}
        />
      ) : items.length === 0 ? (
        <EmptyState icon={<BellOff size={22} />} title={t("rules.emptyTitle")} />
      ) : (
        <>
          <KpiGrid label={t("dense.kpiLabel")}>
            <KpiTile label={t("rules.kpiTotal")} value={formatAdminNumber(items.length)} tone="info" />
            <KpiTile
              label={t("rules.kpiEnabled")}
              value={formatAdminNumber(items.filter((rule) => rule.enabled).length)}
              tone="success"
            />
            <KpiTile
              label={t("rules.kpiPush")}
              value={formatAdminNumber(items.filter((rule) => rule.pushEnabled).length)}
            />
            <KpiTile
              label={t("rules.kpiDisabled")}
              value={formatAdminNumber(items.filter((rule) => !rule.enabled).length)}
              tone={items.some((rule) => !rule.enabled) ? "warning" : "neutral"}
            />
          </KpiGrid>

          <DensePanel title={t("rules.listTitle")} description={t("rules.listDescription")}>
            <DistributionBar
              label={t("rules.severityDistribution")}
              segments={SEVERITIES.map((severity) => ({
                key: severity,
                label: t(`rules.severities.${severity}`),
                value: items.filter((rule) => rule.severity === severity).length,
                tone: severityTone(severity),
              }))}
            />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
              <div className="lg:col-span-6">
                <SearchField label={t("dense.search")} value={query} onChange={setQuery} />
              </div>
              <div className="lg:col-span-3">
                <AdminSelect
                  label={t("rules.colSeverity")}
                  value={severityFilter}
                  className="h-10"
                  onChange={(event) => {
                    setSeverityFilter(event.target.value as MonitoringRuleSeverity | "");
                  }}
                >
                  <option value="">{t("dense.all")}</option>
                  {SEVERITIES.map((severity) => (
                    <option key={severity} value={severity}>
                      {t(`rules.severities.${severity}`)}
                    </option>
                  ))}
                </AdminSelect>
              </div>
              <div className="lg:col-span-3">
                <AdminSelect
                  label={t("rules.colEnabled")}
                  value={enabledFilter}
                  className="h-10"
                  onChange={(event) => {
                    setEnabledFilter(event.target.value as "" | "on" | "off");
                  }}
                >
                  <option value="">{t("dense.all")}</option>
                  <option value="on">{t("rules.on")}</option>
                  <option value="off">{t("rules.off")}</option>
                </AdminSelect>
              </div>
            </div>

            {canWrite ? (
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
                    setBulkEnable(true);
                  }}
                >
                  <BellRing size={14} aria-hidden="true" />
                  {t("rules.bulkEnable")}
                </Button>
                <Button
                  variant="tertiary"
                  size="sm"
                  onClick={() => {
                    setBulkEnable(false);
                  }}
                >
                  <BellOff size={14} aria-hidden="true" />
                  {t("rules.bulkDisable")}
                </Button>
              </BulkActionBar>
            ) : null}

            {visible.length === 0 ? (
              <p className="py-6 text-center text-caption text-text-secondary">{t("dense.noMatch")}</p>
            ) : (
              <DenseTableFrame label={t("rules.listTitle")}>
                <table className={denseTableClass}>
                  <thead>
                    <tr>
                      {canWrite ? (
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
                      <SortableTh label={t("rules.colCode")} sortKey="code" sort={sort} onSort={onSort} />
                      <SortableTh label={t("rules.colSeverity")} sortKey="severity" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        {t("rules.colEnabled")}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t("rules.colPush")}
                      </th>
                      <SortableTh label={t("rules.colCooldown")} sortKey="cooldownHours" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        {t("rules.colParams")}
                      </th>
                      <SortableTh label={t("rules.colUpdatedAt")} sortKey="updatedAt" sort={sort} onSort={onSort} />
                      <th scope="col" className={denseThClass}>
                        <span className="sr-only">{t("rules.colActions")}</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((rule) => {
                      const isSelected = selected.has(rule.code);
                      return (
                        <tr key={rule.code} className={denseRowClass} data-selected={isSelected}>
                          {canWrite ? (
                            <td className={denseTdClass}>
                              <SelectCheckbox
                                label={t("dense.selectRow", { label: rule.code })}
                                checked={isSelected}
                                onChange={() => {
                                  setSelected((current) => toggleSelection(current, rule.code));
                                }}
                              />
                            </td>
                          ) : null}
                          <td className={denseTdClass}>
                            <code className="block font-mono text-small font-semibold text-text-primary">
                              {rule.code}
                            </code>
                            <span className="block max-w-[240px] truncate text-small text-text-secondary">
                              {rule.name}
                            </span>
                          </td>
                          <td className={denseTdClass}>
                            <Badge tone={severityTone(rule.severity)}>{t(`rules.severities.${rule.severity}`)}</Badge>
                          </td>
                          <td className={denseTdClass}>
                            <Badge tone={rule.enabled ? "success" : "neutral"}>
                              {rule.enabled ? t("rules.on") : t("rules.off")}
                            </Badge>
                          </td>
                          <td className={denseTdClass}>
                            <Badge tone={rule.pushEnabled ? "info" : "neutral"}>
                              {rule.pushEnabled ? t("rules.on") : t("rules.off")}
                            </Badge>
                          </td>
                          <td className={`${denseTdClass} font-mono tabular-nums`}>
                            {t("rules.hours", { count: rule.cooldownHours })}
                          </td>
                          <td className={denseTdClass}>
                            <span className="flex max-w-[360px] flex-wrap gap-1">
                              {Object.entries(rule.params ?? {}).map(([key, value]) => (
                                <code
                                  key={key}
                                  className="rounded bg-chip-bg px-1.5 py-0.5 font-mono text-small text-text-secondary"
                                >
                                  {key}={formatParamValue(value)}
                                </code>
                              ))}
                            </span>
                          </td>
                          <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                            {formatAdminDateTime(rule.updatedAt)}
                          </td>
                          <td className={`${denseTdClass} text-right`}>
                            <Button
                              variant="tertiary"
                              size="sm"
                              className="min-h-8 px-2"
                              onClick={() => {
                                setEditing(rule.code);
                              }}
                            >
                              {canWrite ? (
                                <Pencil size={14} aria-hidden="true" />
                              ) : (
                                <Eye size={14} aria-hidden="true" />
                              )}
                              {canWrite ? t("rules.edit") : t("rules.view")}
                            </Button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </DenseTableFrame>
            )}
          </DensePanel>
        </>
      )}

      {editingRule !== undefined ? (
        <RuleEditorDialog
          key={`${editingRule.code}-${editingRule.etag}`}
          rule={editingRule}
          canWrite={canWrite}
          onReload={async () => {
            await rules.refetch();
          }}
          onClose={() => {
            setEditing(null);
          }}
        />
      ) : null}

      {bulkEnable !== null ? (
        <BulkToggleDialog
          rules={items}
          selected={selected}
          enabled={bulkEnable}
          onReload={async () => {
            await rules.refetch();
          }}
          onClose={(outcome) => {
            setBulkEnable(null);
            if (outcome !== null && outcome.failed.length === 0) setSelected(new Set());
          }}
        />
      ) : null}
    </div>
  );
}

interface RuleEditorDialogProps {
  rule: AdminMonitoringRule;
  canWrite: boolean;
  onReload: () => Promise<void>;
  onClose: () => void;
}

function RuleEditorDialog({ rule, canWrite, onReload, onClose }: RuleEditorDialogProps) {
  const { t } = useTranslation("admin");
  const update = useUpdateMonitoringRule();
  const [draft, setDraft] = useState<RuleDraft>(() => ruleToDraft(rule));
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const result = buildRulePatch(rule, draft, reason);
  const patchErrors: RulePatchErrors = submitted && !result.ok ? result.errors : {};
  const errors = {
    cooldown:
      patchErrors.cooldownHours === "invalid"
        ? t("rules.errors.cooldownInvalid")
        : patchErrors.cooldownHours === "zeroNotAllowed"
          ? t("rules.errors.cooldownZero", { code: ZERO_COOLDOWN_RULE })
          : undefined,
    params:
      patchErrors.params !== undefined ? t("rules.errors.params", { keys: patchErrors.params.join(", ") }) : undefined,
    unchanged: patchErrors.unchanged === true ? t("rules.errors.unchanged") : undefined,
  };

  const setParam = (key: string, value: string) => {
    setDraft((current) => ({
      ...current,
      params: current.params.map((param) => (param.key === key ? { ...param, value } : param)),
    }));
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogTitle>{rule.name}</DialogTitle>
        <DialogDescription className="font-mono">{rule.code}</DialogDescription>
        <div className="flex flex-col gap-4 pt-3">
          <DetailGrid
            items={[
              [t("rules.colSeverity"), t(`rules.severities.${rule.severity}`)],
              [t("rules.messageKey"), <code key="mk">{rule.messageKey}</code>],
              [t("rules.calibrated"), rule.requiresCalibratedChart ? t("dense.yes") : t("dense.no")],
              [t("rules.colUpdatedAt"), formatAdminDateTime(rule.updatedAt)],
            ]}
          />

          {canWrite ? (
            <form
              className="flex flex-col gap-4"
              noValidate
              onSubmit={(event) => {
                event.preventDefault();
                setSubmitted(true);
                if (!result.ok) return;
                update.mutate(
                  { code: rule.code, etag: rule.etag, payload: result.payload },
                  {
                    onSuccess: () => {
                      toast.success(t("rules.savedToast", { code: rule.code }));
                      onClose();
                    },
                  },
                );
              }}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <Switch
                  label={t("rules.enabledLabel")}
                  checked={draft.enabled}
                  onCheckedChange={(checked) => {
                    setDraft((current) => ({ ...current, enabled: checked }));
                  }}
                />
                <Switch
                  label={t("rules.pushLabel")}
                  checked={draft.pushEnabled}
                  onCheckedChange={(checked) => {
                    setDraft((current) => ({ ...current, pushEnabled: checked }));
                  }}
                />
              </div>
              <Input
                label={t("rules.cooldownLabel")}
                inputMode="numeric"
                value={draft.cooldownHours}
                error={errors.cooldown}
                helperText={t("rules.cooldownHelp", { code: ZERO_COOLDOWN_RULE })}
                className="h-10 font-mono"
                onChange={(event) => {
                  setDraft((current) => ({ ...current, cooldownHours: event.target.value }));
                }}
              />

              <fieldset className="flex flex-col gap-3 rounded-lg border border-border p-3">
                <legend className="px-1 text-caption font-semibold text-text-secondary">
                  {t("rules.paramsLabel")}
                </legend>
                <p className="text-small text-text-tertiary">{t("rules.paramsHelp")}</p>
                <div className="grid gap-3 sm:grid-cols-2">
                  {draft.params.map((param) =>
                    param.kind === "boolean" ? (
                      <Switch
                        key={param.key}
                        label={param.key}
                        checked={param.value === "true"}
                        onCheckedChange={(checked) => {
                          setParam(param.key, String(checked));
                        }}
                      />
                    ) : param.kind === "json" ? (
                      <TextAreaField
                        key={param.key}
                        label={param.key}
                        value={param.value}
                        rows={2}
                        className="font-mono text-caption"
                        onChange={(value) => {
                          setParam(param.key, value);
                        }}
                      />
                    ) : (
                      <Input
                        key={param.key}
                        label={param.key}
                        value={param.value}
                        inputMode={param.kind === "number" ? "decimal" : undefined}
                        className="h-10 font-mono"
                        onChange={(event) => {
                          setParam(param.key, event.target.value);
                        }}
                      />
                    ),
                  )}
                </div>
                {errors.params !== undefined ? (
                  <p role="alert" className="text-small text-danger-text">
                    {errors.params}
                  </p>
                ) : null}
              </fieldset>

              <ReasonField value={reason} onChange={setReason} showError={submitted} />
              {errors.unchanged !== undefined ? (
                <p className="text-small text-text-secondary">{errors.unchanged}</p>
              ) : null}
              <MutationErrorNote
                error={update.error}
                onReload={() => {
                  update.reset();
                  void onReload();
                }}
              />
              <div className="flex justify-end gap-2 border-t border-border pt-3">
                <Button type="button" variant="tertiary" size="sm" onClick={onClose}>
                  {t("dense.close")}
                </Button>
                <Button type="submit" size="sm" disabled={update.isPending || !isReasonValid(reason)}>
                  <Save size={14} aria-hidden="true" />
                  {t("rules.save")}
                </Button>
              </div>
            </form>
          ) : (
            <>
              <DetailGrid
                items={[
                  [t("rules.colEnabled"), rule.enabled ? t("rules.on") : t("rules.off")],
                  [t("rules.colPush"), rule.pushEnabled ? t("rules.on") : t("rules.off")],
                  [t("rules.colCooldown"), t("rules.hours", { count: rule.cooldownHours })],
                  ...Object.entries(rule.params ?? {}).map(
                    ([key, value]) =>
                      [
                        key,
                        <code key={key} className="font-mono">
                          {formatParamValue(value)}
                        </code>,
                      ] as const,
                  ),
                ]}
              />
              <div className="flex justify-end border-t border-border pt-3">
                <Button variant="tertiary" size="sm" onClick={onClose}>
                  {t("dense.close")}
                </Button>
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface BulkToggleDialogProps {
  rules: readonly AdminMonitoringRule[];
  selected: ReadonlySet<string>;
  enabled: boolean;
  onReload: () => Promise<void>;
  onClose: (outcome: BulkOutcome | null) => void;
}

function BulkToggleDialog({ rules, selected, enabled, onReload, onClose }: BulkToggleDialogProps) {
  const { t } = useTranslation("admin");
  const update = useUpdateMonitoringRule();
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [running, setRunning] = useState<{ done: number; total: number } | null>(null);
  const [outcome, setOutcome] = useState<BulkOutcome | null>(null);
  const plan = useMemo(() => planRuleToggle(rules, selected, enabled), [rules, selected, enabled]);
  const summary = outcome !== null ? summarizeBulk(outcome) : null;

  const submit = async () => {
    setSubmitted(true);
    if (!isReasonValid(reason) || plan.tasks.length === 0) return;
    setRunning({ done: 0, total: plan.tasks.length });
    const result = await runSequential(
      plan.tasks,
      (task) => task.code,
      (task) => update.mutateAsync({ code: task.code, etag: task.etag, payload: { enabled, reason: reason.trim() } }),
      (done, total) => {
        setRunning({ done, total });
      },
    );
    setRunning(null);
    setOutcome(result);
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && running === null) onClose(outcome);
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogTitle>{enabled ? t("rules.bulkEnable") : t("rules.bulkDisable")}</DialogTitle>
        <DialogDescription>
          {t("rules.bulkPlan", { count: plan.tasks.length, skipped: plan.skipped.length })}
        </DialogDescription>
        <div className="flex flex-col gap-3 pt-3">
          <ul className="flex flex-wrap gap-1">
            {plan.tasks.map((task) => (
              <li key={task.code}>
                <code className="rounded bg-chip-bg px-1.5 py-0.5 font-mono text-small">{task.code}</code>
              </li>
            ))}
          </ul>
          <ReasonField value={reason} onChange={setReason} showError={submitted} disabled={running !== null} />
          {running !== null ? (
            <p aria-live="polite" className="text-caption text-text-secondary">
              {t("dense.bulkProgress", { done: running.done, total: running.total })}
            </p>
          ) : null}
          {summary !== null ? (
            <div aria-live="polite" className="flex flex-col gap-2 rounded-lg bg-background-alt px-3 py-2 text-caption">
              <p>{t("dense.bulkResult", { ok: summary.ok, failed: summary.failed, skipped: plan.skipped.length })}</p>
              {summary.stale > 0 ? (
                <div className="flex flex-wrap items-center justify-between gap-2 text-warning-text">
                  <p>{t("dense.bulkStale", { count: summary.stale })}</p>
                  <Button
                    variant="tertiary"
                    size="sm"
                    onClick={() => {
                      void onReload().then(() => {
                        onClose(outcome);
                      });
                    }}
                  >
                    {t("access.reload")}
                  </Button>
                </div>
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
                disabled={running !== null || plan.tasks.length === 0}
                onClick={() => {
                  void submit();
                }}
              >
                {t("rules.bulkConfirm")}
              </Button>
            ) : null}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
