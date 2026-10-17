import { useEffect, useRef, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { message } from "antd";
import { loadSession, useSessionStore } from "@/entities/user";
import { playSplash } from "@/shared/lib/motion/splash";
import { ArrowLeft, AtSign, ArrowRight, Check, ExternalLink, KeyRound, ShieldCheck } from "lucide-react";
import { Button, Input, Switch } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import appLogo from "@/shared/assets/images/web-auth/logo.png";
import portalBadge from "@/shared/assets/icons/web-auth/portal-badge.svg";
import cloudShield from "@/shared/assets/icons/web-auth/cloud-shield.svg";
import { LoginShowcase } from "./LoginShowcase";
import { AuthCard } from "./AuthCard";
import {
  GoogleAuthButton,
  OtpCodeInput,
  InlineAlert,
  PasswordField,
  isMfaChallenge,
  loginSchema,
  useAuthFlowStore,
  useBuildVersion,
  useLogin,
  useVerifyLoginRecoveryCode,
  useVerifyLoginTotp,
  type LoginFieldValues,
  type OtpStatus,
} from "@/features/auth";

// The holder lives above the login/MFA view switch so a toast survives it.
export function LoginPage() {
  const [messageApi, contextHolder] = message.useMessage();
  return (
    <>
      {contextHolder}
      <LoginView messageApi={messageApi} />
    </>
  );
}

function LoginView({ messageApi }: { messageApi: ReturnType<typeof message.useMessage>[0] }) {
  const { t } = useTranslation(["auth", "common"]);
  const [searchParams] = useSearchParams();
  const setPendingLoginMfaMethods = useAuthFlowStore((s) => s.setPendingLoginMfaMethods);
  const pendingLoginMfaMethods = useAuthFlowStore((s) => s.pendingLoginMfaMethods);

  const login = useLogin();
  const { data: buildVersion } = useBuildVersion();
  const verifyTotp = useVerifyLoginTotp();
  const verifyRecovery = useVerifyLoginRecoveryCode();

  const [oauthCancelled, setOauthCancelled] = useState(false);
  const [useRecoveryCode, setUseRecoveryCode] = useState(false);
  const [mfaCode, setMfaCode] = useState("");
  const [mfaError, setMfaError] = useState<string | null>(null);
  const [mfaStatus, setMfaStatus] = useState<OtpStatus>("idle");
  const [mfaErrorSignal, setMfaErrorSignal] = useState(0);
  const mfaInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (pendingLoginMfaMethods && useRecoveryCode) {
      mfaInputRef.current?.focus();
    }
  }, [pendingLoginMfaMethods, useRecoveryCode]);

  useEffect(() => {
    if (searchParams.get("error") === "oauth_cancelled") {
      setOauthCancelled(true);
    }
  }, [searchParams]);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<LoginFieldValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "", rememberMe: true },
  });

  const queryClient = useQueryClient();
  const setSession = useSessionStore((state) => state.setSession);
  const enterApp = () => {
    playSplash(loadSession(), (user) => {
      queryClient.removeQueries();
      setSession(user);
    });
  };

  const showLoginError = (content: string) => {
    void messageApi.open({ type: "error", content, key: "login-error" });
  };

  const onSubmit = async (values: LoginFieldValues) => {
    try {
      const result = await login.mutateAsync(values);
      if (isMfaChallenge(result)) {
        setPendingLoginMfaMethods(result.mfaMethods);
        return;
      }
      enterApp();
    } catch (error) {
      const code = isApiError(error) ? error.code : undefined;
      if (code === "ACCOUNT_LOCKED") {
        showLoginError(t("login.errors.accountLocked"));
      } else if (code === "ACCOUNT_NOT_VERIFIED") {
        showLoginError(t("login.errors.accountNotVerified"));
      } else if (code === "RATE_LIMITED") {
        showLoginError(t("errors.rateLimited"));
      } else if (code === "SESSION_LIMIT_REACHED") {
        showLoginError(t("login.errors.sessionLimitReached"));
      } else if (code === "INVALID_CREDENTIALS" || (isApiError(error) && error.status === 401)) {
        showLoginError(t("login.errors.invalidCredentials"));
      } else {
        showLoginError(t("errors.generic"));
      }
    }
  };

  const onSubmitMfa = async (code = mfaCode) => {
    setMfaError(null);
    setMfaStatus("verifying");
    try {
      if (useRecoveryCode) {
        await verifyRecovery.mutateAsync(code);
      } else {
        await verifyTotp.mutateAsync(code);
      }
      setMfaStatus("success");
      enterApp();
    } catch (error) {
      if (isApiError(error) && error.code === "UNAUTHENTICATED") {
        // Bước chờ TOTP đã hết hạn — quay lại form đăng nhập.
        setPendingLoginMfaMethods(null);
        setMfaCode("");
        setMfaStatus("idle");
        showLoginError(t("mfa.errors.expired"));
        return;
      }
      setMfaStatus("error");
      setMfaErrorSignal((n) => n + 1);
      if (isApiError(error) && error.code === "TOTP_INVALID") {
        setMfaError(t("mfa.errors.invalidCode"));
      } else if (isApiError(error) && error.code === "TOTP_LOCKED") {
        setMfaError(t("mfa.errors.locked"));
      } else if (isApiError(error) && error.code === "TOTP_RECOVERY_INVALID") {
        setMfaError(t("mfa.errors.recoveryInvalid"));
      } else if (isApiError(error) && error.code === "TOTP_RECOVERY_EXHAUSTED") {
        setMfaError(t("mfa.errors.recoveryExhausted"));
      } else {
        setMfaError(t("errors.generic"));
      }
    }
  };

  if (pendingLoginMfaMethods) {
    const isBusy = verifyTotp.isPending || verifyRecovery.isPending;
    const canSubmit = useRecoveryCode ? mfaCode.length > 0 : mfaCode.length === 6;
    return (
      <AuthCard>
        <div className="flex flex-col items-center gap-6">
          <header className="flex flex-col items-center gap-3 text-center">
            <span className="content-enter flex size-14 items-center justify-center rounded-2xl bg-chip-bg text-primary-dark">
              {useRecoveryCode ? <KeyRound size={26} aria-hidden="true" /> : <ShieldCheck size={26} aria-hidden="true" />}
            </span>
            <div className="flex flex-col gap-1">
              <h1 className="text-h2 font-bold text-text-primary">{t("mfa.title")}</h1>
              <p className="max-w-xs text-caption leading-relaxed text-text-secondary">
                {useRecoveryCode ? t("mfa.recoverySubtitle") : t("mfa.totpSubtitle")}
              </p>
            </div>
          </header>

          <div key={useRecoveryCode ? "recovery" : "totp"} className="content-enter w-full">
            {useRecoveryCode ? (
              <Input
                ref={mfaInputRef}
                label={t("mfa.fields.recoveryCode.label")}
                inputMode="text"
                autoComplete="one-time-code"
                maxLength={24}
                value={mfaCode}
                onChange={(event) => {
                  setMfaCode(event.target.value.trim());
                  setMfaError(null);
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && canSubmit && !isBusy) void onSubmitMfa();
                }}
                error={mfaError ?? undefined}
              />
            ) : (
              <OtpCodeInput
                value={mfaCode}
                onChange={(next) => {
                  setMfaCode(next);
                  setMfaError(null);
                  if (mfaStatus === "error") setMfaStatus("idle");
                }}
                onComplete={(full) => {
                  if (!isBusy) void onSubmitMfa(full);
                }}
                error={mfaError ?? undefined}
                errorSignal={mfaErrorSignal}
                status={mfaStatus}
                disabled={isBusy}
                focusOnMount
              />
            )}
          </div>

          <Button
            type="button"
            size="lg"
            className="w-full"
            loading={isBusy}
            disabled={!canSubmit || isBusy}
            onClick={() => {
              void onSubmitMfa();
            }}
          >
            {t("mfa.submit")}
          </Button>

          <div className="flex w-full flex-col items-center gap-1 border-t border-border pt-4">
            <button
              type="button"
              onClick={() => {
                setUseRecoveryCode((v) => !v);
                setMfaCode("");
                setMfaError(null);
                setMfaStatus("idle");
              }}
              className="flex min-h-11 items-center gap-2 rounded-lg px-3 text-caption font-semibold text-primary transition-colors hover:bg-chip-bg"
            >
              {useRecoveryCode ? <ShieldCheck size={15} aria-hidden="true" /> : <KeyRound size={15} aria-hidden="true" />}
              {useRecoveryCode ? t("mfa.useTotpInstead") : t("mfa.useRecoveryInstead")}
            </button>
            <button
              type="button"
              onClick={() => {
                setPendingLoginMfaMethods(null);
                setMfaCode("");
                setMfaStatus("idle");
              }}
              className="flex min-h-11 items-center gap-1.5 rounded-lg px-3 text-caption text-text-tertiary transition-colors hover:text-text-secondary"
            >
              <ArrowLeft size={14} aria-hidden="true" />
              {t("mfa.backToLogin")}
            </button>
          </div>
        </div>
      </AuthCard>
    );
  }

  return (
    <div className="lg:grid lg:min-h-[733.5px] lg:grid-cols-12 lg:overflow-hidden lg:rounded-3xl lg:bg-surface lg:shadow-[0px_25px_50px_-12px_rgba(0,0,0,0.25)]">
      <LoginShowcase />

      <div className="flex flex-col gap-6 pb-6 pt-2 lg:col-span-5 lg:justify-between lg:gap-0 lg:bg-surface lg:p-12 lg:pb-12 lg:pt-12">
        <header className="order-1 flex flex-col items-center gap-1 text-center lg:hidden">
          <div className="relative mb-1 flex h-20 w-44 items-center justify-center">
            <img src={appLogo} alt={t("pages.login.title")} className="h-20 w-44 object-contain" />
            <span className="absolute bottom-1 right-1 flex size-7 items-center justify-center rounded-full bg-secondary text-secondary-text-on">
              <Check size={15} aria-hidden="true" strokeWidth={3} />
            </span>
          </div>
          <div className="flex items-center gap-2">
            {buildVersion ? (
              <span className="rounded-full bg-secondary/30 px-2.5 py-0.5 text-overline font-semibold text-secondary-text-on">
                {t("login.versionBadge", { version: buildVersion })}
              </span>
            ) : null}
          </div>
          <h1 className="pt-2 text-h2 font-bold text-text-primary">{t("login.title")}</h1>
          <p className="max-w-[330px] text-caption leading-[21px] text-text-secondary">{t("login.subtitle")}</p>
        </header>

        <div className="order-1 hidden lg:flex lg:flex-col lg:gap-2">
          <span className="flex w-fit items-center gap-2 rounded-full bg-chip-bg px-3 py-1">
            <img src={portalBadge} alt="" className="h-[14px] w-[10.667px]" />
            <span className="text-[11px] font-bold tracking-[0.4px] text-primary-dark">
              {t("web.signin.portalBadge")}
            </span>
          </span>
          <h1 className="pt-[3px] text-[24px] font-extrabold leading-8 text-text-primary">
            {t("web.signin.formTitle")}
          </h1>
          <p className="text-[14px] leading-5 text-text-secondary">{t("web.signin.formSubtitle")}</p>
        </div>

        {oauthCancelled ? (
          <div className="order-2 lg:order-3">
            <InlineAlert tone="info">{t("login.oauthCancelled")}</InlineAlert>
          </div>
        ) : null}

        <form
          className="order-3 flex flex-col gap-4 lg:order-4 lg:gap-4 lg:py-6"
          onSubmit={(event) => {
            void handleSubmit(onSubmit)(event);
          }}
          noValidate
        >
          <div className="flex flex-col gap-1.5">
            <label htmlFor="login-email" className="text-caption font-semibold text-text-secondary">
              {t("login.fields.email.label")}
              <span className="hidden text-danger lg:inline">{` ${t("common.requiredMark")}`}</span>
            </label>
            <Input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder={t("login.fields.email.placeholder")}
              leftIcon={<AtSign size={18} aria-hidden="true" />}
              error={errors.email ? t(errors.email.message ?? "") : undefined}
              className="lg:bg-background-alt lg:shadow-none"
              {...register("email")}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-end justify-between gap-3">
              <label htmlFor="login-password" className="text-caption font-semibold text-text-secondary">
                {t("login.fields.password.label")}
                <span className="hidden text-danger lg:inline">{` ${t("common.requiredMark")}`}</span>
              </label>
              <Link
                to="/auth/forgot-password"
                className="hidden text-caption font-semibold text-primary hover:underline lg:inline"
              >
                {t("login.forgotPassword")}
              </Link>
            </div>
            <PasswordField
              id="login-password"
              autoComplete="current-password"
              error={errors.password ? t(errors.password.message ?? "") : undefined}
              className="lg:bg-background-alt lg:shadow-none"
              {...register("password")}
            />
          </div>

          <Controller
            control={control}
            name="rememberMe"
            render={({ field }) => (
              <div className="flex items-center justify-between gap-3">
                <span className="lg:hidden">
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    onBlur={field.onBlur}
                    label={t("login.rememberMe")}
                  />
                </span>
                <label
                  htmlFor="login-remember-web"
                  className="hidden min-h-11 cursor-pointer items-center gap-2.5 lg:flex"
                >
                  <input
                    id="login-remember-web"
                    type="checkbox"
                    checked={field.value}
                    onChange={(event) => {
                      field.onChange(event.target.checked);
                    }}
                    onBlur={field.onBlur}
                    className="size-4 shrink-0 rounded-sm border-2 border-border-strong text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
                  />
                  <span className="text-caption text-text-secondary">{t("web.signin.rememberMe")}</span>
                </label>
                <Link
                  to="/auth/forgot-password"
                  className="shrink-0 text-caption font-semibold text-primary hover:underline lg:hidden"
                >
                  {t("login.forgotPassword")}
                </Link>
              </div>
            )}
          />

          <Button type="submit" size="lg" loading={login.isPending} disabled={login.isPending} className="gap-2">
            {t("login.submit")}
            <ArrowRight size={18} aria-hidden="true" />
          </Button>
        </form>

        <div className="order-4 flex flex-col gap-6 lg:order-2 lg:gap-5 lg:pt-6">
          <div className="flex items-center gap-3 lg:hidden" role="separator">
            <span className="h-px flex-1 bg-on-primary-subtle" />
            <span className="text-overline font-semibold text-nav-inactive">{t("login.divider")}</span>
            <span className="h-px flex-1 bg-on-primary-subtle" />
          </div>

          <GoogleAuthButton
            label={t("login.googleCta")}
            className="lg:border-transparent lg:bg-background-alt lg:shadow-none"
          />

          <div className="relative hidden items-center justify-center lg:flex" role="separator">
            <span className="h-px w-full bg-chip-bg" />
            <span className="absolute bg-surface px-3 text-[11px] font-medium tracking-[0.4px] text-text-secondary">
              {t("web.signin.divider")}
            </span>
          </div>
        </div>

        <p className="order-5 text-center text-caption text-text-secondary lg:order-5">
          {t("login.noAccount")}{" "}
          <Link to="/auth/register" className="font-semibold text-primary hover:underline">
            <span className="lg:hidden">{t("login.registerLink")}</span>
            <span className="hidden items-center gap-1 lg:inline-flex">
              {t("web.signin.registerLink")}
              <ExternalLink size={12} aria-hidden="true" />
            </span>
          </Link>
        </p>

        <div className="order-6 flex items-start gap-2.5 rounded-xl bg-background-alt p-4 lg:order-6 lg:mt-4 lg:gap-3 lg:rounded-2xl lg:p-3.5">
          <ShieldCheck size={16} aria-hidden="true" className="mt-0.5 shrink-0 text-verified-deep lg:hidden" />
          <span className="hidden size-8 shrink-0 items-center justify-center rounded-xl bg-verified-bright lg:flex">
            <img src={cloudShield} alt="" className="h-[15px] w-3" />
          </span>
          <div>
            <p className="text-caption font-bold text-text-primary lg:text-[11px] lg:font-semibold lg:tracking-[0.4px]">
              <span className="lg:hidden">{t("login.securityBadge.title")}</span>
              <span className="hidden lg:inline">{t("web.signin.securityTitle")}</span>
            </p>
            <p className="pt-0.5 text-[11px] leading-[17px] tracking-[0.2px] text-text-secondary lg:tracking-[0.4px]">
              <span className="lg:hidden">{t("login.securityBadge.description")}</span>
              <span className="hidden lg:inline">{t("web.signin.securityBody")}</span>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
