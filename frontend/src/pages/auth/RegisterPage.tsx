import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { User, Mail, Gift, Cross, ArrowRight, Globe, ShieldCheck, Tag, SquarePlus } from "lucide-react";
import { Button, Input, Badge, Stepper } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { cn } from "@/shared/lib/cn";
import { WebRegisterSteps } from "./WebRegisterSteps";
import signupCat from "@/shared/assets/images/web-auth/signup-cat.png";
import signupPortalBadge from "@/shared/assets/icons/web-auth/signup-portal-badge.svg";
import voucherBadge from "@/shared/assets/icons/web-auth/voucher-badge.svg";
import voucherGift from "@/shared/assets/icons/web-auth/voucher-gift.svg";
import copyIcon from "@/shared/assets/icons/web-auth/copy.svg";
import previewEye from "@/shared/assets/icons/web-auth/preview-eye.svg";
import value1Alert from "@/shared/assets/icons/web-auth/value1-alert.svg";
import value2Chart from "@/shared/assets/icons/web-auth/value2-chart.svg";
import value3Network from "@/shared/assets/icons/web-auth/value3-network.svg";
import {
  ConsentCheckboxList,
  GoogleAuthButton,
  InlineAlert,
  PasswordField,
  PasswordStrengthMeter,
  registerSchema,
  useAuthFlowStore,
  useConsentPurposes,
  useRegister,
  type RegisterFieldValues,
} from "@/features/auth";

/**
 * Xem trước hồ sơ mèo ở cột phải bản web (Figma 16:6949) — MOCK THEO THIẾT KẾ.
 * Ở bước này người dùng chưa tạo mèo nên không có dữ liệu thật để hiển thị.
 */
const SIGNUP_PREVIEW_MOCK = {
  catName: "Luna (Mèo Anh Lông Ngắn)",
  catMeta: "Cái • 2 Tuổi",
  chipId: "Mã chip định danh: #VN-CAT-9042",
} as const;

/**
 * `/auth/register` (p9 §9.4.3 #3, M1 01c-1 · W1 Web-01c) — chỉ bước "tài khoản chủ nuôi",
 * hiển thị là Bước 1/4 của hành trình đăng ký (`Stepper` dùng chung — xem `LoginPage` không
 * có stepper vì không thuộc luồng đăng ký nhiều bước). Hồ sơ mèo + khảo sát sức khoẻ là 2
 * bước RIÊNG của `features/onboarding` (route `/onboarding/cat`, `/onboarding/health-survey`)
 * — không dựng lại ở đây.
 *
 * Consent (p15 §15.3.3): `SERVICE_CORE` bắt buộc (= checkbox "đồng ý điều khoản"),
 * 4 mục tuỳ chọn lấy nhãn/mô tả thật từ `GET /privacy/purposes` — không bịa câu chữ. Figma
 * không mock UI này (chỉ có 1 checkbox điều khoản) nhưng đây là nghĩa vụ tuân thủ thật
 * (p15) nên GIỮ LẠI, thu gọn sau 1 toggle ẩn/hiện để không phá bố cục gốc.
 */
