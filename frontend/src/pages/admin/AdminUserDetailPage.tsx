import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { Button } from "@/shared/ui";
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
  useAdminUserDetail,
  useAdminUserSessions,
  useRevokeAdminUserSessions,
  useUnmaskAdminUser,
} from "@/features/admin";

function formatDate(value: string | null): string {
  return value === null
    ? "—"
    : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminUserDetailPage() {
  const { t } = useTranslation("admin");
  const { userId } = useParams<{ userId: string }>();
  const detail = useAdminUserDetail(userId);
  const sessions = useAdminUserSessions(userId);
  const unmask = useUnmaskAdminUser();
  const revoke = useRevokeAdminUserSessions();
  const [reason, setReason] = useState("");
  const [unmasked, setUnmasked] = useState<{ email: string; phone: string | null; until: string } | null>(null);
  const canWrite = userId !== undefined && isReasonValid(reason);

  useEffect(() => {
    if (unmasked === null) return;
    const remaining = new Date(unmasked.until).getTime() - Date.now();
    const timer = window.setTimeout(
      () => {
        setUnmasked(null);
      },
      Math.max(0, remaining),
    );
    return () => {
      window.clearTimeout(timer);
    };
  }, [unmasked]);

  return (
    <div className="flex max-w-5xl flex-col gap-5">
      <AdminPageHeader
        title={t("pages.userDetail.title")}
        description={t("pages.userDetail.description")}
        specRef={userId === undefined ? undefined : t("pages.userDetail.subjectId", { id: userId })}
      />

      {detail.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
      <ApiErrorNote error={detail.error} />

      {detail.data ? (
        <>
          <AdminSection title={t("userDetail.overviewTitle")}>
            <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.email")}</dt>
                <dd className="font-semibold">{unmasked?.email ?? detail.data.emailMasked}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.phone")}</dt>
                <dd className="font-semibold">{unmasked?.phone ?? detail.data.phoneMasked ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.name")}</dt>
                <dd className="font-semibold">{detail.data.fullName}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.status")}</dt>
                <dd>{t(`users.statuses.${detail.data.status}`)}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.package")}</dt>
                <dd>{detail.data.highestPackage ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.roles")}</dt>
                <dd>{detail.data.roles.join(", ") || "—"}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.cats")}</dt>
                <dd>{detail.data.catCount}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.scans")}</dt>
                <dd>{detail.data.scanCount}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.sessions")}</dt>
                <dd>{detail.data.activeSessionCount}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.createdAt")}</dt>
                <dd>{formatDate(detail.data.createdAt)}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.lastLogin")}</dt>
                <dd>{formatDate(detail.data.lastLoginAt)}</dd>
              </div>
              <div>
                <dt className="text-small text-text-tertiary">{t("userDetail.totp")}</dt>
                <dd>{detail.data.totpEnabled ? t("userDetail.enabled") : t("userDetail.disabled")}</dd>
              </div>
            </dl>
          </AdminSection>

          <AdminSection title={t("userDetail.piiTitle")} description={t("userDetail.piiDescription")}>
            <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
            <div className="flex flex-wrap items-center gap-3">
              <Button
                variant="tertiary"
                disabled={!canWrite || unmask.isPending}
                onClick={() => {
                  if (userId !== undefined) {
                    unmask.mutate(
                      { userId, reason: reason.trim() },
                      {
                        onSuccess: (value) => {
                          setUnmasked({ email: value.email, phone: value.phone, until: value.unmaskedUntil });
                        },
                      },
                    );
                  }
                }}
              >
                {t("userDetail.unmask")}
              </Button>
              {unmasked !== null ? (
                <span className="text-small text-warning-text">
                  {t("userDetail.maskAgainAt", { at: formatDate(unmasked.until) })}
                </span>
              ) : null}
            </div>
            <ApiErrorNote error={unmask.error} />
          </AdminSection>

          <AdminSection title={t("userDetail.sessionsTitle")}>
            <div className="flex flex-wrap items-center gap-3">
              <Button
                variant="tertiary"
                disabled={!canWrite || revoke.isPending || detail.data.activeSessionCount === 0}
                onClick={() => {
                  if (userId !== undefined) {
                    revoke.mutate({ userId, reason: reason.trim() });
                  }
                }}
              >
                {t("userDetail.revokeSessions")}
              </Button>
              <ApiErrorNote error={revoke.error ?? sessions.error} />
            </div>
            {sessions.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
            {sessions.data && sessions.data.items.length === 0 ? (
              <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
            ) : null}
            {sessions.data && sessions.data.items.length > 0 ? (
              <AdminTableScroll>
                <table className={adminTableClass}>
                  <thead>
                    <tr>
                      <th className={adminThClass}>{t("userDetail.device")}</th>
                      <th className={adminThClass}>{t("userDetail.ip")}</th>
                      <th className={adminThClass}>{t("userDetail.lastSeen")}</th>
                      <th className={adminThClass}>{t("userDetail.expires")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sessions.data.items.map((session) => (
                      <tr key={session.id}>
                        <td className={adminTdClass}>{session.deviceLabel ?? t("userDetail.unknownDevice")}</td>
                        <td className={adminTdClass}>{session.ipAddressMasked ?? "—"}</td>
                        <td className={adminTdClass}>{formatDate(session.lastSeenAt)}</td>
                        <td className={adminTdClass}>{formatDate(session.expiresAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </AdminTableScroll>
            ) : null}
          </AdminSection>
        </>
      ) : null}
    </div>
  );
}
