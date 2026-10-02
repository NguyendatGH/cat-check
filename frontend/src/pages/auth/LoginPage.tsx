import { useEffect, useRef, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { AtSign, ArrowRight, Check, ExternalLink, ShieldCheck } from "lucide-react";
import { Button, Input, Switch } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { LogoPawIcon } from "@/shared/assets/icons/AppIcons";
import loginDemoCat from "@/shared/assets/images/login-demo-cat.jpg";
import showcaseCat from "@/shared/assets/images/web-auth/signin-showcase-cat.png";
import reviewerAvatar from "@/shared/assets/images/web-auth/signin-reviewer.png";
import isfmBadge from "@/shared/assets/icons/web-auth/isfm-badge.svg";
import brandPaw from "@/shared/assets/icons/web-auth/brand-paw.svg";
import avatarCheck from "@/shared/assets/icons/web-auth/avatar-check.svg";
import phFlask from "@/shared/assets/icons/web-auth/ph-flask.svg";
import ratingStarBox from "@/shared/assets/icons/web-auth/rating-star-box.svg";
import starIcon from "@/shared/assets/icons/web-auth/star.svg";
import portalBadge from "@/shared/assets/icons/web-auth/portal-badge.svg";
import cloudShield from "@/shared/assets/icons/web-auth/cloud-shield.svg";
import {
  GoogleAuthButton,
  InlineAlert,
  PasswordField,
  isMfaChallenge,
  loginSchema,
  useAuthFlowStore,
  useLogin,
  useVerifyLoginRecoveryCode,
  useVerifyLoginTotp,
  type LoginFieldValues,
} from "@/features/auth";

const DEFAULT_AFTER_LOGIN = "/dashboard";

/**
 * Nội dung MOCK THEO THIẾT KẾ cho panel giới thiệu bên trái bản web (Figma 16:7573).
 * Đây là khu vực marketing hiển thị TRƯỚC khi đăng nhập nên không có API nào cấp dữ liệu;
 * số liệu lấy đúng như trong Figma, không phải dữ liệu thật của người dùng.
 */
const SHOWCASE_MOCK = {
  catName: "Miu Miu (Luna)",
  catAge: "2 tuổi",
  lastCheck: "Lần kiểm tra gần nhất: 10 phút trước",
  phBadge: "pH 6.8 • Bình thường",
  phPercent: 55,
  statsValue: "15.000+",
  quote: "“Nhờ hạt cát đổi màu tím mà mình phát hiện bé bị sỏi bàng quang sớm, cứu bé kịp thời!”",
  quoteAuthor: "Thu Hà • Sen của Mochi & Đậu Đậu (Cầu Giấy, HN)",
} as const;

function resolveRedirectTarget(next: string | null): string {
  // Chỉ chấp nhận path nội bộ bắt đầu bằng "/" — chặn open-redirect qua query `next`.
  if (next && next.startsWith("/") && !next.startsWith("//")) return next;
  return DEFAULT_AFTER_LOGIN;
}

/**
 * `/auth/login` (p9 §9.4.3 #2, M1 01b · W1 Web-01) — khớp Figma (CatCheck-Demo
 * 26mOVF2zdu4cI1EPz2Syxw). `AuthLayout` chỉ vẽ TopBar task-level dùng chung; logo tròn +
 * badge phiên bản + thẻ demo mèo là nội dung riêng của trang này. Không có panel marketing
 * 2 cột (p10 §6.1) — giữ single-column cho mọi breakpoint (xem `docs/handovers/A1-fe.md`).
 *
 * Thẻ demo "Bé Luna & Bạn" là nội dung trang trí tĩnh lấy nguyên văn từ ảnh thiết kế gốc
 * (asset/screen/.../01b) — màn hình trước đăng nhập chưa có dữ liệu mèo thật để hiển thị.
 *
 * Bước 2 MFA (A10/A11, p11 §11.12): `POST /auth/login` có thể trả `mfaRequired: true`
 * ngay trên trang này (không có route riêng) — chuyển UI in-place sang nhập mã TOTP hoặc
 * mã khôi phục.
 */
export function LoginPage() {
  const { t } = useTranslation(["auth", "common"]);
  const [searchParams] = useSearchParams();
  const setPendingLoginMfaMethods = useAuthFlowStore((s) => s.setPendingLoginMfaMethods);
  const pendingLoginMfaMethods = useAuthFlowStore((s) => s.pendingLoginMfaMethods);

  const login = useLogin();
  const verifyTotp = useVerifyLoginTotp();
  const verifyRecovery = useVerifyLoginRecoveryCode();

  const [loginError, setLoginError] = useState<string | null>(null);
  const [oauthCancelled, setOauthCancelled] = useState(false);
  const [useRecoveryCode, setUseRecoveryCode] = useState(false);
  const [mfaCode, setMfaCode] = useState("");
  const [mfaError, setMfaError] = useState<string | null>(null);
  const mfaInputRef = useRef<HTMLInputElement>(null);

  // jsx-a11y/no-autofocus cấm prop `autoFocus` — focus bằng tay khi bước MFA vừa hiện,
  // hành vi tương đương (M1 01c-2 tự focus ô nhập mã).
  useEffect(() => {
    if (pendingLoginMfaMethods) {
      mfaInputRef.current?.focus();
    }
  }, [pendingLoginMfaMethods]);

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

  const redirectAfterLogin = () => {
    window.location.assign(resolveRedirectTarget(searchParams.get("next")));
  };

  const onSubmit = async (values: LoginFieldValues) => {
    setLoginError(null);
    try {
      const result = await login.mutateAsync(values);
      if (isMfaChallenge(result)) {
        setPendingLoginMfaMethods(result.mfaMethods);
        return;
      }
      redirectAfterLogin();
    } catch (error) {
      // Nhánh mặc định TRƯỚC ĐÂY là `invalidCredentials`, nên mọi lỗi lạ đều hiện "sai mật
      // khẩu" — kể cả `SESSION_LIMIT_REACHED` (409), khiến người dùng gõ lại đúng mật khẩu
      // mãi không vào được. Giờ chỉ 401/INVALID_CREDENTIALS mới nói sai thông tin đăng nhập.
      const code = isApiError(error) ? error.code : undefined;
      if (code === "ACCOUNT_LOCKED") {
        setLoginError(t("login.errors.accountLocked"));
      } else if (code === "ACCOUNT_NOT_VERIFIED") {
        setLoginError(t("login.errors.accountNotVerified"));
      } else if (code === "RATE_LIMITED") {
        setLoginError(t("errors.rateLimited"));
      } else if (code === "SESSION_LIMIT_REACHED") {
        setLoginError(t("login.errors.sessionLimitReached"));
      } else if (code === "INVALID_CREDENTIALS" || (isApiError(error) && error.status === 401)) {
        setLoginError(t("login.errors.invalidCredentials"));
      } else {
        setLoginError(t("errors.generic"));
      }
    }
  };

  const onSubmitMfa = async () => {
    setMfaError(null);
    try {
      if (useRecoveryCode) {
        await verifyRecovery.mutateAsync(mfaCode);
      } else {
        await verifyTotp.mutateAsync(mfaCode);
      }
      redirectAfterLogin();
    } catch (error) {
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
    return (
      <div className="flex flex-col gap-6">
        <header className="flex flex-col gap-1 text-center">
          <h1 className="text-h2 font-bold text-text-primary">{t("mfa.title")}</h1>
          <p className="text-caption text-text-secondary">
            {useRecoveryCode ? t("mfa.recoverySubtitle") : t("mfa.totpSubtitle")}
          </p>
        </header>

        <Input
          ref={mfaInputRef}
          label={useRecoveryCode ? t("mfa.fields.recoveryCode.label") : t("mfa.fields.code.label")}
          inputMode={useRecoveryCode ? "text" : "numeric"}
          maxLength={useRecoveryCode ? 24 : 6}
          value={mfaCode}
          onChange={(event) => { setMfaCode(event.target.value.trim()); }}
          error={mfaError ?? undefined}
        />

        <Button type="button" size="lg" loading={isBusy} disabled={!mfaCode || isBusy} onClick={() => { void onSubmitMfa(); }}>
          {t("mfa.submit")}
        </Button>

        <button
          type="button"
          onClick={() => {
            setUseRecoveryCode((v) => !v);
            setMfaCode("");
            setMfaError(null);
          }}
          className="min-h-11 text-caption font-semibold text-primary hover:underline"
        >
          {useRecoveryCode ? t("mfa.useTotpInstead") : t("mfa.useRecoveryInstead")}
        </button>

        <button
          type="button"
          onClick={() => { setPendingLoginMfaMethods(null); }}
          className="min-h-11 text-caption text-text-tertiary hover:underline"
        >
          {t("mfa.backToLogin")}
        </button>
      </div>
    );
  }

  return (
    <div className="lg:grid lg:min-h-[733.5px] lg:grid-cols-12 lg:overflow-hidden lg:rounded-3xl lg:bg-surface lg:shadow-[0px_25px_50px_-12px_rgba(0,0,0,0.25)]">
      {/* ══ Cột trái CHỈ CÓ Ở WEB (Figma 16:7573) — panel thương hiệu nền gradient ══ */}
      <aside
        className="relative hidden overflow-hidden lg:col-span-7 lg:flex lg:flex-col lg:justify-between lg:p-14"
        style={{
          backgroundImage:
            "linear-gradient(134.43deg, var(--color-primary-dark) 0%, var(--color-primary) 50%, rgb(26,62,161) 100%)",
        }}
      >
        <div
          className="pointer-events-none absolute -right-20 -top-20 size-96 rounded-full bg-[rgba(255,223,147,0.1)] blur-[32px]"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute bottom-0 left-1/4 right-[29.29%] h-80 rounded-full bg-[rgba(220,225,255,0.15)] blur-[20px]"
          aria-hidden="true"
        />

        <div className="relative flex flex-col gap-6">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2 rounded-full bg-[rgba(255,255,255,0.15)] px-3.5 py-1.5 backdrop-blur-[6px]">
              <span className="size-2 rounded-full bg-secondary" aria-hidden="true" />
              <span className="text-[12px] font-bold tracking-[0.3px] text-background">
                {t("web.signin.innovationBadge")}
              </span>
            </span>
            <span className="flex items-center gap-1.5 rounded-full bg-[rgba(255,255,255,0.1)] px-3 py-1">
              <img src={isfmBadge} alt="" className="h-[14px] w-[14.667px]" />
              <span className="text-[11px] font-semibold tracking-[0.4px] text-white">
                {t("web.signin.partnerBadge")}
              </span>
            </span>
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <span className="flex size-12 items-center justify-center rounded-2xl bg-surface shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1),0px_2px_4px_-2px_rgba(0,0,0,0.1)]">
                <img src={brandPaw} alt="" className="h-[23.75px] w-[25px]" />
              </span>
              <span className="flex flex-col">
                <span className="text-[32px] font-extrabold leading-10 tracking-[-0.8px] text-background">
                  CATCHECK
                </span>
                <span className="text-[11px] font-semibold uppercase tracking-[0.55px] text-on-primary-subtle">
                  {t("web.signin.brandTagline")}
                </span>
              </span>
            </div>
            <h2 className="pt-2 text-[24px] font-extrabold leading-[33px] text-background">
              {t("web.signin.headline")}
            </h2>
            <p className="max-w-[576px] text-[16px] leading-6 text-on-primary-subtle opacity-90">
              {t("web.signin.subheadline")}
            </p>
          </div>
        </div>

        {/* Thẻ mèo + thang pH + thống kê (dữ liệu mock theo thiết kế) */}
        <div className="relative grid grid-cols-12 gap-4 py-8">
          <div className="col-span-7 flex flex-col justify-between rounded-2xl bg-[rgba(255,255,255,0.15)] p-4 shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] backdrop-blur-[12px]">
            <div className="flex items-center gap-3">
              <span className="relative shrink-0">
                <img src={showcaseCat} alt="" className="size-14 rounded-2xl object-cover" />
                <span className="absolute -bottom-1 -right-1 flex size-4 items-center justify-center rounded-full bg-verified-bright">
                  <img src={avatarCheck} alt="" className="h-[6.825px] w-[8.95px]" />
                </span>
              </span>
              <span className="flex flex-col">
                <span className="flex items-center gap-2">
                  <span className="text-[18px] font-bold leading-6 text-background">{SHOWCASE_MOCK.catName}</span>
                  <span className="rounded-full bg-secondary-light/20 px-2 py-0.5 text-[11px] font-semibold tracking-[0.4px] text-secondary-light">
                    {SHOWCASE_MOCK.catAge}
                  </span>
                </span>
                <span className="text-[11px] font-semibold tracking-[0.4px] text-on-primary-subtle">
                  {SHOWCASE_MOCK.lastCheck}
                </span>
              </span>
            </div>

            <div className="mt-4 flex flex-col gap-2 rounded-xl bg-[rgba(255,255,255,0.1)] p-3">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1">
                  <img src={phFlask} alt="" className="h-[13.333px] w-[10.667px]" />
                  <span className="text-[11px] font-semibold tracking-[0.4px] text-background">
                    {t("web.signin.phLabel")}
                  </span>
                </span>
                <span className="rounded-full bg-verified-bright px-2 py-0.5 text-[12px] font-bold tracking-[0.3px] text-[rgb(0,33,9)]">
                  {SHOWCASE_MOCK.phBadge}
                </span>
              </div>
              <div
                className="relative h-3 rounded-full"
                style={{
                  backgroundImage:
                    "linear-gradient(90deg, rgb(251,191,36) 0%, rgb(52,211,153) 50%, rgb(56,189,248) 100%)",
                }}
              >
                <span
                  className="absolute top-1/2 flex h-4 -translate-y-1/2 items-center justify-center rounded-full bg-surface px-1 shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1)]"
                  style={{ left: `${String(SHOWCASE_MOCK.phPercent)}%` }}
                >
                  <span className="size-2 rounded-full bg-primary" />
                </span>
              </div>
              <div className="flex items-start justify-between text-[11px] font-semibold tracking-[0.4px]">
                <span className="text-[rgba(222,225,249,0.9)]">{t("web.signin.phAcid")}</span>
                <span className="text-background">{t("web.signin.phIdeal")}</span>
                <span className="text-[rgba(222,225,249,0.9)]">{t("web.signin.phAlkaline")}</span>
              </div>
            </div>
          </div>

          <div className="col-span-5 flex flex-col justify-between gap-4">
            <div className="rounded-2xl bg-[rgba(255,255,255,0.15)] p-4 shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1)] backdrop-blur-[12px]">
              <p className="text-[32px] font-extrabold leading-10 text-secondary-light">{SHOWCASE_MOCK.statsValue}</p>
              <p className="text-[11px] font-semibold tracking-[0.4px] text-background">
                {t("web.signin.statsCaption")}
              </p>
              <p className="text-[11px] font-semibold tracking-[0.4px] text-on-primary-subtle opacity-80">
                {t("web.signin.statsSub")}
              </p>
            </div>
            <div className="flex items-center gap-3 rounded-2xl bg-[rgba(255,255,255,0.15)] p-3.5 shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1)] backdrop-blur-[12px]">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-secondary-light">
                <img src={ratingStarBox} alt="" className="h-[15.833px] w-[16.667px]" />
              </span>
              <span className="flex flex-col">
                <span className="text-[12px] font-bold tracking-[0.3px] text-background">
                  {t("web.signin.ratingTitle")}
                </span>
                <span className="text-[11px] font-semibold tracking-[0.4px] text-on-primary-subtle">
                  {t("web.signin.ratingSub")}
                </span>
              </span>
            </div>
          </div>
        </div>

        <div className="relative flex items-center justify-between pt-4">
          <div className="flex items-center gap-3">
            <img src={reviewerAvatar} alt="" className="size-10 shrink-0 rounded-full object-cover" />
            <div className="flex flex-col">
              <p className="text-[14px] font-semibold leading-5 text-background">{SHOWCASE_MOCK.quote}</p>
              <p className="text-[11px] font-semibold tracking-[0.4px] text-on-primary-subtle">
                {SHOWCASE_MOCK.quoteAuthor}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            {[0, 1, 2, 3, 4].map((i) => (
              <img key={i} src={starIcon} alt="" className="h-[14.25px] w-[15px]" />
            ))}
          </div>
        </div>
      </aside>

      {/* ══ Cột form — mobile là toàn bộ trang, web là 5 cột bên phải ══ */}
      <div className="flex flex-col gap-6 pb-6 pt-2 lg:col-span-5 lg:justify-between lg:gap-0 lg:bg-surface lg:p-12 lg:pb-12 lg:pt-12">
      <header className="order-1 flex flex-col items-center gap-1 text-center lg:hidden">
        <div className="relative mb-1 flex size-20 items-center justify-center rounded-full bg-primary-dark/10">
          <div className="flex size-16 items-center justify-center rounded-full bg-primary-dark text-white shadow-[0px_8px_20px_-4px_rgba(13,54,154,0.35)]">
            <LogoPawIcon size={30} />
          </div>
          <span className="absolute bottom-1 right-1 flex size-7 items-center justify-center rounded-full bg-secondary text-secondary-text-on">
            <Check size={15} aria-hidden="true" strokeWidth={3} />
          </span>
        </div>
        <div className="flex items-center gap-2">
          <p className="text-[20px] font-extrabold tracking-[-0.5px] text-primary-dark">
            CAT<span className="text-secondary">CHECK</span>
          </p>
          <span className="rounded-full bg-secondary/30 px-2.5 py-0.5 text-overline font-semibold text-secondary-text-on">
            {t("login.versionBadge")}
          </span>
        </div>
        <h1 className="pt-2 text-h2 font-bold text-text-primary">{t("login.title")}</h1>
        <p className="max-w-[330px] text-caption leading-[21px] text-text-secondary">{t("login.subtitle")}</p>
      </header>

      <div className="order-2 flex items-center gap-3.5 rounded-xl bg-surface p-4 shadow-xs lg:hidden">
        <img
          src={loginDemoCat}
          alt=""
          className="size-12 shrink-0 rounded-full object-cover"
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate text-body font-bold text-text-primary">{t("login.demoCat.name")}</p>
            <span className="shrink-0 rounded-full bg-verified-bright/40 px-2 py-0.5 text-overline font-semibold text-verified-deep">
              {t("login.demoCat.status")}
            </span>
          </div>
          <p className="pt-0.5 text-[11px] leading-[18px] tracking-[0.4px] text-text-secondary">
            {t("login.demoCat.reminder")}
          </p>
        </div>
      </div>

      {/* Header form CHỈ CÓ Ở WEB (Figma 16:7682) */}
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
        onSubmit={(event) => { void handleSubmit(onSubmit)(event); }}
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
            {/* Web (Figma 16:7712): link nằm ngay hàng label. Mobile: xuống hàng toggle bên dưới. */}
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

        {/* Mobile (Figma 01b): toggle TRÁI + "Quên mật khẩu?" PHẢI trên cùng 1 hàng.
            Web (Figma 16:7719): checkbox vuông 16px, không phải toggle. */}
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
              <label htmlFor="login-remember-web" className="hidden min-h-11 cursor-pointer items-center gap-2.5 lg:flex">
                <input
                  id="login-remember-web"
                  type="checkbox"
                  checked={field.value}
                  onChange={(event) => { field.onChange(event.target.checked); }}
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

        {loginError ? <InlineAlert>{loginError}</InlineAlert> : null}

        <Button
          type="submit"
          size="lg"
          loading={login.isPending}
          disabled={login.isPending}
          className="gap-2"
        >
          {t("login.submit")}
          <ArrowRight size={18} aria-hidden="true" />
        </Button>
      </form>

      {/* Google + divider: dưới form ở mobile, TRÊN form ở web (Figma 16:7693/16:7701) */}
      <div className="order-4 flex flex-col gap-6 lg:order-2 lg:gap-5 lg:pt-6">
        <div className="flex items-center gap-3 lg:hidden" role="separator">
          <span className="h-px flex-1 bg-on-primary-subtle" />
          <span className="text-overline font-semibold text-nav-inactive">{t("login.divider")}</span>
          <span className="h-px flex-1 bg-on-primary-subtle" />
        </div>

        {/* Web (Figma 16:7693): nút Google nền #F3F2FF, không viền. Mobile: trắng + viền. */}
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