export function RegisterPage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const setPendingRegistration = useAuthFlowStore((s) => s.setPendingRegistration);
  const register = useRegister();
  const { data: purposes } = useConsentPurposes();

  const [agreeTerms, setAgreeTerms] = useState(false);
  const [optionalConsents, setOptionalConsents] = useState<Record<string, boolean | undefined>>({});
  const [showOptional, setShowOptional] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register: registerField,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<RegisterFieldValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", referralCodeRaw: "" },
  });

  const optionalPurposes = (purposes ?? []).filter((p) => !p.mandatory);

  const onSubmit = async (values: RegisterFieldValues) => {
    setSubmitError(null);
    const consents = [
      { purposeCode: "SERVICE_CORE", granted: true },
      ...optionalPurposes.map((p) => ({ purposeCode: p.code, granted: Boolean(optionalConsents[p.code]) })),
    ];
    try {
      await register.mutateAsync({
        email: values.email,
        password: values.password,
        fullName: values.fullName,
        referralCodeRaw: values.referralCodeRaw || undefined,
        consents,
      });
      setPendingRegistration({
        email: values.email,
        password: values.password,
        fullName: values.fullName,
        referralCodeRaw: values.referralCodeRaw ?? "",
        consents,
      });
      void navigate("/auth/verify-otp");
    } catch (error) {
      if (isApiError(error) && error.code === "EMAIL_ALREADY_REGISTERED") {
        setSubmitError(t("register.errors.emailAlreadyRegistered"));
      } else if (isApiError(error) && error.code === "PASSWORD_TOO_WEAK") {
        setSubmitError(t("register.errors.passwordTooWeak"));
      } else if (isApiError(error) && error.code === "PASSWORD_BREACHED") {
        setSubmitError(t("register.errors.passwordBreached"));
      } else if (isApiError(error) && error.code === "RATE_LIMITED") {
        setSubmitError(t("errors.rateLimited"));
      } else {
        setSubmitError(t("errors.generic"));
      }
    }
  };

  const password = watch("password");
  const canSubmit = agreeTerms;

  return (
    <div className="flex flex-col gap-5 pb-6 lg:gap-10">
      {/* Mobile: stepper 1 dòng. Web: thanh 4 bước (Figma 16:8430). */}
      <div className="lg:hidden">
        <Stepper current={1} total={4} label={t("register.stepLabel")} />
      </div>
      <WebRegisterSteps current={1} withHeader />

      <div className="lg:grid lg:grid-cols-12 lg:items-start lg:gap-8">
        {/* ══ Cột trái: form (web 7 cột, mobile toàn trang) ══ */}
        <div className="flex flex-col gap-5 lg:col-span-7 lg:rounded-3xl lg:bg-surface lg:p-10 lg:shadow-xs">
          {/* Hero card — chỉ mobile (Figma 01c-1) */}
          <div className="relative overflow-hidden rounded-2xl bg-surface p-4 shadow-xs lg:hidden">
            <span
              className="pointer-events-none absolute -bottom-10 -right-6 size-28 rounded-full bg-secondary/20"
              aria-hidden="true"
            />
            <div className="relative flex items-start gap-3">
              <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-info text-primary-dark">
                <Cross size={22} aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-1.5">
                  <Badge tone="brand" className="bg-secondary px-2 py-0 text-overline text-secondary-text-on">
                    {t("register.hero.idBadge")}
                  </Badge>
                  <span className="inline-flex items-center gap-1 text-overline font-semibold text-success-text">
                    <ShieldCheck size={13} aria-hidden="true" />
                    {t("register.hero.safetyBadge")}
                  </span>
                </div>
                <h1 className="pt-1.5 text-[17px] font-bold leading-snug text-text-primary">
                  {t("register.hero.title")}
                </h1>
                <p className="pt-1 text-caption text-text-secondary">{t("register.hero.body")}</p>
              </div>
            </div>
          </div>

          {/* Header form — chỉ web (Figma 16:6836) */}
          <div className="hidden lg:flex lg:flex-col lg:gap-2">
            <span className="flex w-fit items-center gap-2.5">
              <span className="flex size-7 items-center justify-center rounded-lg bg-secondary">
                <img src={signupPortalBadge} alt="" className="size-[14px]" />
              </span>
              <span className="text-[11px] font-bold tracking-[0.4px] text-primary-dark">
                {t("web.signup.portalBadge")}
              </span>
            </span>
            <h1 className="text-[24px] font-extrabold leading-8 text-text-primary">{t("web.signup.formTitle")}</h1>
            <p className="text-[14px] leading-5 text-text-secondary">{t("web.signup.formSubtitle")}</p>
          </div>

          {/* Google + divider: web đặt TRƯỚC form (Figma 16:6861) */}
          <div className="hidden lg:flex lg:flex-col lg:gap-5">
            <GoogleAuthButton
              label={t("web.signup.googleCta")}
              className="h-14 rounded-2xl border-transparent bg-background-alt shadow-none"
            />
            <div className="relative flex items-center justify-center" role="separator">
              <span className="h-px w-full bg-chip-bg" />
              <span className="absolute bg-surface px-3 text-[11px] font-medium tracking-[0.4px] text-text-secondary">
                {t("web.signup.divider")}
              </span>
            </div>
          </div>

          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              void handleSubmit(onSubmit)(event);
            }}
            noValidate
          >
            <div className="flex flex-col gap-1.5">
              <label htmlFor="register-fullName" className="text-caption font-semibold text-text-secondary">
                {t("register.fields.fullName.label")}
                <span className="text-danger">{` ${t("common.requiredMark")}`}</span>
              </label>
              <Input
                id="register-fullName"
                autoComplete="name"
                placeholder={t("register.fields.fullName.placeholder")}
                leftIcon={<User size={18} aria-hidden="true" />}
                error={errors.fullName ? t(errors.fullName.message ?? "") : undefined}
                className="lg:bg-background lg:shadow-none"
                {...registerField("fullName")}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-end justify-between gap-3">
                <label htmlFor="register-email" className="text-caption font-semibold text-text-secondary">
                  {t("register.fields.email.label")}
                  <span className="text-danger">{` ${t("common.requiredMark")}`}</span>
                </label>
                {/* Figma 01c-1: gợi ý bước kế tiếp nằm cuối hàng label — chữ tĩnh, không phải link. */}
                <span className="shrink-0 text-overline font-semibold text-primary lg:hidden">
                  {t("register.fields.email.verifyHint")}
                </span>
              </div>
              <Input
                id="register-email"
                type="email"
                autoComplete="email"
                placeholder={t("register.fields.email.placeholder")}
                leftIcon={<Mail size={18} aria-hidden="true" />}
                error={errors.email ? t(errors.email.message ?? "") : undefined}
                className="lg:bg-background lg:shadow-none"
                {...registerField("email")}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="register-password" className="text-caption font-semibold text-text-secondary">
                {t("register.fields.password.label")}
                <span className="text-danger">{` ${t("common.requiredMark")}`}</span>
              </label>
              <PasswordField
                id="register-password"
                placeholder={t("register.fields.password.placeholder")}
                autoComplete="new-password"
                error={errors.password ? t(errors.password.message ?? "") : undefined}
                className="lg:bg-background lg:shadow-none"
                {...registerField("password")}
              />
              <PasswordStrengthMeter password={password} />
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-2">
                <label
                  htmlFor="referralCodeRaw"
                  className="flex min-w-0 items-center gap-1 whitespace-nowrap text-caption font-semibold text-text-secondary"
                >
                  <span className="truncate">{t("register.fields.referralCode.label")}</span>
                  <SquarePlus size={14} className="shrink-0 text-primary" aria-hidden="true" />
                </label>
                <span className="inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-full bg-secondary px-2 py-0.5 text-overline font-semibold text-secondary-text-on">
                  <Tag size={10} aria-hidden="true" />
                  {t("register.fields.referralCode.promoBadge")}
                </span>
              </div>
              <Input
                id="referralCodeRaw"
                autoComplete="off"
                placeholder={t("register.fields.referralCode.placeholder")}
                leftIcon={<Gift size={18} aria-hidden="true" />}
                className="lg:bg-background lg:shadow-none"
                {...registerField("referralCodeRaw")}
              />
            </div>

            <label className="flex min-h-11 cursor-pointer items-start gap-3">
              <input
                type="checkbox"
                checked={agreeTerms}
                onChange={(event) => {
                  setAgreeTerms(event.target.checked);
                }}
                className="mt-0.5 size-4 shrink-0 rounded-sm border-2 border-border-strong text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
              />
              <span className="text-caption text-text-secondary">
                {t("register.agreeTerms.prefix")}{" "}
                <Link
                  to="/legal/terms"
                  target="_blank"
                  rel="noreferrer"
                  className="font-semibold text-primary hover:underline"
                >
                  {t("register.agreeTerms.terms")}
                </Link>{" "}
                {t("register.agreeTerms.and")}{" "}
                <Link
                  to="/legal/privacy"
                  target="_blank"
                  rel="noreferrer"
                  className="font-semibold text-primary hover:underline"
                >
                  {t("register.agreeTerms.privacy")}
                </Link>{" "}
                {t("register.agreeTerms.suffix")}
              </span>
            </label>

            <button
              type="button"
              onClick={() => {
                setShowOptional((v) => !v);
              }}
              className="min-h-11 text-left text-caption font-semibold text-primary hover:underline"
            >
              {showOptional ? t("register.consents.hideOptional") : t("register.consents.showOptional")}
            </button>
            {showOptional ? (
              <ConsentCheckboxList
                purposes={optionalPurposes}
                values={optionalConsents}
                onChange={(code, granted) => {
                  setOptionalConsents((prev) => ({ ...prev, [code]: granted }));
                }}
              />
            ) : null}

            {submitError ? <InlineAlert>{submitError}</InlineAlert> : null}

            <Button
              type="submit"
              size="lg"
              className="gap-2 rounded-2xl lg:h-14"
              loading={register.isPending}
              disabled={!canSubmit || register.isPending}
            >
              {t("register.submit")}
              <ArrowRight size={18} aria-hidden="true" />
            </Button>
          </form>

          {/* Google + divider bản mobile (web đã có khối riêng phía trên form) */}
          <div className="flex items-center gap-3 lg:hidden" role="separator">
            <span className="h-px flex-1 bg-chip-bg" />
            <span className="text-overline font-semibold text-nav-inactive">{t("register.divider")}</span>
            <span className="h-px flex-1 bg-chip-bg" />
          </div>

          <div className="lg:hidden">
            <GoogleAuthButton label={t("register.googleCta")} />
          </div>

          <p className="text-center text-caption text-text-secondary">
            {t("register.haveAccount")}{" "}
            <Link to="/auth/login" className="font-semibold text-primary hover:underline">
              {t("register.loginLink")}
            </Link>
          </p>

          <div className="flex items-center gap-3 rounded-2xl bg-deco-backdrop p-3 lg:hidden">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-verified-deep text-white">
              <ShieldCheck size={18} aria-hidden="true" />
            </span>
            <div>
              <p className="text-caption font-bold text-text-primary">{t("register.securityBadge.title")}</p>
              <p className="flex items-center gap-1 pt-0.5 text-[11px] leading-[17px] tracking-[0.2px] text-text-secondary">
                <Globe size={11} aria-hidden="true" className="shrink-0 text-verified-deep" />
                {t("register.securityBadge.description")}
              </p>
            </div>
          </div>
        </div>

        {/* ══ Cột phải CHỈ CÓ Ở WEB (Figma 16:6949): ưu đãi + xem trước hồ sơ + giá trị ══ */}
        <aside className="hidden lg:col-span-5 lg:flex lg:flex-col lg:gap-6">
          {/* Voucher — nội dung mock theo thiết kế, chưa có API khuyến mãi */}
          <div className="relative overflow-hidden rounded-3xl bg-secondary p-6">
            <div className="flex items-start justify-between">
              <span className="flex items-center gap-2 rounded-full bg-surface px-3 py-1 shadow-xs">
                <img src={voucherBadge} alt="" className="size-[14px]" />
                <span className="text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
                  {t("web.signup.voucherBadge")}
                </span>
              </span>
              <img src={voucherGift} alt="" className="size-6" />
            </div>
            <h3 className="pt-4 text-[20px] font-extrabold leading-7 text-secondary-text-on">
              {t("web.signup.voucherTitle")}
            </h3>
            <p className="pt-1 text-[14px] leading-5 text-secondary-text-on opacity-90">
              {t("web.signup.voucherBody")}
            </p>
            <div className="mt-4 flex items-center justify-between gap-3 rounded-xl bg-[rgba(255,255,255,0.45)] p-3 backdrop-blur-[6px]">
              <span className="flex flex-col">
                <span className="text-[10px] font-semibold tracking-[0.4px] text-secondary-text-on">
                  {t("web.signup.voucherCodeLabel")}
                </span>
                <span className="text-[18px] font-extrabold tracking-[0.5px] text-secondary-text-on">
                  {t("web.signup.voucherCode")}
                </span>
              </span>
              <span className="flex items-center gap-1.5 rounded-xl bg-primary-dark px-3 py-1.5 text-[12px] font-bold text-white shadow-xs">
                <img src={copyIcon} alt="" className="size-3 brightness-0 invert" />
                {t("web.signup.voucherCopy")}
              </span>
            </div>
          </div>

          {/* Xem trước hồ sơ bé mèo — mock theo thiết kế (chưa tạo mèo ở bước này) */}
          <div className="rounded-3xl bg-surface p-6 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-2">
                <img src={previewEye} alt="" className="h-[15.833px] w-[16.667px]" />
                <span className="text-[14px] font-bold tracking-[0.2px] text-text-primary">
                  {t("web.signup.previewTitle")}
                </span>
              </span>
              <span className="rounded-full bg-chip-bg px-2.5 py-1 text-[11px] font-bold tracking-[0.4px] text-primary-dark">
                {t("web.signup.previewNext")}
              </span>
            </div>
            <div className="mt-4 flex items-center gap-4 rounded-xl bg-deco-backdrop p-4">
              <img src={signupCat} alt="" className="size-16 shrink-0 rounded-2xl object-cover shadow-xs" />
              <div className="min-w-0">
                <p className="text-[18px] font-bold leading-6 text-text-primary">{SIGNUP_PREVIEW_MOCK.catName}</p>
                <p className="text-[14px] leading-5 text-text-secondary">{SIGNUP_PREVIEW_MOCK.catMeta}</p>
                <p className="pt-1 text-[11px] font-semibold tracking-[0.4px] text-primary-dark">
                  {SIGNUP_PREVIEW_MOCK.chipId}
                </p>
              </div>
            </div>
          </div>

          {/* 3 giá trị khoa học */}
          <div className="rounded-3xl bg-surface p-6 shadow-xs">
            <h4 className="text-[16px] font-bold text-text-primary">{t("web.signup.valuesTitle")}</h4>
            <div className="mt-4 flex flex-col gap-3">
              {[
                {
                  icon: value1Alert,
                  tint: "bg-primary-dark/10",
                  title: t("web.signup.value1Title"),
                  body: t("web.signup.value1Body"),
                },
                {
                  icon: value2Chart,
                  tint: "bg-secondary-text-on/15",
                  title: t("web.signup.value2Title"),
                  body: t("web.signup.value2Body"),
                },
                {
                  icon: value3Network,
                  tint: "bg-verified-deep/20",
                  title: t("web.signup.value3Title"),
                  body: t("web.signup.value3Body"),
                },
              ].map((item) => (
                <div key={item.title} className="flex items-start gap-3 rounded-2xl bg-background p-3">
                  <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-xl", item.tint)}>
                    <img src={item.icon} alt="" className="size-4" />
                  </span>
                  <span className="flex flex-col">
                    <span className="text-[14px] font-bold leading-5 text-text-primary">{item.title}</span>
                    <span className="text-[14px] leading-5 text-text-secondary">{item.body}</span>
                  </span>
                </div>
              ))}
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
