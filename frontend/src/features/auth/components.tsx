import {
  useEffect,
  useId,
  useRef,
  useState,
  type InputHTMLAttributes,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { useTranslation } from "react-i18next";
import { Check, Copy, Eye, EyeOff, Lock, Clock } from "lucide-react";
import { Button } from "@/shared/ui";
import { GoogleGlyph } from "@/shared/assets/icons/GoogleGlyph";
import { cn } from "@/shared/lib/cn";
import { estimatePasswordStrength, type PasswordStrength } from "./schemas";
import type { ConsentPurposeOption, SessionItem } from "./types";

/**
 * Component dùng chung cho luồng auth — gom trong 1 file root theo đúng khuôn
 * `features/onboarding/components.tsx` (xem docs/handovers/A7.md §6 — giữ nguyên quy ước
 * dù thực đo lại cho thấy import feature->feature sibling không còn bị chặn, để nhất quán
 * kiến trúc toàn repo như ORCHESTRATOR yêu cầu).
 */

/* ---------------- OtpCodeInput ---------------- */

const OTP_LENGTH = 6;

interface OtpCodeInputProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  focusOnMount?: boolean;
}

/** 6 ô nhập OTP tách rời, tự chuyển ô, dán được cả chuỗi 6 số (M1 01c-2 / Web-01c-2). */
export function OtpCodeInput({ value, onChange, error, disabled, focusOnMount }: OtpCodeInputProps) {
  const { t } = useTranslation("auth");
  const inputsRef = useRef<(HTMLInputElement | null)[]>([]);
  const digits = value.padEnd(OTP_LENGTH, " ").split("").slice(0, OTP_LENGTH);

  // jsx-a11y/no-autofocus cấm prop `autoFocus` trên JSX — focus bằng tay ở effect thay vì
  // thuộc tính, hành vi tương đương (focus ô đầu tiên khi màn OTP vừa mở).
  useEffect(() => {
    if (focusOnMount) {
      inputsRef.current[0]?.focus();
    }
  }, [focusOnMount]);

  const setDigit = (index: number, digit: string) => {
    const next = digits.slice();
    next[index] = digit;
    onChange(next.join("").replace(/\s/g, ""));
  };

  const handleChange = (index: number, raw: string) => {
    const clean = raw.replace(/\D/g, "");
    if (!clean) {
      setDigit(index, "");
      return;
    }
    if (clean.length > 1) {
      // Dán cả chuỗi vào 1 ô — rải ra các ô tiếp theo.
      const chars = clean.slice(0, OTP_LENGTH - index).split("");
      const next = digits.slice();
      chars.forEach((char, offset) => {
        next[index + offset] = char;
      });
      onChange(next.join("").replace(/\s/g, ""));
      const lastIndex = Math.min(index + chars.length, OTP_LENGTH - 1);
      inputsRef.current[lastIndex]?.focus();
      return;
    }
    setDigit(index, clean);
    if (index < OTP_LENGTH - 1) {
      inputsRef.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && !digits[index]?.trim() && index > 0) {
      inputsRef.current[index - 1]?.focus();
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex justify-between gap-2 lg:max-w-[460px] lg:gap-3" role="group" aria-label={t("verifyOtp.fields.code.label")}>
        {digits.map((digit, index) => (
          <input
            key={index}
            ref={(el) => {
              inputsRef.current[index] = el;
            }}
            type="text"
            inputMode="numeric"
            autoComplete={index === 0 ? "one-time-code" : "off"}
            maxLength={OTP_LENGTH}
            value={digit.trim()}
            disabled={disabled}
            aria-invalid={error ? true : undefined}
            onChange={(event) => { handleChange(index, event.target.value); }}
            onKeyDown={(event) => { handleKeyDown(index, event); }}
            className={cn(
              "h-14 w-full min-w-0 rounded-xl bg-surface text-center text-h3 font-bold text-text-primary shadow-xs lg:rounded-lg",
              "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
              error && "outline outline-2 outline-danger",
            )}
          />
        ))}
      </div>
      {error ? (
        <p role="alert" className="text-small font-medium text-danger-text">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/* ---------------- PasswordField ---------------- */

interface PasswordFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

/**
 * Input mật khẩu có icon ổ khoá trái + nút hiện/ẩn phải (icon con mắt, M1 01b/01c-1). Không
 * tái dùng `shared/ui` `Input` trực tiếp — cần bọc thêm nút hiện/ẩn nên tự vẽ lại đúng style
 * của `Input` (rounded-xl/shadow-xs/h-12, khớp Figma 26mOVF2zdu4cI1EPz2Syxw) thay vì định vị
 * tuyệt đối chồng lên (dễ lệch khi có/không `label`/`error`).
 */
export function PasswordField({ className, label, error, helperText, id, ...props }: PasswordFieldProps) {
  const { t } = useTranslation("auth");
  const [visible, setVisible] = useState(false);
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const helperId = `${inputId}-helper`;
  const errorId = `${inputId}-error`;

  return (
    <div className="flex flex-col gap-1.5">
      {label ? (
        <label htmlFor={inputId} className="text-caption font-semibold text-text-secondary">
          {label}
        </label>
      ) : null}
      <div className="relative flex items-center">
        <span className="pointer-events-none absolute left-3.5 flex items-center text-text-tertiary" aria-hidden="true">
          <Lock className="size-4" />
        </span>
        <input
          id={inputId}
          type={visible ? "text" : "password"}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : helperText ? helperId : undefined}
          className={cn(
            "h-12 w-full rounded-xl bg-surface pl-11 pr-11 text-body text-text-primary shadow-xs",
            "placeholder:text-text-tertiary",
            "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
            "disabled:cursor-not-allowed disabled:opacity-50",
            error && "outline outline-2 outline-danger",
            className,
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => { setVisible((v) => !v); }}
          aria-label={visible ? t("common.hidePassword") : t("common.showPassword")}
          className="absolute right-2 flex size-8 items-center justify-center rounded-lg text-text-tertiary hover:text-text-secondary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
        >
          {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
        </button>
      </div>
      {error ? (
        <p id={errorId} className="text-small text-danger-text">
          {error}
        </p>
      ) : helperText ? (
        <p id={helperId} className="text-small text-text-tertiary">
          {helperText}
        </p>
      ) : null}
    </div>
  );
}

/* ---------------- PasswordStrengthMeter ---------------- */

const STRENGTH_LEVEL: Record<PasswordStrength, number> = { empty: 0, weak: 1, medium: 2, strong: 3 };
const STRENGTH_COLOR: Record<PasswordStrength, string> = {
  empty: "bg-border",
  weak: "bg-danger",
  medium: "bg-warning",
  strong: "bg-success",
};

export function PasswordStrengthMeter({ password }: { password: string }) {
  const { t } = useTranslation("auth");
  const strength = estimatePasswordStrength(password);
  const level = STRENGTH_LEVEL[strength];

  return (
    <div className="flex flex-col gap-1.5 rounded-xl bg-background-alt px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-overline font-medium text-text-secondary">
          {t("register.passwordStrength.label")}
        </span>
        <span className="text-overline font-medium text-text-tertiary">
          {strength === "empty"
            ? t("register.passwordStrength.empty")
            : t(`register.passwordStrength.${strength}`)}
        </span>
      </div>
      <div className="flex gap-1.5">
        {[1, 2, 3].map((segment) => (
          <span
            key={segment}
            className={cn("h-1.5 flex-1 rounded-full bg-border", segment <= level && STRENGTH_COLOR[strength])}
          />
        ))}
      </div>
    </div>
  );
}

/* ---------------- ResendCountdown ---------------- */

interface ResendCountdownProps {
  seconds: number;
  onExpire?: () => void;
  onResend: () => void;
  disabled?: boolean;
}

/** Đếm ngược "Gửi lại mã sau {n}s" dùng chung cho verify-otp/forgot-password (M1 01c-2). */
export function ResendCountdown({ seconds, onExpire, onResend, disabled }: ResendCountdownProps) {
  const { t } = useTranslation("auth");
  const [remaining, setRemaining] = useState(seconds);
  const expiredRef = useRef(false);

  useEffect(() => {
    setRemaining(seconds);
    expiredRef.current = false;
  }, [seconds]);

  useEffect(() => {
    if (remaining <= 0) {
      if (!expiredRef.current) {
        expiredRef.current = true;
        onExpire?.();
      }
      return;
    }
    const timer = window.setTimeout(() => {
      setRemaining((r) => r - 1);
    }, 1000);
    return () => { window.clearTimeout(timer); };
  }, [remaining, onExpire]);

  if (remaining > 0) {
    return (
      <p className="flex w-fit items-center gap-1.5 rounded-full bg-background-alt px-3 py-1 text-caption text-text-secondary lg:bg-transparent lg:px-0">
        <Clock size={14} aria-hidden="true" className="text-primary-dark" />
        {t("verifyOtp.resendCountdown", { seconds: remaining })}
      </p>
    );
  }

  return (
    <button
      type="button"
      onClick={onResend}
      disabled={disabled}
      className="min-h-11 text-caption font-semibold text-primary hover:underline disabled:opacity-50"
    >
      {t("verifyOtp.resendNow")}
    </button>
  );
}

/* ---------------- ConsentCheckboxList ---------------- */

interface ConsentCheckboxListProps {
  purposes: ConsentPurposeOption[];
  values: Record<string, boolean | undefined>;
  onChange: (code: string, granted: boolean) => void;
}

/**
 * Danh sách checkbox consent riêng từng mục đích (p15 §15.3.1 C2 — cấm gộp). Nội dung
 * nhãn/mô tả lấy nguyên văn từ API `GET /privacy/purposes` (module privacy sở hữu câu
 * chữ), component chỉ vẽ UI + badge "Dữ liệu nhạy cảm" khi `sensitive`.
 */
export function ConsentCheckboxList({ purposes, values, onChange }: ConsentCheckboxListProps) {
  const { t } = useTranslation("auth");
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="sr-only">{t("register.consents.legend")}</legend>
      {purposes.map((purpose) => {
        const checked = purpose.mandatory ? true : Boolean(values[purpose.code]);
        const inputId = `consent-${purpose.code}`;
        return (
          <label
            key={purpose.code}
            htmlFor={inputId}
            aria-label={purpose.label}
            className={cn(
              "flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border border-border bg-surface p-3",
              purpose.mandatory && "cursor-default bg-background-alt",
            )}
          >
            <input
              id={inputId}
              type="checkbox"
              checked={checked}
              disabled={purpose.mandatory}
              onChange={(event) => { onChange(purpose.code, event.target.checked); }}
              className="mt-0.5 size-5 shrink-0 rounded-sm border-2 border-border-strong text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
            <span className="flex flex-col gap-0.5">
              <span className="flex flex-wrap items-center gap-2">
                <span className="text-caption font-semibold text-text-primary">{purpose.label}</span>
                {purpose.mandatory ? (
                  <span className="rounded-full bg-chip-bg px-2 py-0.5 text-overline font-semibold text-primary">
                    {t("register.consents.mandatoryBadge")}
                  </span>
                ) : null}
                {purpose.sensitive ? (
                  <span className="rounded-full bg-danger-bg px-2 py-0.5 text-overline font-semibold text-danger-text">
                    {t("register.consents.sensitiveBadge")}
                  </span>
                ) : null}
              </span>
              <span className="text-small text-text-secondary">{purpose.description}</span>
            </span>
          </label>
        );
      })}
    </fieldset>
  );
}

/* ---------------- GoogleAuthButton ---------------- */

/**
 * Nút Google OAuth — điều hướng TOÀN TRANG (`window.location.assign`), KHÔNG `fetch`
 * (p9 §9.6.3, p11 §11.1.4): fetch vướng CORS của Google và không đặt được cookie phiên.
 * Chữ tối màu (không phải `text-primary` như tertiary mặc định) — khớp nút Google trắng viền
 * nhạt của Figma (M1 01b), không phải nút dạng link.
 */
export function GoogleAuthButton({ label, className }: { label: string; className?: string }) {
  return (
    <Button
      type="button"
      variant="tertiary"
      size="lg"
      className={cn("w-full bg-surface text-text-primary shadow-xs", className)}
      onClick={() => { window.location.assign("/oauth2/authorization/google"); }}
      leftIcon={<GoogleGlyph className="size-5" />}
    >
      {label}
    </Button>
  );
}

/* ---------------- SessionRow ---------------- */

interface SessionRowProps {
  session: SessionItem;
  onRevoke?: (id: string) => void;
  revoking?: boolean;
}

/** Một dòng phiên đăng nhập (danh sách thiết bị, A13/A14) — sẵn cho `/settings/security`. */
export function SessionRow({ session, onRevoke, revoking }: SessionRowProps) {
  const { t } = useTranslation("auth");
  return (
    <li className="flex items-center justify-between gap-3 rounded-lg border border-border bg-surface p-3">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="flex items-center gap-2 text-body font-semibold text-text-primary">
          <span className="truncate">{session.deviceLabel ?? t("sessions.unknownDevice")}</span>
          {session.current ? (
            <span className="shrink-0 rounded-full bg-chip-bg px-2 py-0.5 text-overline font-semibold text-primary">
              {t("sessions.currentBadge")}
            </span>
          ) : null}
        </span>
        <span className="text-caption text-text-tertiary">
          {session.ipMasked ? `${session.ipMasked} • ` : ""}
          {t("sessions.lastSeen", { date: new Date(session.lastSeenAt).toLocaleString("vi-VN") })}
        </span>
      </div>
      {!session.current && onRevoke ? (
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          loading={revoking}
          onClick={() => { onRevoke(session.id); }}
        >
          {t("sessions.revoke")}
        </Button>
      ) : null}
    </li>
  );
}

/* ---------------- RecoveryCodeGrid ---------------- */

/** Lưới 10 mã khôi phục TOTP hiện đúng một lần (B16/B17) — sẵn cho `/admin/setup-2fa`. */
export function RecoveryCodeGrid({ codes }: { codes: string[] }) {
  const { t } = useTranslation("auth");
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    void navigator.clipboard.writeText(codes.join("\n")).then(() => {
      setCopied(true);
      window.setTimeout(() => { setCopied(false); }, 2000);
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2 rounded-lg border border-border bg-background-alt p-3 font-mono">
        {codes.map((code) => (
          <span key={code} className="text-caption text-text-primary">
            {code}
          </span>
        ))}
      </div>
      <Button type="button" variant="tertiary" size="sm" onClick={handleCopy} leftIcon={copied ? <Check className="size-4" /> : <Copy className="size-4" />}>
        {copied ? t("mfa.recoveryCodes.copied") : t("mfa.recoveryCodes.copy")}
      </Button>
    </div>
  );
}

/* ---------------- InlineAlert ---------------- */

export function InlineAlert({ children, tone = "danger" }: { children: ReactNode; tone?: "danger" | "info" }) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={cn(
        "rounded-lg border p-3 text-small",
        tone === "danger" ? "border-danger bg-danger-bg text-danger-text" : "border-border bg-background-alt text-text-secondary",
      )}
    >
      {children}
    </div>
  );
}
