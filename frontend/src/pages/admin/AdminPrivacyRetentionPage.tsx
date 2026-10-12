import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FlaskConical, Pencil } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  EMPTY_CELL,
  MutationErrorNote,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  useAdminConsentPurposes,
  useAdminDataInventory,
  useAdminRetentionPolicies,
  useDryRunAdminRetentionPolicy,
  useUpdateAdminRetentionPolicy,
} from "@/features/admin";
import type { AdminRetentionPolicy, RetentionAction } from "@/features/admin";
import { useGroup1Can } from "./group1Access";

const RETENTION_ACTIONS: RetentionAction[] = ["HARD_DELETE", "ANONYMIZE", "ARCHIVE", "MASK"];

function toDraft(policy: AdminRetentionPolicy) {
  return {
    dataInventoryCode: policy.dataInventoryCode ?? "",
    targetTable: policy.targetTable,
    retentionDays: policy.retentionDays == null ? "" : String(policy.retentionDays),
    anchorColumn: policy.anchorColumn,
    actionOnExpiry: policy.actionOnExpiry,
    jobName: policy.jobName ?? "",
    safetyThresholdPercent: String(policy.safetyThresholdPercent),
    enabled: policy.enabled,
    legalBasis: policy.legalBasis ?? "",
  };
}

export function AdminPrivacyRetentionPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  const canEdit = can("retentionWrite");
  const canDryRun = can("retention");
  const inventory = useAdminDataInventory();
  const purposes = useAdminConsentPurposes();
  const policies = useAdminRetentionPolicies();
  const update = useUpdateAdminRetentionPolicy();
  const dryRun = useDryRunAdminRetentionPolicy();
  const [editingCode, setEditingCode] = useState<string | null>(null);
  const [draft, setDraft] = useState<ReturnType<typeof toDraft> | null>(null);

  const inventoryItems = inventory.data?.items ?? [];
  const purposeItems = purposes.data?.items ?? [];
  const policyItems = policies.data ?? [];

  function startEdit(policy: AdminRetentionPolicy) {
    setEditingCode(policy.code);
    setDraft(toDraft(policy));
    update.reset();
  }

  function submitEdit() {
    if (
      !canEdit ||
      editingCode === null ||
      draft === null ||
      draft.targetTable.trim() === "" ||
      draft.anchorColumn.trim() === ""
    )
      return;
    update.mutate(
      {
        code: editingCode,
        payload: {
          dataInventoryCode: draft.dataInventoryCode.trim() || null,
          targetTable: draft.targetTable.trim(),
          retentionDays: draft.retentionDays.trim() === "" ? null : Number(draft.retentionDays),
          anchorColumn: draft.anchorColumn.trim(),
          actionOnExpiry: draft.actionOnExpiry,
          jobName: draft.jobName.trim() || null,
          safetyThresholdPercent: Number(draft.safetyThresholdPercent),
          enabled: draft.enabled,
          legalBasis: draft.legalBasis.trim() || null,
        },
      },
      {
        onSuccess: () => {
          setEditingCode(null);
          setDraft(null);
        },
      },
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader
        title={t("pages.privacyRetention.title")}
        description={t("pages.privacyRetention.description")}
        specRef="GET/PATCH /api/v1/admin/privacy/retention-policies"
      />

      <DensePanel title={t("retention.inventoryTitle")} description={t("retention.inventoryDescription")}>
        {inventory.isPending ? (
          <TableSkeleton rows={4} />
        ) : inventory.isError ? (
          <AdminQueryError
            error={inventory.error}
            onRetry={() => {
              void inventory.refetch();
            }}
          />
        ) : inventoryItems.length === 0 ? (
          <Empty label={t("feedback.empty")} />
        ) : (
          <DenseTableFrame label={t("retention.inventoryTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colCode")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colCategory")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colSensitivity")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colLegalBasis")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colPolicy")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colStorage")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colCrossBorder")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colRecipient")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {inventoryItems.map((item) => (
                  <tr key={item.code} className={denseRowClass}>
                    <td className={denseTdClass}>
                      <span className="font-mono text-small font-bold">{item.code}</span>
                      <span className="block max-w-[32ch] pt-0.5 text-small text-text-tertiary">
                        {item.description}
                      </span>
                    </td>
                    <td className={denseTdClass}>{item.category}</td>
                    <td className={denseTdClass}>{item.sensitivity}</td>
                    <td className={denseTdClass}>{item.legalBasis}</td>
                    <td className={`${denseTdClass} font-mono text-small`}>{item.retentionPolicyCode ?? EMPTY_CELL}</td>
                    <td className={denseTdClass}>{item.storageLocation ?? EMPTY_CELL}</td>
                    <td className={denseTdClass}>
                      {item.crossBorder ? t("retention.crossBorderYes") : t("retention.crossBorderNo")}
                    </td>
                    <td className={denseTdClass}>{item.recipient ?? EMPTY_CELL}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
        )}
      </DensePanel>

      <DensePanel title={t("retention.purposesTitle")} description={t("retention.purposesDescription")}>
        {purposes.isPending ? (
          <TableSkeleton rows={4} />
        ) : purposes.isError ? (
          <AdminQueryError
            error={purposes.error}
            onRetry={() => {
              void purposes.refetch();
            }}
          />
        ) : purposeItems.length === 0 ? (
          <Empty label={t("feedback.empty")} />
        ) : (
          <DenseTableFrame label={t("retention.purposesTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colCode")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colPurposeLabel")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colMandatory")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colSensitive")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colWithdrawEffect")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colPhase")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {purposeItems.map((purpose) => (
                  <tr key={purpose.code} className={denseRowClass}>
                    <td className={denseTdClass}>
                      <span className="font-mono text-small font-bold">{purpose.code}</span>
                    </td>
                    <td className={denseTdClass}>
                      <span className="font-semibold">{purpose.label}</span>
                      <span className="block max-w-[48ch] pt-0.5 text-small text-text-tertiary">
                        {purpose.description}
                      </span>
                    </td>
                    <td className={denseTdClass}>
                      {purpose.mandatory ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={denseTdClass}>
                      {purpose.sensitive ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={denseTdClass}>{purpose.withdrawEffect ?? EMPTY_CELL}</td>
                    <td className={`${denseTdClass} font-mono text-small`}>{purpose.phase}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
        )}
      </DensePanel>
      <DensePanel title={t("retention.policiesTitle")} description={t("retention.policiesDescription")}>
        {policies.isPending ? (
          <TableSkeleton rows={4} />
        ) : policies.isError ? (
          <AdminQueryError
            error={policies.error}
            onRetry={() => {
              void policies.refetch();
            }}
          />
        ) : policyItems.length === 0 ? (
          <Empty label={t("feedback.empty")} />
        ) : (
          <DenseTableFrame label={t("retention.policiesTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colCode")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colTarget")}
                  </th>
                  <th scope="col" className={`${denseThClass} text-right`}>
                    {t("retention.colDays")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colAction")}
                  </th>
                  <th scope="col" className={`${denseThClass} text-right`}>
                    {t("retention.colThreshold")}
                  </th>
                  <th scope="col" className={denseThClass}>
                    {t("retention.colEnabled")}
                  </th>
                  {canEdit || canDryRun ? (
                    <th scope="col" className={denseThClass}>
                      {t("retention.action")}
                    </th>
                  ) : null}
                </tr>
              </thead>
              <tbody>
                {policyItems.map((policy) => (
                  <tr key={policy.code} className={denseRowClass} data-selected={policy.code === editingCode}>
                    <td className={denseTdClass}>
                      <span className="font-mono text-small font-bold">{policy.code}</span>
                    </td>
                    <td className={denseTdClass}>
                      <span className="font-mono text-small">
                        {policy.targetTable}.{policy.anchorColumn}
                      </span>
                    </td>
                    <td className={`${denseTdClass} text-right font-mono tabular-nums`}>
                      {policy.retentionDays ?? t("retention.accountLifetime")}
                    </td>
                    <td className={`${denseTdClass} font-mono text-small`}>{policy.actionOnExpiry}</td>
                    <td className={`${denseTdClass} text-right font-mono tabular-nums`}>
                      {policy.safetyThresholdPercent}%
                    </td>
                    <td className={denseTdClass}>{policy.enabled ? t("retention.flagYes") : t("retention.flagNo")}</td>
                    {canEdit || canDryRun ? (
                      <td className={denseTdClass}>
                        <div className="flex flex-wrap gap-2">
                          {canEdit ? (
                            <Button
                              type="button"
                              variant="tertiary"
                              size="sm"
                              leftIcon={<Pencil size={14} />}
                              onClick={() => {
                                startEdit(policy);
                              }}
                            >
                              {t("retention.edit")}
                            </Button>
                          ) : null}
                          {canDryRun ? (
                            <Button
                              type="button"
                              variant="tertiary"
                              size="sm"
                              leftIcon={<FlaskConical size={14} />}
                              disabled={dryRun.isPending}
                              onClick={() => {
                                dryRun.mutate(policy.code);
                              }}
                            >
                              {t("actions.dryRun")}
                            </Button>
                          ) : null}
                        </div>
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
        )}
        {canEdit && draft !== null ? (
          <div className="grid gap-3 rounded-lg border border-border bg-background-alt p-3 md:grid-cols-2">
            <p className="text-caption font-semibold md:col-span-2">
              {t("retention.editTitle", { code: editingCode })}
            </p>
            <Input
              label={t("retention.colTarget")}
              value={draft.targetTable}
              className="h-10 font-mono"
              onChange={(event) => {
                setDraft({ ...draft, targetTable: event.target.value });
              }}
            />
            <Input
              label={t("retention.anchor")}
              value={draft.anchorColumn}
              className="h-10 font-mono"
              onChange={(event) => {
                setDraft({ ...draft, anchorColumn: event.target.value });
              }}
            />
            <Input
              label={t("retention.colDays")}
              type="number"
              min={1}
              value={draft.retentionDays}
              className="h-10"
              onChange={(event) => {
                setDraft({ ...draft, retentionDays: event.target.value });
              }}
            />
            <AdminSelect
              label={t("retention.colAction")}
              value={draft.actionOnExpiry}
              className="h-10"
              onChange={(event) => {
                setDraft({ ...draft, actionOnExpiry: event.target.value as RetentionAction });
              }}
            >
              {RETENTION_ACTIONS.map((action) => (
                <option key={action} value={action}>
                  {action}
                </option>
              ))}
            </AdminSelect>
            <Input
              label={t("retention.colThreshold")}
              type="number"
              min={1}
              max={100}
              value={draft.safetyThresholdPercent}
              className="h-10"
              onChange={(event) => {
                setDraft({ ...draft, safetyThresholdPercent: event.target.value });
              }}
            />
            <label className="flex items-center gap-2 text-caption md:pt-6">
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(event) => {
                  setDraft({ ...draft, enabled: event.target.checked });
                }}
              />
              {t("retention.enabled")}
            </label>
            <div className="flex gap-2 md:col-span-2">
              <Button type="button" size="sm" onClick={submitEdit} loading={update.isPending}>
                {t("retention.save")}
              </Button>
              <Button
                type="button"
                variant="tertiary"
                size="sm"
                onClick={() => {
                  setEditingCode(null);
                  setDraft(null);
                }}
              >
                {t("retention.cancel")}
              </Button>
            </div>
            <div className="md:col-span-2">
              <MutationErrorNote error={update.error} />
            </div>
          </div>
        ) : null}
        {dryRun.data ? (
          <div role="status" className="rounded-lg border border-border bg-background-alt px-3 py-2 text-caption">
            <p className="font-semibold">{t("actions.dryRunResult", { code: dryRun.data.policyCode })}</p>
            <p className="pt-0.5 text-text-secondary">
              {t("actions.dryRunCount", {
                count: dryRun.data.candidateCount,
                table: dryRun.data.targetTable,
                action: dryRun.data.actionOnExpiry,
              })}
            </p>
          </div>
        ) : null}
        <MutationErrorNote error={dryRun.error} />
      </DensePanel>
    </div>
  );
}

function Empty({ label }: { label: string }) {
  return <p className="py-6 text-center text-caption text-text-secondary">{label}</p>;
}
