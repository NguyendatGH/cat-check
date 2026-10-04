import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminTableScroll,
  ApiErrorNote,
  ReasonField,
  SavedNote,
  adminTableClass,
  adminTdClass,
  adminThClass,
  isReasonValid,
  useAdminPackagePlans,
  useUpdateAdminPackagePlan,
} from "@/features/admin";
import type { AdminPackagePlan, AdminPlanFeatures } from "@/features/admin";

interface PlanDraft {
  creditAmount: string;
  creditValidityDays: string;
  maxCatProfiles: string;
  unlimitedCats: boolean;
  features: AdminPlanFeatures;
  active: boolean;
}

function toDraft(plan: AdminPackagePlan): PlanDraft {
  return {
    creditAmount: String(plan.creditAmount),
    creditValidityDays: String(plan.creditValidityDays),
    maxCatProfiles: plan.maxCatProfiles === null ? "" : String(plan.maxCatProfiles),
    unlimitedCats: plan.maxCatProfiles === null,
    features: { ...plan.features },
    active: plan.active,
  };
}

export function AdminPackagesPage() {
  const { t } = useTranslation("admin");
  const plans = useAdminPackagePlans(true);
  const update = useUpdateAdminPackagePlan();
  const [editingCode, setEditingCode] = useState<string | null>(null);
  const [draft, setDraft] = useState<PlanDraft | null>(null);
  const [reason, setReason] = useState("");
  const [saved, setSaved] = useState(false);
  const canSave = draft !== null && isReasonValid(reason);

  function startEdit(plan: AdminPackagePlan) {
    setEditingCode(plan.code);
    setDraft(toDraft(plan));
    setReason("");
    setSaved(false);
    update.reset();
  }

  function updateFeature<K extends keyof AdminPlanFeatures>(key: K, value: AdminPlanFeatures[K]) {
    setDraft((current) =>
      current === null ? current : { ...current, features: { ...current.features, [key]: value } },
    );
  }

  function submit(plan: AdminPackagePlan) {
    if (!draft || !canSave) return;
    update.mutate(
      {
        code: plan.code,
        etag: plan.etag,
        payload: {
          creditAmount: Number(draft.creditAmount),
          creditValidityDays: Number(draft.creditValidityDays),
          ...(draft.unlimitedCats ? { clearMaxCatProfiles: true } : { maxCatProfiles: Number(draft.maxCatProfiles) }),
          features: draft.features,
          active: draft.active,
          reason: reason.trim(),
        },
      },
      {
        onSuccess: () => {
          setSaved(true);
          setEditingCode(null);
          setDraft(null);
        },
      },
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.packages.title")}
        description={t("pages.packages.description")}
        specRef="L25–L26 · GET/PATCH /api/v1/admin/package-plans"
      />

      <AdminSection title={t("packageAdmin.listTitle")} description={t("packageAdmin.listDescription")}>
        {plans.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {plans.isError ? <ApiErrorNote error={plans.error} /> : null}
        {plans.data && plans.data.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {plans.data && plans.data.length > 0 ? (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("packageAdmin.code")}</th>
                  <th className={adminThClass}>{t("packageAdmin.name")}</th>
                  <th className={adminThClass}>{t("packageAdmin.credit")}</th>
                  <th className={adminThClass}>{t("packageAdmin.validity")}</th>
                  <th className={adminThClass}>{t("packageAdmin.cats")}</th>
                  <th className={adminThClass}>{t("packageAdmin.status")}</th>
                  <th className={adminThClass}>{t("packageAdmin.action")}</th>
                </tr>
              </thead>
              <tbody>
                {plans.data.map((plan) => (
                  <tr key={plan.code}>
                    <td className={adminTdClass}>
                      <code>{plan.code}</code>
                      <span className="block text-small text-text-tertiary">
                        {t("packageAdmin.version", { version: plan.version })}
                      </span>
                    </td>
                    <td className={adminTdClass}>{plan.name}</td>
                    <td className={adminTdClass}>{plan.creditAmount}</td>
                    <td className={adminTdClass}>
                      {plan.creditValidityDays} {t("packageAdmin.days")}
                    </td>
                    <td className={adminTdClass}>{plan.maxCatProfiles ?? t("packageAdmin.unlimited")}</td>
                    <td className={adminTdClass}>
                      {plan.active ? t("packageAdmin.active") : t("packageAdmin.inactive")}
                    </td>
                    <td className={adminTdClass}>
                      <Button
                        variant="tertiary"
                        size="sm"
                        onClick={() => {
                          startEdit(plan);
                        }}
                      >
                        {t("actions.edit")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        ) : null}
      </AdminSection>

      {editingCode !== null && draft !== null
        ? (() => {
            const plan = plans.data?.find((item) => item.code === editingCode);
            if (!plan) return null;
            return (
              <AdminSection
                title={t("packageAdmin.editTitle", { code: plan.code })}
                description={t("packageAdmin.editDescription")}
              >
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                  <Input
                    label={t("packageAdmin.credit")}
                    type="number"
                    min={1}
                    value={draft.creditAmount}
                    onChange={(event) => {
                      setDraft({ ...draft, creditAmount: event.target.value });
                    }}
                  />
                  <Input
                    label={t("packageAdmin.validity")}
                    type="number"
                    min={1}
                    value={draft.creditValidityDays}
                    onChange={(event) => {
                      setDraft({ ...draft, creditValidityDays: event.target.value });
                    }}
                  />
                  <Input
                    label={t("packageAdmin.cats")}
                    type="number"
                    min={1}
                    disabled={draft.unlimitedCats}
                    value={draft.maxCatProfiles}
                    onChange={(event) => {
                      setDraft({ ...draft, maxCatProfiles: event.target.value });
                    }}
                  />
                  <label className="flex items-center gap-2 self-end pb-3 text-caption font-semibold text-text-secondary">
                    <input
                      type="checkbox"
                      checked={draft.unlimitedCats}
                      onChange={(event) => {
                        setDraft({ ...draft, unlimitedCats: event.target.checked });
                      }}
                    />
                    {t("packageAdmin.unlimited")}
                  </label>
                </div>
                <div className="grid gap-3 rounded-xl bg-background-alt p-4 md:grid-cols-2 lg:grid-cols-5">
                  <label className="flex items-center gap-2 text-caption">
                    <input
                      type="checkbox"
                      checked={draft.features.trend}
                      onChange={(event) => {
                        updateFeature("trend", event.target.checked);
                      }}
                    />
                    {t("packageAdmin.trend")}
                  </label>
                  <label className="flex items-center gap-2 text-caption">
                    <input
                      type="checkbox"
                      checked={draft.features.reminder}
                      onChange={(event) => {
                        updateFeature("reminder", event.target.checked);
                      }}
                    />
                    {t("packageAdmin.reminder")}
                  </label>
                  <label className="flex items-center gap-2 text-caption">
                    <input
                      type="checkbox"
                      checked={draft.features.export}
                      onChange={(event) => {
                        updateFeature("export", event.target.checked);
                      }}
                    />
                    {t("packageAdmin.export")}
                  </label>
                  <label className="flex items-center gap-2 text-caption">
                    <input
                      type="checkbox"
                      checked={draft.features.storeImage}
                      onChange={(event) => {
                        updateFeature("storeImage", event.target.checked);
                      }}
                    />
                    {t("packageAdmin.storeImage")}
                  </label>
                  <label className="flex items-center gap-2 text-caption">
                    <input
                      type="checkbox"
                      checked={draft.active}
                      onChange={(event) => {
                        setDraft({ ...draft, active: event.target.checked });
                      }}
                    />
                    {t("packageAdmin.active")}
                  </label>
                </div>
                <ReasonField
                  value={reason}
                  onChange={setReason}
                  showError={reason.length > 0 || update.isError}
                  disabled={update.isPending}
                />
                <ApiErrorNote error={update.error} />
                <SavedNote visible={saved} />
                <div className="flex gap-2">
                  <Button
                    variant="primary"
                    loading={update.isPending}
                    disabled={!canSave}
                    onClick={() => {
                      submit(plan);
                    }}
                  >
                    {t("packageAdmin.save")}
                  </Button>
                  <Button
                    variant="tertiary"
                    onClick={() => {
                      setEditingCode(null);
                      setDraft(null);
                    }}
                  >
                    {t("actions.cancel")}
                  </Button>
                </div>
              </AdminSection>
            );
          })()
        : null}
    </div>
  );
}
