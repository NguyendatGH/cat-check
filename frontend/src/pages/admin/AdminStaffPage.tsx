import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminTableScroll,
  ApiErrorNote,
  ReasonField,
  adminTableClass,
  adminTdClass,
  adminThClass,
  isReasonValid,
  useAdminMfaResetRequests,
  useApproveAdminMfaReset,
  useGrantAdminRole,
  useRequestAdminMfaReset,
  useRevokeAdminRole,
} from "@/features/admin";

/**
 * `/admin/staff` — cấp/gỡ role staff và duyệt reset TOTP theo quy tắc hai người.
 */
export function AdminStaffPage() {
  const { t } = useTranslation("admin");
  const [userId, setUserId] = useState("");
  const [role, setRole] = useState("ADMIN_SUPPORT");
  const [reason, setReason] = useState("");
  const requests = useAdminMfaResetRequests();
  const grant = useGrantAdminRole();
  const revoke = useRevokeAdminRole();
  const requestReset = useRequestAdminMfaReset();
  const approve = useApproveAdminMfaReset();
  const canSubmit = userId.trim().length > 0 && isReasonValid(reason);
  const staffRoles = ["ADMIN_SUPPORT", "ADMIN_CATALOG", "MODERATOR", "VET"];

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader title={t("pages.staff.title")} description={t("pages.staff.description")} specRef="L13–L17 · /api/v1/admin" />
      <AdminSection title={t("staff.roleTitle")} description={t("staff.roleDescription")}>
        <div className="grid gap-3 md:grid-cols-[1fr_220px_auto_auto] md:items-end">
          <Input label={t("staff.userId")} value={userId} onChange={(event) => { setUserId(event.target.value); }} />
          <label className="flex flex-col gap-1 text-caption font-medium text-text-secondary">
            {t("staff.role")}
            <select className="h-11 rounded-xl border border-border bg-surface px-3 text-body text-text-primary" value={role} onChange={(event) => { setRole(event.target.value); }}>
              {staffRoles.map((item) => <option key={item}>{item}</option>)}
            </select>
          </label>
          <Button variant="secondary" disabled={!canSubmit || grant.isPending} onClick={() => { grant.mutate({ userId: userId.trim(), role, reason: reason.trim() }); }}>{t("staff.grant")}</Button>
          <Button variant="tertiary" disabled={!canSubmit || revoke.isPending} onClick={() => { revoke.mutate({ userId: userId.trim(), role }); }}>{t("staff.revoke")}</Button>
        </div>
        <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
        <div className="grid gap-3 md:grid-cols-[1fr_auto] md:items-end">
          <Input label={t("staff.resetUserId")} value={userId} onChange={(event) => { setUserId(event.target.value); }} />
          <Button variant="primary" disabled={!canSubmit || requestReset.isPending} onClick={() => { requestReset.mutate({ userId: userId.trim(), reason: reason.trim() }); }}>{t("staff.requestReset")}</Button>
        </div>
        <ApiErrorNote error={grant.error ?? revoke.error ?? requestReset.error} />
      </AdminSection>
      <AdminSection title={t("staff.queueTitle")} description={t("staff.queueDescription")}>
        {requests.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {requests.isError ? <ApiErrorNote error={requests.error} /> : null}
        {requests.data && requests.data.length === 0 ? <p className="text-caption text-text-secondary">{t("feedback.empty")}</p> : null}
        {requests.data && requests.data.length > 0 ? (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead><tr><th className={adminThClass}>{t("staff.target")}</th><th className={adminThClass}>{t("staff.requester")}</th><th className={adminThClass}>{t("staff.reason")}</th><th className={adminThClass}>{t("staff.requestedAt")}</th><th className={adminThClass}>{t("staff.action")}</th></tr></thead>
              <tbody>{requests.data.map((item) => <tr key={item.id}>
                <td className={adminTdClass}><code>{item.targetUserId}</code></td>
                <td className={adminTdClass}><code>{item.requestedBy}</code></td>
                <td className={adminTdClass}>{item.reason}</td>
                <td className={adminTdClass}>{new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(item.requestedAt))}</td>
                <td className={adminTdClass}><Button variant="tertiary" size="sm" disabled={approve.isPending} onClick={() => { approve.mutate(item.id); }}>{t("staff.approve")}</Button></td>
              </tr>)}</tbody>
            </table>
          </AdminTableScroll>
        ) : null}
        <ApiErrorNote error={approve.error} />
      </AdminSection>
    </div>
  );
}
