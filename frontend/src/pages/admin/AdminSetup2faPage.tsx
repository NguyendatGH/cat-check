import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Loader2, ShieldCheck } from "lucide-react";
import { Button, Input, buttonVariants } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { ApiErrorNote } from "@/features/admin";
import { RecoveryCodeGrid, useTotpConfirm, useTotpInit, useTotpStatus } from "@/features/auth";

export function AdminSetup2faPage() {
  const { t } = useTranslation("admin");
  const queryClient = useQueryClient();

  const totp = useTotpStatus();
  const totpInit = useTotpInit();
  const totpConfirm = useTotpConfirm();
  const [code, setCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);

  const active = totp.data?.status === "ACTIVE";

  return (
    <main className="mx-auto flex w-full max-w-xl flex-col gap-5 px-4 py-10">
      <header>
        <h1 className="text-h2 font-bold text-text-primary">{t("pages.setup2fa.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("pages.setup2fa.description")}</p>
      </header>

      <section className="flex flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-md">
        {totp.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : totp.isError ? (
          <ApiErrorNote error={totp.error} />
        ) : active ? (
          <>
            <p className="flex items-center gap-2 text-body font-semibold text-success-text">
              <ShieldCheck size={18} aria-hidden="true" />
              {t("setup2fa.doneTitle")}
            </p>
            <p className="text-caption text-text-secondary">{t("setup2fa.doneBody")}</p>
            <Link to="/admin" className={cn(buttonVariants({ variant: "primary", size: "md" }), "self-start")}>
              {t("setup2fa.goToAdmin")}
              <ArrowRight size={16} aria-hidden="true" />
            </Link>
          </>
        ) : totpInit.data !== undefined ? (
          <>
            <ol className="flex list-decimal flex-col gap-1.5 pl-5 text-caption text-text-secondary">
              <li>{t("setup2fa.step1")}</li>
              <li>{t("setup2fa.step2")}</li>
              <li>{t("setup2fa.step3")}</li>
            </ol>
            <div className="rounded-xl bg-background-alt/70 p-3.5">
              <p className="text-overline text-text-tertiary">{t("setup2fa.secretLabel")}</p>
              <p className="break-all pt-1 font-mono text-body font-bold text-text-primary">
                {totpInit.data.secretBase32}
              </p>
            </div>
            <Input
              label={t("setup2fa.codeLabel")}
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(event) => {
                setCode(event.target.value);
              }}
            />
            <Button
              type="button"
              size="md"
              className="self-start"
              loading={totpConfirm.isPending}
              onClick={() => {
                totpConfirm.mutate(code, {
                  onSuccess: (result) => {
                    setRecoveryCodes(result.recoveryCodes);
                    setCode("");
                    void queryClient.invalidateQueries({ queryKey: ["auth", "totp-status"] });
                  },
                });
              }}
            >
              {t("setup2fa.confirmCta")}
            </Button>
          </>
        ) : (
          <>
            <p className="text-caption text-text-secondary">{t("setup2fa.intro")}</p>
            <Button
              type="button"
              size="md"
              className="self-start"
              loading={totpInit.isPending}
              onClick={() => {
                totpInit.mutate(undefined);
              }}
            >
              {t("setup2fa.startCta")}
            </Button>
          </>
        )}

        <ApiErrorNote error={totpInit.error ?? totpConfirm.error} />

        {recoveryCodes !== null ? (
          <div className="rounded-xl bg-warning-bg p-3.5">
            <p className="text-body font-bold text-warning-text">{t("setup2fa.recoveryTitle")}</p>
            <p className="pb-2 pt-0.5 text-caption text-warning-text">{t("setup2fa.recoveryBody")}</p>
            <RecoveryCodeGrid codes={recoveryCodes} />
          </div>
        ) : null}
      </section>
    </main>
  );
}
