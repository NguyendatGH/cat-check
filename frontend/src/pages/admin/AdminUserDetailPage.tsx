import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { Eye, Lock, LockOpen, LogOut } from "lucide-react";
import { Button } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminQueryError,
  DensePanel,
  DenseTableFrame,
  DetailGrid,
  EMPTY_CELL,
  MutationErrorNote,
  ReasonConfirmDialog,
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
  useLockAdminUser,
  useUnlockAdminUser,
  useAdminUserSessions,
  useRevokeAdminUserSessions,
  useUnmaskAdminUser,
} from "@/features/admin";
import { useGroup1Can } from "./group1Access";

export function AdminUserDetailPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  const canUnmask = can("users");
  const canSessions = can("userLock");
  const { userId } = useParams<{ userId: string }>();
  const detail = useAdminUserDetail(userId);
  const sessions = useAdminUserSessions(canSessions ? userId : undefined);
  const unmask = useUnmaskAdminUser();
  const revoke = useRevokeAdminUserSessions();
  const lock = useLockAdminUser();
  const unlock = useUnlockAdminUser();
  // Lý do xem PII và lý do thu hồi phiên/khoá tách riêng (p17 A2 bước 4): thu hồi/khoá hỏi lý do trong hộp xác nhận.
  const [reason, setReason] = useState("");
  const [confirming, setConfirming] = useState<"revoke" | "lock" | "unlock" | null>(null);
  const statusMutation = confirming === "unlock" ? unlock : confirming === "lock" ? lock : revoke;
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
        meta={userId === undefined ? undefined : t("pages.userDetail.subjectId", { id: userId })}
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
          <DensePanel
            title={t("userDetail.overviewTitle")}
            actions={
              canSessions ? (
                detail.data.status === "LOCKED" ? (
                  <Button
                    variant="tertiary"
                    size="sm"
                    leftIcon={<LockOpen size={14} />}
                    onClick={() => {
                      unlock.reset();
                      setConfirming("unlock");
                    }}
                  >
                    {t("users.unlock")}
                  </Button>
                ) : (
                  <Button
                    variant="tertiary"
                    size="sm"
                    leftIcon={<Lock size={14} />}
                    disabled={detail.data.status !== "ACTIVE" && detail.data.status !== "PENDING_VERIFICATION"}
                    onClick={() => {
                      lock.reset();
                      setConfirming("lock");
                    }}
                  >
                    {t("users.lock")}
                  </Button>
                )
              ) : undefined
            }
          >
            <DetailGrid
              items={[
                [t("userDetail.email"), unmasked?.email ?? detail.data.emailMasked],
                [t("userDetail.phone"), unmasked?.phone ?? detail.data.phoneMasked ?? EMPTY_CELL],
                [t("userDetail.name"), detail.data.fullName],
                [t("userDetail.status"), t(`users.statuses.${detail.data.status}`)],
                [t("userDetail.package"), detail.data.highestPackage ?? EMPTY_CELL],
                [
                  t("userDetail.roles"),
                  detail.data.roles.map((role) => t(`roleLabels.${role}`, { defaultValue: role })).join(", ") ||
                    EMPTY_CELL,
                ],
                [t("userDetail.cats"), formatAdminNumber(detail.data.catCount)],
                [t("userDetail.scans"), formatAdminNumber(detail.data.scanCount)],
                [t("userDetail.sessions"), formatAdminNumber(detail.data.activeSessionCount)],
                [t("userDetail.createdAt"), formatAdminDateTime(detail.data.createdAt)],
                [t("userDetail.lastLogin"), formatAdminDateTime(detail.data.lastLoginAt)],
                [t("userDetail.totp"), detail.data.totpEnabled ? t("userDetail.enabled") : t("userDetail.disabled")],
              ]}
            />
          </DensePanel>

          {canUnmask ? (
            <DensePanel title={t("userDetail.piiTitle")} description={t("userDetail.piiDescription")}>
              <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
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
                  disabled={revoke.isPending || detail.data.activeSessionCount === 0}
                  onClick={() => {
                    revoke.reset();
                    setConfirming("revoke");
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
                {revoke.isSuccess ? (
                  <p role="status" className="text-caption text-success-text">
                    {t("userDetail.revokedCount", { count: revoke.data.revokedSessions })}
                  </p>
                ) : null}
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
          <ReasonConfirmDialog
            open={confirming !== null}
            onOpenChange={(open) => {
              if (!open) setConfirming(null);
            }}
            title={
              confirming === "revoke"
                ? t("userDetail.revokeConfirmTitle")
                : confirming === "unlock"
                  ? t("users.unlockConfirmTitle")
                  : t("users.lockConfirmTitle")
            }
            target={`${detail.data.fullName} · ${detail.data.emailMasked}`}
            consequence={
              confirming === "revoke"
                ? t("userDetail.revokeConsequence", { count: detail.data.activeSessionCount })
                : confirming === "unlock"
                  ? t("users.unlockConsequence")
                  : t("users.lockConsequence")
            }
            confirmLabel={
              confirming === "revoke"
                ? t("userDetail.revokeSessions")
                : confirming === "unlock"
                  ? t("users.unlock")
                  : t("users.lock")
            }
            destructive={confirming !== "unlock"}
            pending={statusMutation.isPending}
            error={statusMutation.error}
            onConfirm={(value) => {
              if (userId === undefined) return;
              statusMutation.mutate(
                { userId, reason: value },
                {
                  onSuccess: () => {
                    setConfirming(null);
                  },
                },
              );
            }}
          />
        </>
      ) : null}
    </div>
  );
}
