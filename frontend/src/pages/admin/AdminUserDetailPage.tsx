import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { Eye, LogOut } from "lucide-react";
import { Button } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminQueryError,
  DensePanel,
  DenseTableFrame,
  DetailGrid,
  EMPTY_CELL,
  MutationErrorNote,
  ReasonField,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isReasonValid,
  useAdminUserDetail,
  useAdminUserSessions,
  useRevokeAdminUserSessions,
  useUnmaskAdminUser,
} from "@/features/admin";
import { useGroup1Can } from "./group1Access";

export function AdminUserDetailPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  // Mở che dữ liệu liên hệ: ROLES.users (backend UNMASK_ROLES). Xem/thu hồi phiên: ROLES.userLock.
  const canUnmask = can("users");
  const canSessions = can("userLock");
  const { userId } = useParams<{ userId: string }>();
  const detail = useAdminUserDetail(userId);
  // Không có quyền xem phiên thì không gọi API (server sẽ trả 403).
  const sessions = useAdminUserSessions(canSessions ? userId : undefined);
  const unmask = useUnmaskAdminUser();
  const revoke = useRevokeAdminUserSessions();
  const [reason, setReason] = useState("");
  const [unmasked, setUnmasked] = useState<{ email: string; phone: string | null; until: string } | null>(null);
  const reasonValid = userId !== undefined && isReasonValid(reason);

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
    <div className="flex max-w-5xl flex-col gap-4">
      <AdminPageHeader
        title={t("pages.userDetail.title")}
        description={t("pages.userDetail.description")}
        specRef={userId === undefined ? undefined : t("pages.userDetail.subjectId", { id: userId })}
      />

      {detail.isPending ? <TableSkeleton rows={4} /> : null}
      {detail.isError ? (
        <AdminQueryError
          error={detail.error}
          onRetry={() => {
            void detail.refetch();
          }}
        />
      ) : null}

      {detail.data ? (
        <>
          <DensePanel title={t("userDetail.overviewTitle")}>
            <DetailGrid
              items={[
                [t("userDetail.email"), unmasked?.email ?? detail.data.emailMasked],
                [t("userDetail.phone"), unmasked?.phone ?? detail.data.phoneMasked ?? EMPTY_CELL],
                [t("userDetail.name"), detail.data.fullName],
                [t("userDetail.status"), t(`users.statuses.${detail.data.status}`)],
                [t("userDetail.package"), detail.data.highestPackage ?? EMPTY_CELL],
                [t("userDetail.roles"), detail.data.roles.join(", ") || EMPTY_CELL],
                [t("userDetail.cats"), formatAdminNumber(detail.data.catCount)],
                [t("userDetail.scans"), formatAdminNumber(detail.data.scanCount)],
                [t("userDetail.sessions"), formatAdminNumber(detail.data.activeSessionCount)],
                [t("userDetail.createdAt"), formatAdminDateTime(detail.data.createdAt)],
                [t("userDetail.lastLogin"), formatAdminDateTime(detail.data.lastLoginAt)],
                [t("userDetail.totp"), detail.data.totpEnabled ? t("userDetail.enabled") : t("userDetail.disabled")],
              ]}
            />
          </DensePanel>

          {canUnmask || canSessions ? (
            <DensePanel title={t("userDetail.piiTitle")} description={t("userDetail.piiDescription")}>
              <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
              {canUnmask ? (
                <div className="flex flex-wrap items-center gap-3">
                  <Button
                    variant="tertiary"
                    size="sm"
                    leftIcon={<Eye size={14} />}
                    disabled={!reasonValid || unmask.isPending}
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
                      {t("userDetail.maskAgainAt", { at: formatAdminDateTime(unmasked.until) })}
                    </span>
                  ) : null}
                </div>
              ) : null}
              <MutationErrorNote error={unmask.error} />
            </DensePanel>
          ) : null}

          <DensePanel
            title={t("userDetail.sessionsTitle")}
            actions={
              canSessions ? (
                <Button
                  variant="tertiary"
                  size="sm"
                  leftIcon={<LogOut size={14} />}
                  disabled={!reasonValid || revoke.isPending || detail.data.activeSessionCount === 0}
                  onClick={() => {
                    if (userId !== undefined) {
                      revoke.mutate({ userId, reason: reason.trim() });
                    }
                  }}
                >
                  {t("userDetail.revokeSessions")}
                </Button>
              ) : undefined
            }
          >
            {!canSessions ? (
              <p className="text-caption text-text-secondary">{t("userDetail.sessionsReadOnly")}</p>
            ) : (
              <>
                <MutationErrorNote error={revoke.error} />
                {sessions.isPending ? <TableSkeleton rows={3} /> : null}
                {sessions.isError ? (
                  <AdminQueryError
                    error={sessions.error}
                    onRetry={() => {
                      void sessions.refetch();
                    }}
                  />
                ) : null}
                {sessions.data && sessions.data.items.length === 0 ? (
                  <p className="py-4 text-center text-caption text-text-secondary">{t("feedback.empty")}</p>
                ) : null}
                {sessions.data && sessions.data.items.length > 0 ? (
                  <DenseTableFrame label={t("userDetail.sessionsTitle")}>
                    <table className={denseTableClass}>
                      <thead>
                        <tr>
                          <th scope="col" className={denseThClass}>
                            {t("userDetail.device")}
                          </th>
                          <th scope="col" className={denseThClass}>
                            {t("userDetail.ip")}
                          </th>
                          <th scope="col" className={denseThClass}>
                            {t("userDetail.lastSeen")}
                          </th>
                          <th scope="col" className={denseThClass}>
                            {t("userDetail.expires")}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {sessions.data.items.map((session) => (
                          <tr key={session.id} className={denseRowClass}>
                            <td className={denseTdClass}>{session.deviceLabel ?? t("userDetail.unknownDevice")}</td>
                            <td className={`${denseTdClass} font-mono text-small`}>
                              {session.ipAddressMasked ?? EMPTY_CELL}
                            </td>
                            <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                              {formatAdminDateTime(session.lastSeenAt)}
                            </td>
                            <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                              {formatAdminDateTime(session.expiresAt)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </DenseTableFrame>
                ) : null}
              </>
            )}
          </DensePanel>
        </>
      ) : null}
    </div>
  );
}
