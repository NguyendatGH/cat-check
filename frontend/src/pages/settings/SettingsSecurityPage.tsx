import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Check, KeyRound, Loader2, Monitor, ShieldCheck } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import {
  RecoveryCodeGrid,
  useChangePassword,
  useDeleteTotp,
  useRegenerateRecoveryCodes,
  useRevokeAllSessions,
  useRevokeSession,
  useSessions,
  useTotpConfirm,
  useTotpInit,
  useTotpStatus,
} from "@/features/auth";

/**
 * `/settings/security` — mật khẩu, xác thực hai lớp, thiết bị đăng nhập.
 *
 * Nối API thật: `POST /account/password` (B6), `GET|POST|DELETE /account/mfa/totp*`
 * (B13-B17), `GET|DELETE /auth/sessions` + `POST /auth/sessions/revoke-all` (A14-A16).
 * Thiết kế `Web - 16` chỉ có một lối vào "Bảo mật tài khoản" ở cột điều hướng, không có frame
 * chi tiết — trang này dựng theo đúng tone card/token của bộ thiết kế.
 *
 * Trang nằm trong `TaskLayout`: layout cấp `px-4 py-6` + hộp 944px, trang không tự thêm.
 */

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
}

export function SettingsSecurityPage() {
  const { t } = useTranslation(["settings", "common"]);
  const queryClient = useQueryClient();

  const changePassword = useChangePassword();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  // Khớp ràng buộc backend `ChangePasswordRequest.newPassword` (8..72 ký tự).
  const newPasswordInvalid = newPassword.length > 0 && (newPassword.length < 8 || newPassword.length > 72);

  const { data: totp } = useTotpStatus();
  const totpInit = useTotpInit();
  const totpConfirm = useTotpConfirm();
  const totpDelete = useDeleteTotp();
  const regenerate = useRegenerateRecoveryCodes();
  const [totpCode, setTotpCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);

  const { data: sessions, isPending: sessionsPending, isError: sessionsError } = useSessions();
  const revokeSession = useRevokeSession();
  const revokeAll = useRevokeAllSessions();

  const invalidateSessions = () => {
    void queryClient.invalidateQueries({ queryKey: ["auth", "sessions"] });
  };

  const totpActive = totp?.status === "ACTIVE";
  const otherSessions = sessions?.items.filter((s) => !s.current) ?? [];

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.security.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("security.intro")}</p>
      </header>

      <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          {/* ---------- Đổi mật khẩu (B6) ---------- */}
          <form
            onSubmit={(event) => {
              event.preventDefault();
              changePassword.mutate(
                { currentPassword, newPassword },
                {
                  onSuccess: () => {
                    setCurrentPassword("");
                    setNewPassword("");
                    // Server thu hồi mọi phiên khác — danh sách thiết bị đã khác.
                    invalidateSessions();
                  },
                },
              );
            }}
            className="flex flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-md"
          >
            <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
              <KeyRound size={18} className="shrink-0 text-primary-dark" aria-hidden="true" />
              {t("security.sectionPassword")}
            </h2>

            <Input
              label={t("security.currentPassword")}
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => {
                setCurrentPassword(event.target.value);
              }}
            />
            <Input
              label={t("security.newPassword")}
              type="password"
              autoComplete="new-password"
              helperText={newPasswordInvalid ? undefined : t("security.changePasswordNote")}
              error={newPasswordInvalid ? t("security.newPasswordLength") : undefined}
              value={newPassword}
              onChange={(event) => {
                setNewPassword(event.target.value);
              }}
            />

            <div className="flex items-center gap-3">
              <Button
                type="submit"
                size="md"
                disabled={currentPassword.length === 0 || newPassword.length === 0 || newPasswordInvalid}
                loading={changePassword.isPending}
              >
                {t("security.changePasswordSubmit")}
              </Button>
              <p aria-live="polite" className="text-caption">
                {changePassword.isSuccess ? (
                  <span className="flex items-center gap-1.5 text-success-text">
                    <Check size={14} aria-hidden="true" />
                    {t("security.changePasswordDone")}
                  </span>
                ) : changePassword.isError ? (
                  <span className="flex items-center gap-1.5 text-danger">
                    <AlertTriangle size={14} aria-hidden="true" />
                    {isApiError(changePassword.error) && changePassword.error.status === 400
                      ? t("security.changePasswordInvalidNew")
                      : t("security.changePasswordFailed")}
                  </span>
                ) : null}
              </p>
            </div>
          </form>

          {/* ---------- Thiết bị đăng nhập (A14-A16) ---------- */}
          <section className="flex flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
                <Monitor size={18} className="shrink-0 text-primary-dark" aria-hidden="true" />
                {t("security.sectionSessions")}
              </h2>
              {otherSessions.length > 0 ? (
                <button
                  type="button"
                  onClick={() => {
                    revokeAll.mutate(undefined, { onSuccess: invalidateSessions });
                  }}
                  className="text-caption font-semibold text-danger hover:underline"
                >
                  {t("security.sessionRevokeAll")}
                </button>
              ) : null}
            </div>

            {sessionsPending ? (
              <p className="flex items-center gap-2 text-caption text-text-secondary">
                <Loader2 size={14} className="animate-spin" aria-hidden="true" />
                {t("common:actions.loading")}
              </p>
            ) : sessionsError ? (
              <p className="flex items-start gap-1.5 text-caption text-danger">
                <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
                {t("security.sessionsLoadFailed")}
              </p>
            ) : (
              <ul className="flex flex-col gap-2">
                {sessions.items.map((session) => (
                  <li key={session.id} className="flex items-center gap-3 rounded-xl bg-background-alt/60 p-3">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                      <Monitor size={17} aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="text-body font-semibold text-text-primary">
                          {session.deviceLabel ?? t("security.sessionUnknownDevice")}
                        </span>
                        {session.current ? (
                          <span className="rounded-md bg-success-bg px-2 py-0.5 text-[11px] font-bold text-success-text">
                            {t("security.sessionCurrent")}
                          </span>
                        ) : null}
                      </span>
                      <span className="block text-caption text-text-secondary">
                        {t("security.sessionLastSeen", { at: formatDateTime(session.lastSeenAt) })}
                        {session.ipMasked ? ` · ${session.ipMasked}` : ""}
                      </span>
                    </span>
                    {!session.current ? (
                      <button
                        type="button"
                        onClick={() => {
                          revokeSession.mutate(session.id, { onSuccess: invalidateSessions });
                        }}
                        className="shrink-0 text-caption font-semibold text-danger hover:underline"
                      >
                        {t("security.sessionRevoke")}
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
            <p className="text-caption text-text-tertiary">{t("security.sessionLimitNote")}</p>
          </section>
        </div>

        {/* ---------- TOTP (B13-B17) ---------- */}
        <section className="flex w-full flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md xl:w-[360px] xl:shrink-0">
          {/* Tiêu đề một dòng riêng, nhãn trạng thái xuống dòng dưới: cột 360px không đủ chỗ
              cho cả hai trên một hàng — để chung thì tiêu đề bị bẻ đôi. */}
          <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
            <ShieldCheck size={18} className="shrink-0 text-primary-dark" aria-hidden="true" />
            {t("security.sectionMfa")}
          </h2>
          <span
            className={
              totpActive
                ? "w-fit whitespace-nowrap rounded-full bg-success-bg px-3 py-1 text-caption font-semibold text-success-text"
                : "w-fit whitespace-nowrap rounded-full bg-background-alt px-3 py-1 text-caption font-semibold text-text-secondary"
            }
          >
            {totpActive ? t("security.mfaStatusActive") : t("security.mfaStatusNone")}
          </span>
          <p className="text-caption leading-relaxed text-text-secondary">{t("security.mfaBody")}</p>

          {totpActive ? (
            <>
              {typeof totp.recoveryCodesRemaining === "number" ? (
                <p className="text-caption text-text-secondary">
                  {t("security.mfaRecoveryRemaining", { count: totp.recoveryCodesRemaining })}
                </p>
              ) : null}
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
                {t("security.mfaRecoveryRegenerate")}
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
                      void queryClient.invalidateQueries({ queryKey: ["auth", "totp"] });
                    },
                  });
                }}
              >
                {t("security.mfaDisableCta")}
              </Button>
            </>
          ) : totpInit.data ? (
            <>
              <div className="rounded-xl bg-background-alt/60 p-3.5">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
                  {t("security.mfaSecretLabel")}
                </p>
                <p className="break-all pt-1 font-mono text-body font-bold text-text-primary">
                  {totpInit.data.secretBase32}
                </p>
              </div>
              <Input
                label={t("security.mfaCodeLabel")}
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
                      void queryClient.invalidateQueries({ queryKey: ["auth", "totp"] });
                    },
                  });
                }}
              >
                {t("security.mfaConfirmCta")}
              </Button>
            </>
          ) : (
            <Button
              type="button"
              size="md"
              loading={totpInit.isPending}
              onClick={() => {
                totpInit.mutate(undefined);
              }}
            >
              {t("security.mfaEnableCta")}
            </Button>
          )}

          {totpInit.isError || totpConfirm.isError ? (
            <p className="flex items-start gap-1.5 text-caption text-danger">
              <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
              {isApiError(totpConfirm.error) ? totpConfirm.error.message : t("security.mfaFailed")}
            </p>
          ) : null}

          {recoveryCodes ? (
            <div className="rounded-xl bg-warning-bg p-3.5">
              <p className="text-body font-bold text-warning-text">{t("security.mfaRecoveryTitle")}</p>
              <p className="pb-2 pt-0.5 text-caption text-warning-text">{t("security.mfaRecoveryBody")}</p>
              <RecoveryCodeGrid codes={recoveryCodes} />
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}
