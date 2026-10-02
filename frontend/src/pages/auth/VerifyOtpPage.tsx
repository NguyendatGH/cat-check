import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { MailCheck, Lock, Pencil, ShieldCheck, ArrowRight } from "lucide-react";
import { Button, EmptyState, Stepper } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { WebRegisterSteps } from "./WebRegisterSteps";
import otpCat from "@/shared/assets/images/web-auth/otp-cat.png";
import mailBox from "@/shared/assets/icons/web-auth/mail-box.svg";
import securityLevel from "@/shared/assets/icons/web-auth/security-level.svg";
import atSign from "@/shared/assets/icons/web-auth/at-sign.svg";
import pencil from "@/shared/assets/icons/web-auth/pencil.svg";
import infoIcon from "@/shared/assets/icons/web-auth/info.svg";
import pendingProfile from "@/shared/assets/icons/web-auth/pending-profile.svg";
import readyCheck from "@/shared/assets/icons/web-auth/ready-check.svg";
import whyVerify from "@/shared/assets/icons/web-auth/why-verify.svg";
import valueLock from "@/shared/assets/icons/web-auth/value-lock.svg";
import valueNetwork from "@/shared/assets/icons/web-auth/value-network.svg";
import valueBell from "@/shared/assets/icons/web-auth/value-bell.svg";
import supportHeadset from "@/shared/assets/icons/web-auth/support-headset.svg";
import phoneIcon from "@/shared/assets/icons/web-auth/phone.svg";
import certIso from "@/shared/assets/icons/web-auth/cert-iso.svg";
import certIsfm from "@/shared/assets/icons/web-auth/cert-isfm.svg";
import {
  OtpCodeInput,
  ResendCountdown,
  useAuthFlowStore,
  useRegister,
  useRequestOtp,
  useVerifyOtp,
} from "@/features/auth";

const INITIAL_RESEND_COOLDOWN_SECONDS = 60;

/**
 * Thẻ "Hồ sơ chờ kích hoạt" ở cột phải bản web (Figma 16:8554) — MOCK THEO THIẾT KẾ.
 * Ở bước xác thực email người dùng chưa tạo hồ sơ mèo nên không có dữ liệu thật.
 */
const OTP_PREVIEW_MOCK = {
  catName: "Luna",
  sexBadge: "Cái (Đã triệt sản)",
  breedAge: "Mèo Anh Lông Ngắn • 2 tuổi 4 tháng",
  ownerName: "Anna Nguyễn",
  code: "Mã số: CC-VN-8921",
} as const;

function maskEmail(email: string): string {
  const [local, domain] = email.split("@");
  if (!domain) return "***";
  const maskedLocal = local.length <= 1 ? "*" : `${local[0]}***${local[local.length - 1]}`;
  return `${maskedLocal}@${domain}`;
}

/**
 * `/auth/verify-otp` (p9 §9.4.3 #4, M1 01c-2 · W1 Web-01c-2) — bước 2 của đăng ký.
 *
 * Backend KHÔNG có endpoint "activate" riêng: `POST /auth/otp/verify` chỉ trả
 * `otpTicket` (bằng chứng đã xác minh), việc kích hoạt tài khoản đi qua gọi LẠI
 * `POST /auth/register` kèm `otpTicket` (xem `RegistrationService.completePreverifiedRegistration`,
 * `docs/handovers/A1.md`). `AuthController.register()` đã tự gọi `sessionGateway.establish()`
 * khi `result.authenticated()` — activation xong là ĐÃ có phiên thật, KHÔNG được gọi thêm
 * `POST /auth/login` sau đó: cùng 1 HTTP session gọi `establish()` 2 lần trong cùng request
 * cycle làm `SessionAuthenticationStrategy` đổi session id, nhưng `user_device_session` vẫn
 * insert theo id CŨ ở lần gọi thứ hai → `duplicate key value violates unique constraint
 * "uq_device_session_hash"` (500) — xác nhận thật bằng cách gọi trực tiếp API, tạo tài khoản
 * demo. Vào thẳng onboarding sau khi activation trả `authenticated: true`.
 */
