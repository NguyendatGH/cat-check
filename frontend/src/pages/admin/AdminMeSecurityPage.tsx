import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2, Monitor, ShieldAlert, ShieldCheck } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import { formatDate } from "@/shared/lib/format/formatDate";
import { AdminPageHeader, AdminSection, ApiErrorNote } from "@/features/admin";
import {
  RecoveryCodeGrid,
  useDeleteTotp,
  useRegenerateRecoveryCodes,
  useRevokeAllSessions,
  useRevokeSession,
  useSessions,
  useTotpConfirm,
  useTotpInit,
  useTotpStatus,
} from "@/features/auth";

export function AdminMeSecurityPage() {
  const { t } = useTranslation("admin");
  const queryClient = useQueryClient();

  const totp = useTotpStatus();
  const totpInit = useTotpInit();
  const totpConfirm = useTotpConfirm();
  const totpDelete = useDeleteTotp();
  const regenerate = useRegenerateRecoveryCodes();
  const [totpCode, setTotpCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);

  const sessions = useSessions();
  const revokeSession = useRevokeSession();
  const revokeAll = useRevokeAllSessions();

  const totpActive = totp.data?.status === "ACTIVE";
  const sessionItems = sessions.data?.items ?? [];
  const otherSessions = sessionItems.filter((item) => !item.current);

  const invalidateTotp = () => {
    void queryClient.invalidateQueries({ queryKey: ["auth", "totp-status"] });
  };
  const invalidateSessions = () => {
    void queryClient.invalidateQueries({ queryKey: ["auth", "sessions"] });
  };

  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <AdminPageHeader title={t("pages.meSecurity.title")} description={t("pages.meSecurity.description")} />

      <p className="flex items-start gap-2 rounded-xl bg-info px-4 py-3 text-caption text-info-text">
        <ShieldAlert size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
        {t("meSecurity.totpGateNote")}
      </p>

      <AdminSection
        title={t("meSecurity.totpTitle")}
        description={t("meSecurity.totpDescription")}
        actions={
          <span
            className={
              totpActive
                ? "inline-flex items-center gap-1.5 rounded-full bg-success-bg px-3 py-1 text-caption font-semibold text-success-text"
                : "inline-flex items-center gap-1.5 rounded-full bg-warning-bg px-3 py-1 text-caption font-semibold text-warning-text"
            }
          >
            <ShieldCheck size={15} aria-hidden="true" />
            {totpActive ? t("meSecurity.totpActive") : t("meSecurity.totpInactive")}
          </span>
        }
      >
        {totp.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : totp.isError ? (
          <ApiErrorNote error={totp.error} />
        ) : totpActive ? (
          <div className="flex flex-col gap-3">
            {totp.data.activatedAt != null ? (
              <p className="text-caption text-text-secondary">
                {t("meSecurity.activatedAt", { at: formatDate(totp.data.activatedAt, "dd/MM/yyyy HH:mm") })}
              </p>
            ) : null}
            {totp.data.recoveryCodesRemaining != null ? (
              <p className="text-caption text-text-secondary">
                {t("meSecurity.recoveryRemaining", { count: totp.data.recoveryCodesRemaining })}
              </p>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="tertiary"
                size="md"
                loading={regenerate.isPending}
                onClick={() => {
                  regenerate.mutate(undefined, {
                    onSuccess: (result) => {
                      setRecoveryCodes(result.recoveryCodes);
                    },
                  });
                }}
              >
                {t("meSecurity.regenerateCta")}
              </Button>
              <Button
                type="button"
                variant="tertiary"
                size="md"
                loading={totpDelete.isPending}
                onClick={() => {
                  totpDelete.mutate(undefined, {
                    onSuccess: () => {
                      setRecoveryCodes(null);
                      invalidateTotp();
                    },
                  });
                }}
              >
                {t("meSecurity.disableCta")}
              </Button>
            </div>
          </div>
        ) : totpInit.data !== undefined ? (
          <div className="flex max-w-md flex-col gap-3">
            <div className="rounded-xl bg-background-alt/70 p-3.5">
              <p className="text-overline text-text-tertiary">{t("meSecurity.secretLabel")}</p>
              <p className="break-all pt-1 font-mono text-body font-bold text-text-primary">
                {totpInit.data.secretBase32}
              </p>
            </div>
            <Input
              label={t("meSecurity.codeLabel")}
              inputMode="numeric"
              autoComplete="one-time-code"
              value={totpCode}
              onChange={(event) => {
                setTotpCode(event.target.value);
              }}
            />
            <Button
              type="button"
              size="md"
              loading={totpConfirm.isPending}
              onClick={() => {
                totpConfirm.mutate(totpCode, {
                  onSuccess: (result) => {
                    setRecoveryCodes(result.recoveryCodes);
                    setTotpCode("");
                    invalidateTotp();
                  },
                });
              }}
            >
              {t("meSecurity.confirmCta")}
            </Button>
          </div>
        ) : (
          <Button
            type="button"
            size="md"
            className="self-start"
            loading={totpInit.isPending}
            onClick={() => {
              totpInit.mutate(undefined);
            }}
          >
            {t("meSecurity.enableCta")}
          </Button>
        )}

        <ApiErrorNote error={totpInit.error ?? totpConfirm.error ?? totpDelete.error ?? regenerate.error} />

        {recoveryCodes !== null ? (
          <div className="rounded-xl bg-warning-bg p-3.5">
            <p className="text-body font-bold text-warning-text">{t("meSecurity.recoveryTitle")}</p>
            <p className="pb-2 pt-0.5 text-caption text-warning-text">{t("meSecurity.recoveryBody")}</p>
            <RecoveryCodeGrid codes={recoveryCodes} />
          </div>
        ) : null}
      </AdminSection>

      <AdminSection
        title={t("meSecurity.sessionsTitle")}
        description={t("meSecurity.sessionsDescription")}
        actions={
          otherSessions.length > 0 ? (
            <Button
              type="button"
              variant="tertiary"
              size="sm"
              loading={revokeAll.isPending}
              onClick={() => {
                revokeAll.mutate(undefined, { onSuccess: invalidateSessions });
              }}
            >
              {t("meSecurity.revokeAllCta")}
            </Button>
          ) : undefined
        }
      >
        {sessions.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : sessions.isError ? (
          <ApiErrorNote error={sessions.error} />
        ) : (
          <ul className="flex flex-col gap-2">
            {sessionItems.map((item) => (
              <li key={item.id} className="flex items-center gap-3 rounded-xl bg-background-alt/60 p-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                  <Monitor size={17} aria-hidden="true" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="text-body font-semibold text-text-primary">
                      {item.deviceLabel ?? t("meSecurity.unknownDevice")}
                    </span>
                    {item.current ? (
                      <span className="rounded-md bg-success-bg px-2 py-0.5 text-small font-bold text-success-text">
                        {t("meSecurity.currentSession")}
                      </span>
                    ) : null}
                  </span>
                  <span className="block text-caption text-text-secondary">
                    {t("meSecurity.lastSeen", { at: formatDate(item.lastSeenAt, "dd/MM/yyyy HH:mm") })}
                  </span>
                </span>
                {!item.current ? (
                  <button
                    type="button"
                    onClick={() => {
                      revokeSession.mutate(item.id, { onSuccess: invalidateSessions });
                    }}
                    className="inline-flex min-h-[var(--touch-target-min)] shrink-0 items-center rounded-md px-2 text-caption font-semibold text-danger hover:underline focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-[var(--focus-ring-color)]"
                  >
                    {t("meSecurity.revokeCta")}
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </AdminSection>
    </div>
  );
}
