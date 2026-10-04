import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  ApiErrorNote,
  MissingApiNotice,
  ReasonField,
  isReasonValid,
  useUpdateAdminMaintenance,
} from "@/features/admin";

/**
 * `/admin/system/broadcast` — Maintenance đã nối app_setting thật; broadcast L72 còn chờ
 * contract outbox thông báo hệ thống.
 */
export function AdminBroadcastPage() {
  const { t } = useTranslation("admin");
  const [active, setActive] = useState(false);
  const [until, setUntil] = useState("");
  const [reason, setReason] = useState("");
  const update = useUpdateAdminMaintenance();
  const canSave = isReasonValid(reason);
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader title={t("pages.broadcast.title")} description={t("pages.broadcast.description")} specRef="L71–L72 · /api/v1/admin" />
      <AdminSection title={t("broadcast.maintenanceTitle")} description={t("broadcast.maintenanceDescription")}>
        <label className="flex items-center gap-3 text-body text-text-primary">
          <input type="checkbox" checked={active} onChange={(event) => { setActive(event.target.checked); }} />
          {t("broadcast.maintenanceActive")}
        </label>
        <Input label={t("broadcast.until")} value={until} onChange={(event) => { setUntil(event.target.value); }} placeholder="2026-10-04T18:00:00+07:00" />
        <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
        <div className="flex justify-end"><Button disabled={!canSave || update.isPending} onClick={() => { update.mutate({ active, until: until.trim() || null, reason: reason.trim() }); }}>{t("broadcast.saveMaintenance")}</Button></div>
        <ApiErrorNote error={update.error} />
      </AdminSection>
      <MissingApiNotice
        specSection="p8 §8.4.12 (f)"
        endpoints={[
          { code: "L72", signature: "POST /admin/system/broadcast" },
        ]}
        note={t("broadcast.broadcastNote")}
      />
    </div>
  );
}