export function VerifyOtpPage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const pendingRegistration = useAuthFlowStore((s) => s.pendingRegistration);
  const reset = useAuthFlowStore((s) => s.reset);

  const verifyOtp = useVerifyOtp();
  const activateRegistration = useRegister();
  const requestOtp = useRequestOtp();

  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [resendSeconds, setResendSeconds] = useState(INITIAL_RESEND_COOLDOWN_SECONDS);
  const [resendKey, setResendKey] = useState(0);

  if (!pendingRegistration) {
    return (
      <EmptyState
        title={t("verifyOtp.missingDraft.title")}
        description={t("verifyOtp.missingDraft.description")}
        action={
          <Button type="button" onClick={() => { void navigate("/auth/register"); }}>
            {t("verifyOtp.missingDraft.cta")}
          </Button>
        }
      />
    );
  }

  const isBusy = verifyOtp.isPending || activateRegistration.isPending;

  const handleSubmit = async () => {
    setError(null);
    try {
      const { otpTicket } = await verifyOtp.mutateAsync({
        email: pendingRegistration.email,
        purpose: "REGISTER_VERIFY",
        code,
      });
      await activateRegistration.mutateAsync({
        email: pendingRegistration.email,
        password: pendingRegistration.password,
        fullName: pendingRegistration.fullName,
        referralCodeRaw: pendingRegistration.referralCodeRaw || undefined,
        otpTicket,
      });
      reset();
      window.location.assign("/onboarding/cat");
    } catch (err) {
      if (isApiError(err) && err.code === "OTP_INVALID") {
        setError(t("verifyOtp.errors.invalidCode"));
      } else if (isApiError(err) && err.code === "OTP_EXPIRED") {
        setError(t("verifyOtp.errors.expired"));
      } else if (isApiError(err) && err.code === "OTP_LOCKED") {
        setError(t("verifyOtp.errors.locked"));
      } else if (isApiError(err) && err.code === "OTP_TICKET_INVALID") {
        setError(t("verifyOtp.errors.ticketInvalid"));
      } else {
        setError(t("errors.generic"));
      }
    }
  };

  const handleResend = async () => {
    setError(null);
    try {
      const result = await requestOtp.mutateAsync({ email: pendingRegistration.email, purpose: "REGISTER_VERIFY" });
      setResendSeconds(result.canResendInSeconds);
      setResendKey((k) => k + 1);
      setCode("");
    } catch {
      setError(t("errors.generic"));
    }
  };

  return (
    <div className="flex flex-col gap-6 pb-6 lg:gap-10">
      <div className="lg:hidden">
        <Stepper current={2} total={4} label={t("verifyOtp.stepLabel")} />
      </div>
      <WebRegisterSteps current={2} />

      <div className="lg:grid lg:grid-cols-12 lg:items-start lg:gap-8">
        {/* ══ Cột trái: thẻ xác thực (web 7 cột) ══ */}
        <div className="flex flex-col gap-6 lg:col-span-7 lg:relative lg:gap-8 lg:overflow-hidden lg:rounded-xl lg:bg-surface lg:p-10 lg:shadow-xs">
          <div
            className="pointer-events-none absolute -right-16 -top-16 hidden size-48 rounded-full bg-[rgba(13,54,154,0.05)] blur-[20px] lg:block"
            aria-hidden="true"
          />

          {/* Header thẻ — chỉ web (Figma 16:8479) */}
          <div className="relative hidden items-center justify-between pb-4 lg:flex">
            <div className="flex items-center gap-3">
              <span className="flex size-12 items-center justify-center rounded-xl bg-deco-backdrop shadow-xs">
                <img src={mailBox} alt="" className="h-[21px] w-[24.5px]" />
              </span>
              <span className="flex flex-col gap-[3.5px]">
                <span className="text-[11px] font-bold uppercase tracking-[1.1px] text-nav-inactive">
                  {t("web.otp.encryptionBadge")}
                </span>
                <span className="text-[20px] font-bold leading-7 text-text-primary">
                  {t("web.otp.inboxTitle")}
                </span>
              </span>
            </div>
            <span className="flex items-center gap-1.5 rounded-full bg-secondary-light/50 px-3 py-1.5">
              <img src={securityLevel} alt="" className="h-[13.333px] w-[10.667px]" />
              <span className="text-[12px] font-bold tracking-[0.3px] text-secondary-text-on">
                {t("web.otp.securityLevel")}
              </span>
            </span>
          </div>

          {/* Tiêu đề + email — chỉ web */}
          <div className="relative hidden lg:flex lg:flex-col lg:gap-3">
            <h1 className="text-[32px] font-extrabold leading-10 tracking-[-0.8px] text-text-primary">
              {t("web.otp.title")}
            </h1>
            <p className="text-[16px] leading-[26px] text-text-secondary">{t("web.otp.subtitle")}</p>
            <div className="flex w-fit items-center gap-2 rounded-lg bg-chip-bg px-3.5 py-2">
              <img src={atSign} alt="" className="size-[15px]" />
              <span className="text-[16px] font-bold leading-6 text-primary-dark">
                {maskEmail(pendingRegistration.email)}
              </span>
              <Link
                to="/auth/register"
                onClick={() => { reset(); }}
                className="ml-2 flex items-center gap-1 text-[12px] font-bold tracking-[0.3px] text-secondary-text-on"
              >
                <img src={pencil} alt="" className="size-3" />
                {t("web.otp.changeEmail")}
              </Link>
            </div>
          </div>

      <header className="flex flex-col items-center gap-1 pt-2 text-center lg:hidden">
        <div className="relative mb-2 flex size-20 items-center justify-center rounded-full bg-primary-dark/10">
          <div className="flex size-14 items-center justify-center rounded-full bg-primary-dark text-white shadow-[0px_8px_20px_-4px_rgba(13,54,154,0.35)]">
            <MailCheck size={24} aria-hidden="true" />
          </div>
          <span className="absolute right-0 top-0 flex size-6 items-center justify-center rounded-full bg-secondary text-secondary-text-on">
            <Lock size={12} aria-hidden="true" strokeWidth={2.5} />
          </span>
        </div>
        <h1 className="text-h2 font-bold text-text-primary">{t("verifyOtp.title")}</h1>
        <p className="max-w-[310px] text-caption leading-[21px] text-text-secondary">
          {t("verifyOtp.subtitle")}
        </p>
        <p className="text-caption font-bold text-text-primary">{maskEmail(pendingRegistration.email)}</p>
        <Link
          to="/auth/register"
          onClick={() => { reset(); }}
          className="mt-1.5 flex items-center gap-1.5 rounded-full bg-deco-backdrop px-3 py-1 text-caption font-semibold text-primary-dark hover:bg-chip-bg"
        >
          <Pencil size={12} aria-hidden="true" />
          {t("verifyOtp.changeEmail")}
        </Link>
      </header>

      <div className="relative flex flex-col gap-3">
        <span className="hidden text-[14px] font-bold tracking-[0.2px] text-text-primary lg:block">
          {t("web.otp.otpLabel")}
        </span>
        <OtpCodeInput value={code} onChange={setCode} error={error ?? undefined} disabled={isBusy} focusOnMount />
      </div>

      <div className="relative flex flex-col items-center gap-1.5 lg:flex-row lg:justify-between lg:gap-3">
        <ResendCountdown
          key={resendKey}
          seconds={resendSeconds}
          onResend={() => { void handleResend(); }}
          disabled={requestOtp.isPending}
        />
        <p className="text-[11px] tracking-[0.2px] text-text-tertiary lg:text-[12px]">{t("verifyOtp.checkSpamNote")}</p>
      </div>

      {/* Mẹo bảo mật: bản mobile. Web dùng khối "Clinical Security Notice" bên dưới CTA. */}
      <div className="flex items-start gap-3 rounded-xl bg-background-alt p-4 lg:hidden">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-text-on">
          <ShieldCheck size={16} aria-hidden="true" />
        </span>
        <div>
          <p className="text-caption font-bold text-text-primary">{t("verifyOtp.securityTipTitle")}</p>
          <p className="pt-0.5 text-[11px] leading-[18px] tracking-[0.2px] text-text-secondary">
            {t("verifyOtp.securityTip")}
          </p>
        </div>
      </div>

      <Button
        type="button"
        size="lg"
        className="relative gap-2 rounded-2xl lg:h-14 lg:rounded-xl"
        loading={isBusy}
        disabled={code.length !== 6 || isBusy}
        onClick={() => { void handleSubmit(); }}
      >
        <span className="lg:hidden">{t("verifyOtp.submit")}</span>
        <span className="hidden lg:inline">{t("web.otp.submit")}</span>
        <ArrowRight size={18} aria-hidden="true" />
      </Button>

      {/* Ghi chú bảo mật — chỉ web (Figma 16:8545) */}
      <div className="relative hidden gap-3 rounded-xl bg-deco-backdrop p-4 lg:flex">
        <img src={infoIcon} alt="" className="h-[18.667px] w-[16.667px] shrink-0" />
        <div className="flex flex-col gap-1">
          <p className="text-[14px] leading-5 text-text-secondary">{t("web.otp.noticeBody")}</p>
          <p className="text-[11px] font-semibold tracking-[0.4px] text-nav-inactive">
            {t("web.otp.noticeFooter")}
          </p>
        </div>
      </div>

      <p className="relative text-center text-caption text-text-secondary lg:hidden">
        {t("verifyOtp.supportPrompt")}{" "}
        <Link to="/legal/contact" className="font-semibold text-primary hover:underline">
          {t("verifyOtp.supportLink")}
        </Link>
      </p>
        </div>

        {/* ══ Cột phải CHỈ CÓ Ở WEB (Figma 16:8553) ══ */}
        <aside className="hidden lg:col-span-5 lg:flex lg:flex-col lg:gap-6">
          <div className="rounded-xl bg-surface p-6 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-2">
                <img src={pendingProfile} alt="" className="h-[15.833px] w-[16.667px]" />
                <span className="text-[14px] font-bold tracking-[0.2px] text-text-primary">
                  {t("web.otp.pendingProfile")}
                </span>
              </span>
              <span className="flex items-center gap-1 rounded-full bg-secondary px-2.5 py-1">
                <span className="size-1.5 rounded-full bg-[rgb(118,91,0)]" aria-hidden="true" />
                <span className="text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
                  {t("web.otp.pendingBadge")}
                </span>
              </span>
            </div>
            <div className="mt-4 flex items-center gap-4 rounded-xl bg-deco-backdrop p-4">
              <img src={otpCat} alt="" className="size-16 shrink-0 rounded-full object-cover shadow-xs" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-[18px] font-bold leading-6 text-text-primary">
                    {OTP_PREVIEW_MOCK.catName}
                  </span>
                  <span className="rounded bg-chip-bg px-2 py-0.5 text-[11px] font-bold text-primary-dark">
                    {OTP_PREVIEW_MOCK.sexBadge}
                  </span>
                </div>
                <p className="text-[14px] leading-5 text-text-secondary">{OTP_PREVIEW_MOCK.breedAge}</p>
                <p className="flex items-center gap-1 pt-1 text-[11px] font-semibold tracking-[0.4px] text-verified-deep">
                  <img src={readyCheck} alt="" className="size-[11.667px]" />
                  {t("web.otp.readyToLink")}
                </p>
              </div>
            </div>
            <div className="flex items-center justify-between pt-4 text-[11px] tracking-[0.4px]">
              <span className="font-semibold text-text-secondary">
                {t("web.otp.ownerPrefix")} {OTP_PREVIEW_MOCK.ownerName}
              </span>
              <span className="font-bold text-primary-dark">{OTP_PREVIEW_MOCK.code}</span>
            </div>
          </div>

          <div className="rounded-xl bg-surface p-6 shadow-xs">
            <div className="flex items-center gap-2.5">
              <img src={whyVerify} alt="" className="h-[21px] w-[22px]" />
              <h3 className="text-[18px] font-bold leading-6 text-text-primary">{t("web.otp.whyTitle")}</h3>
            </div>
            <p className="pt-2.5 text-[14px] leading-[22.75px] text-text-secondary">{t("web.otp.whyBody")}</p>
            <div className="mt-3 flex flex-col gap-3">
              {[
                { icon: valueLock, title: t("web.otp.why1Title"), body: t("web.otp.why1Body") },
                { icon: valueNetwork, title: t("web.otp.why2Title"), body: t("web.otp.why2Body") },
                { icon: valueBell, title: t("web.otp.why3Title"), body: t("web.otp.why3Body") },
              ].map((item) => (
                <div key={item.title} className="flex gap-3 rounded-lg bg-background-alt p-3">
                  <img src={item.icon} alt="" className="mt-0.5 size-4 shrink-0" />
                  <span className="flex flex-col">
                    <span className="text-[14px] font-bold leading-5 text-text-primary">{item.title}</span>
                    <span className="text-[14px] leading-5 text-text-secondary">{item.body}</span>
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="flex items-center justify-between rounded-xl bg-deco-backdrop p-5 shadow-xs">
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-full bg-surface shadow-xs">
                <img src={supportHeadset} alt="" className="h-[15px] w-[16.667px]" />
              </span>
              <span className="flex flex-col">
                <span className="text-[14px] font-bold leading-5 tracking-[0.2px] text-text-primary">
                  {t("web.otp.supportTitle")}
                </span>
                <span className="text-[14px] leading-5 text-text-secondary">{t("web.otp.supportBody")}</span>
              </span>
            </div>
            <Link
              to="/legal/contact"
              className="flex items-center gap-1 rounded-lg bg-surface px-3.5 py-2 shadow-xs"
            >
              <img src={phoneIcon} alt="" className="size-3" />
              <span className="text-[12px] font-bold tracking-[0.3px] text-primary-dark">
                {t("web.otp.supportPhone")}
              </span>
            </Link>
          </div>
        </aside>
      </div>

      {/* Footer chứng nhận — chỉ web (Figma 16:8621) */}
      <div className="hidden items-center justify-between pb-4 pt-10 lg:flex">
        <div className="flex items-center gap-6">
          <span className="flex items-center gap-2">
            <img src={certIso} alt="" className="h-[15.75px] w-[16.5px]" />
            <span className="text-[11px] font-semibold tracking-[0.4px] text-nav-inactive">
              {t("web.otp.certIso")}
            </span>
          </span>
          <span className="flex items-center gap-2">
            <img src={certIsfm} alt="" className="h-[14.25px] w-[10.5px]" />
            <span className="text-[11px] font-semibold tracking-[0.4px] text-nav-inactive">
              {t("web.otp.certIsfm")}
            </span>
          </span>
        </div>
        <span className="text-[12px] font-bold tracking-[0.3px] text-text-secondary">
          {t("web.otp.copyright")}
        </span>
      </div>
    </div>
  );
}
