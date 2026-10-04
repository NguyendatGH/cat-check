import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Loader2 } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminTableScroll,
  ApiErrorNote,
  adminTableClass,
  adminTdClass,
  adminThClass,
  useAdminConsentPurposes,
  useAdminDataInventory,
  useAdminRetentionPolicies,
  useDryRunAdminRetentionPolicy,
  useUpdateAdminRetentionPolicy,
} from "@/features/admin";
import type { AdminRetentionPolicy, RetentionAction } from "@/features/admin";

const RETENTION_ACTIONS: RetentionAction[] = ["HARD_DELETE", "ANONYMIZE", "ARCHIVE", "MASK"];

function toDraft(policy: AdminRetentionPolicy) {
  return {
    dataInventoryCode: policy.dataInventoryCode ?? "",
    targetTable: policy.targetTable,
    retentionDays: policy.retentionDays === null ? "" : String(policy.retentionDays),
    anchorColumn: policy.anchorColumn,
    actionOnExpiry: policy.actionOnExpiry,
    jobName: policy.jobName ?? "",
    safetyThresholdPercent: String(policy.safetyThresholdPercent),
    enabled: policy.enabled,
    legalBasis: policy.legalBasis ?? "",
  };
}

/** `/admin/privacy/retention` — kiểm kê dữ liệu và chỉnh retention thật cho DPO. */
export function AdminPrivacyRetentionPage() {
  const { t } = useTranslation("admin");
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
    if (editingCode === null || draft === null || draft.targetTable.trim() === "" || draft.anchorColumn.trim() === "")
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
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.privacyRetention.title")}
        description={t("pages.privacyRetention.description")}
        specRef="GET/PATCH /api/v1/admin/privacy/retention-policies"
      />

      <AdminSection title={t("retention.inventoryTitle")} description={t("retention.inventoryDescription")}>
        {inventory.isPending ? (
          <Loading label={t("feedback.loading")} />
        ) : inventory.isError ? (
          <ApiErrorNote error={inventory.error} />
        ) : inventoryItems.length === 0 ? (
          <Empty label={t("feedback.empty")} />
        ) : (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("retention.colCode")}</th>
                  <th className={adminThClass}>{t("retention.colCategory")}</th>
                  <th className={adminThClass}>{t("retention.colSensitivity")}</th>
                  <th className={adminThClass}>{t("retention.colLegalBasis")}</th>
                  <th className={adminThClass}>{t("retention.colPolicy")}</th>
                  <th className={adminThClass}>{t("retention.colStorage")}</th>
                  <th className={adminThClass}>{t("retention.colCrossBorder")}</th>
                  <th className={adminThClass}>{t("retention.colRecipient")}</th>
                </tr>
              </thead>
              <tbody>
                {inventoryItems.map((item) => (
                  <tr key={item.code}>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small font-bold">{item.code}</span>
                      <span className="block max-w-[32ch] pt-0.5 text-small text-text-tertiary">
                        {item.description}
                      </span>
                    </td>
                    <td className={adminTdClass}>{item.category}</td>
                    <td className={adminTdClass}>{item.sensitivity}</td>
                    <td className={adminTdClass}>{item.legalBasis}</td>
                    <td className={adminTdClass}>{item.retentionPolicyCode ?? "—"}</td>
                    <td className={adminTdClass}>{item.storageLocation ?? "—"}</td>
                    <td className={adminTdClass}>
                      {item.crossBorder ? t("retention.crossBorderYes") : t("retention.crossBorderNo")}
                    </td>
                    <td className={adminTdClass}>{item.recipient ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        )}
      </AdminSection>

      <AdminSection title={t("retention.purposesTitle")} description={t("retention.purposesDescription")}>
        {purposes.isPending ? (
          <Loading label={t("feedback.loading")} />
        ) : purposes.isError ? (
          <ApiErrorNote error={purposes.error} />
        ) : purposeItems.length === 0 ? (
          <Empty label={t("feedback.empty")} />
        ) : (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("retention.colCode")}</th>
                  <th className={adminThClass}>{t("retention.colPurposeLabel")}</th>
                  <th className={adminThClass}>{t("retention.colMandatory")}</th>
                  <th className={adminThClass}>{t("retention.colSensitive")}</th>
                  <th className={adminThClass}>{t("retention.colWithdrawEffect")}</th>
                  <th className={adminThClass}>{t("retention.colPhase")}</th>
                </tr>
              </thead>
              <tbody>
                {purposeItems.map((purpose) => (
                  <tr key={purpose.code}>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small font-bold">{purpose.code}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-semibold">{purpose.label}</span>
                      <span className="block max-w-[48ch] pt-0.5 text-small text-text-tertiary">
                        {purpose.description}
                      </span>
                    </td>
                    <td className={adminTdClass}>
                      {purpose.mandatory ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={adminTdClass}>
                      {purpose.sensitive ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={adminTdClass}>{purpose.withdrawEffect ?? "—"}</td>
                    <td className={adminTdClass}>{purpose.phase}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        )}
      </AdminSection>

      <AdminSection title={t("retention.policiesTitle")} description={t("retention.policiesDescription")}>
        {policies.isPending ? (
          <Loading label={t("feedback.loading")} />
        ) : policies.isError ? (
          <ApiErrorNote error={policies.error} />
        ) : policyItems.length === 0 ? (
          <Empty label={t("feedback.empty")} />
        ) : (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("retention.colCode")}</th>
                  <th className={adminThClass}>{t("retention.colTarget")}</th>
                  <th className={adminThClass}>{t("retention.colDays")}</th>
                  <th className={adminThClass}>{t("retention.colAction")}</th>
                  <th className={adminThClass}>{t("retention.colThreshold")}</th>
                  <th className={adminThClass}>{t("retention.colEnabled")}</th>
                  <th className={adminThClass}>{t("retention.action")}</th>
                </tr>
              </thead>
              <tbody>
                {policyItems.map((policy) => (
                  <tr key={policy.code}>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small font-bold">{policy.code}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">
                        {policy.targetTable}.{policy.anchorColumn}
                      </span>
                    </td>
                    <td className={adminTdClass}>{policy.retentionDays ?? t("retention.accountLifetime")}</td>
                    <td className={adminTdClass}>{policy.actionOnExpiry}</td>
                    <td className={adminTdClass}>{policy.safetyThresholdPercent}%</td>
                    <td className={adminTdClass}>{policy.enabled ? t("retention.flagYes") : t("retention.flagNo")}</td>
                    <td className={adminTdClass}>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => {
                          startEdit(policy);
                        }}
                      >
                        {t("retention.edit")}
                      </Button>
                      <Button
                        type="button"
                        variant="tertiary"
                        size="sm"
                        className="ml-2"
                        disabled={dryRun.isPending}
                        onClick={() => { dryRun.mutate(policy.code); }}
                      >
                        {t("retention.dryRun")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        )}
        {draft !== null ? (
          <div className="mt-4 grid gap-3 rounded-lg border border-border-subtle bg-background-alt p-4 md:grid-cols-2">
            <p className="md:col-span-2 text-small font-semibold">{t("retention.editTitle", { code: editingCode })}</p>
            <label className="flex flex-col gap-1 text-small">
              <span>{t("retention.colTarget")}</span>
              <Input
                value={draft.targetTable}
                onChange={(event) => {
                  setDraft({ ...draft, targetTable: event.target.value });
                }}
              />
            </label>
            <label className="flex flex-col gap-1 text-small">
              <span>{t("retention.anchor")}</span>
              <Input
                value={draft.anchorColumn}
                onChange={(event) => {
                  setDraft({ ...draft, anchorColumn: event.target.value });
                }}
              />
            </label>
            <label className="flex flex-col gap-1 text-small">
              <span>{t("retention.colDays")}</span>
              <Input
                type="number"
                min={1}
                value={draft.retentionDays}
                onChange={(event) => {
                  setDraft({ ...draft, retentionDays: event.target.value });
                }}
              />
            </label>
            <label className="flex flex-col gap-1 text-small">
              <span>{t("retention.colAction")}</span>
              <select
                className="h-10 rounded-md border border-border-subtle bg-background px-3 text-small"
                value={draft.actionOnExpiry}
                onChange={(event) => {
                  setDraft({ ...draft, actionOnExpiry: event.target.value as RetentionAction });
                }}
              >
                {RETENTION_ACTIONS.map((action) => (
                  <option key={action} value={action}>
                    {action}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-small">
              <span>{t("retention.colThreshold")}</span>
              <Input
                type="number"
                min={1}
                max={100}
                value={draft.safetyThresholdPercent}
                onChange={(event) => {
                  setDraft({ ...draft, safetyThresholdPercent: event.target.value });
                }}
              />
            </label>
            <label className="flex items-center gap-2 pt-6 text-small">
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
              <Button type="button" onClick={submitEdit} disabled={update.isPending}>
                {update.isPending ? t("feedback.saving") : t("retention.save")}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setEditingCode(null);
                  setDraft(null);
                }}
              >
                {t("retention.cancel")}
              </Button>
            </div>
            {update.isError ? (
              <div className="md:col-span-2">
                <ApiErrorNote error={update.error} />
              </div>
            ) : null}
          </div>
        ) : null}
        {dryRun.data ? (
          <div className="rounded-xl border border-border bg-background-alt p-4 text-caption">
            <p className="font-semibold">{t("retention.dryRunResult", { code: dryRun.data.policyCode })}</p>
            <p className="pt-1 text-text-secondary">
              {t("retention.dryRunCount", { count: dryRun.data.candidateCount, table: dryRun.data.targetTable, action: dryRun.data.actionOnExpiry })}
            </p>
          </div>
        ) : null}
        {dryRun.isError ? <ApiErrorNote error={dryRun.error} /> : null}
      </AdminSection>
    </div>
  );
}

function Loading({ label }: { label: string }) {
  return (
    <p className="flex items-center gap-2 text-caption text-text-secondary">
      <Loader2 size={15} className="animate-spin" aria-hidden="true" />
      {label}
    </p>
  );
}
function Empty({ label }: { label: string }) {
  return <p className="text-caption text-text-secondary">{label}</p>;
}
